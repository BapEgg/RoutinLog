package com.bapegg.routinlog.report

import com.bapegg.routinlog.account.persistence.*
import com.bapegg.routinlog.body.*
import com.bapegg.routinlog.condition.*
import com.bapegg.routinlog.food.*
import com.bapegg.routinlog.profile.*
import com.bapegg.routinlog.security.AuthenticatedUser
import com.bapegg.routinlog.workout.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.*
import java.util.UUID
import kotlin.test.*

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class RecordCalendarApiTest @Autowired constructor(private val service: RecordCalendarService, private val accounts: UserAccountRepository,
    private val profiles: ProfileService, private val meals: MealService, private val body: BodyMeasurementService,
    private val conditions: ConditionService, private val workouts: WorkoutService, private val jdbc: JdbcTemplate, private val mvc: MockMvc) {
    private val today get() = LocalDate.now(ZoneId.of("Asia/Seoul"))
    private val month get() = today.minusMonths(1).withDayOfMonth(1)
    private fun n(value: String) = BigDecimal(value)
    private fun user() = accounts.saveAndFlush(UserAccountEntity()).id.also { profiles.save(it, ProfileDto(
        age=32, sex=ProfileSex.MALE, heightCm=n("178"), initialWeightKg=n("83.2"), goal=ProfileGoal.MAINTAIN,
        activityLevel=ActivityLevel.LIGHT, exerciseDays=listOf(1,4), exerciseMinutes=40, experience=ExerciseExperience.BEGINNER,
        units=DisplayUnits.METRIC, nutritionMode=NutritionMode.NONE,
        termsVersion=CURRENT_POLICY_VERSION, privacyVersion=CURRENT_POLICY_VERSION, healthConsentVersion=CURRENT_POLICY_VERSION,
        timeZone="Asia/Seoul", effectiveFrom=month)) }
    private fun auth(id: UUID) = authentication(UsernamePasswordAuthenticationToken(AuthenticatedUser(id), null, emptyList()))

    @Test fun `calendar authenticates returns no-store and rejects malformed future or partial months`() {
        mvc.perform(get("/api/v1/record-calendar")).andExpect(status().isUnauthorized)
        val u = user()
        mvc.perform(get("/api/v1/record-calendar").with(auth(u))).andExpect(status().isOk)
            .andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$.today").value(today.toString()))
        listOf("wrong", "1899-12-01", month.plusDays(1).toString(), today.plusMonths(1).withDayOfMonth(1).toString()).forEach {
            mvc.perform(get("/api/v1/record-calendar").with(auth(u)).param("month", it)).andExpect(status().isBadRequest)
        }
    }
    @Test fun `empty calendar includes every past date without inventing body steps meals or workouts`() {
        val u = user(); val result = service.get(u, month)
        assertEquals(YearMonth.from(month).lengthOfMonth(), result.days.size)
        assertTrue(result.days.all { it.mealsEaten == 0 && it.mealsSkipped == 0 && it.weightKg == null && it.waistCm == null && it.steps == null && it.workoutStatus == null && !it.conditionRecorded })
        assertEquals(today.dayOfMonth, service.get(u, null).days.size)
        assertEquals(29, service.get(u, LocalDate.of(2024,2,1)).days.size)
    }
    @Test fun `meal confirmations and deleted records are reflected without a fixed meal denominator`() {
        val u = user()
        val food = meals.putFood(u, UUID.randomUUID(), FoodWrite("food", basisGrams=n("80"), nutrition=NutritionValues(kcal=n("160")), preparation=FoodPreparation.AS_SOLD))
        val records = (1..4).map { meals.putMeal(u, UUID.randomUUID(), MealWrite(month, UUID.randomUUID().toString(), "meal $it", MealStatus.EATEN,
            listOf(MealItemWrite(UUID.randomUUID().toString(), food.id, null)))) }
        meals.putMeal(u, UUID.randomUUID(), MealWrite(month, UUID.randomUUID().toString(), "skipped", MealStatus.SKIPPED, emptyList()))
        assertEquals(4, service.get(u, month).days.first().mealsEaten)
        assertEquals(1, service.get(u, month).days.first().mealsSkipped)
        meals.deleteMeal(u, UUID.fromString(records.first().id), records.first().version)
        assertEquals(3, service.get(u, month).days.first().mealsEaten)
        assertTrue(service.get(user(), month).days.all { it.mealsEaten == 0 && it.mealsSkipped == 0 })
    }
    @Test fun `body condition and actual zero steps are distinguished from missing values and other accounts`() {
        val u = user()
        body.put(u, month, PutBodyMeasurement(weightKg=n("83.2")))
        conditions.save(u, month.plusDays(1), ConditionWrite(ConditionValues(sleepMinutes=0)))
        val connection = UUID.randomUUID(); val instant = month.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant()
        jdbc.update("INSERT INTO step_connections(id,user_id,started_at,time_zone) VALUES(?,?,?,?)", connection,u,java.sql.Timestamp.from(instant),"Asia/Seoul")
        jdbc.update("INSERT INTO step_observations(connection_id,recorded_on,from_time,through_time,steps) VALUES(?,?,?,?,?)", connection,month,java.sql.Timestamp.from(instant),java.sql.Timestamp.from(instant.plusSeconds(60)),0)
        val days = service.get(u, month).days
        assertEquals(0L, days.first().steps); assertNull(days[1].steps)
        assertEquals(0, n("83.2").compareTo(days.first().weightKg)); assertNull(days.first().waistCm)
        assertFalse(days.first().conditionRecorded); assertTrue(days[1].conditionRecorded)
        assertTrue(service.get(user(), month).days.all { it.weightKg == null && it.steps == null && !it.conditionRecorded })
    }
    @Test fun `workout starts alone are not completed records and actual set states retain meaning`() {
        val u = user()
        val e = workouts.putExercise(u, UUID.randomUUID(), ExerciseWrite("squat","smith","quads",RecordType.WEIGHT_REPS,LoadConvention.MACHINE))
        val sets = (1..3).map { PlannedSet(UUID.randomUUID().toString(),n("100"),10) }
        val routine = workouts.putRoutine(u,UUID.randomUUID(),RoutineWrite("하체 A",listOf(RoutineEntry(UUID.randomUUID().toString(),e.id,sets))))
        workouts.putOverride(u, month, WorkoutOverrideWrite(routine.id))
        val session = workouts.start(u,UUID.randomUUID(),WorkoutStartWrite(month))
        assertFalse(service.get(u,month).days.first().workoutRecorded)
        val entry = session.entries.single()
        workouts.putSession(u,UUID.fromString(session.id),WorkoutSessionWrite(listOf(WorkoutEntryWrite(entry.id,entry.plannedEntryId,e.id,
            listOf(ActualSet(entry.sets[0].id,sets[0].id,SetStatus.DONE,n("80"),8),
                ActualSet(entry.sets[1].id,sets[1].id,SetStatus.SKIPPED),entry.sets[2]))),version=session.version))
        val result = service.get(u,month).days.first()
        assertTrue(result.workoutRecorded); assertEquals(WorkoutStatus.IN_PROGRESS,result.workoutStatus)
        assertEquals("하체 A",result.workoutName); assertEquals(1,result.doneSets); assertEquals(1,result.skippedSets); assertEquals(1,result.pendingSets)
        assertNull(service.get(user(),month).days.first().workoutStatus)
    }
    @Test fun `calendar dates follow account timezone and a deleted account is rejected`() {
        val account = accounts.saveAndFlush(UserAccountEntity(timeZone="Pacific/Honolulu"))
        assertEquals(LocalDate.now(ZoneId.of("Pacific/Honolulu")),service.get(account.id,null).today)
        account.status = AccountStatus.DELETION_REQUESTED; accounts.saveAndFlush(account)
        assertEquals(401,assertFailsWith<ReportException> { service.get(account.id,null) }.status.value())
    }
}
