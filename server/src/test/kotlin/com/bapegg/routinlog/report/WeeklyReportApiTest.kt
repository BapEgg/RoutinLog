package com.bapegg.routinlog.report

import com.bapegg.routinlog.account.persistence.*
import com.bapegg.routinlog.profile.*
import com.bapegg.routinlog.body.*
import com.bapegg.routinlog.food.*
import com.bapegg.routinlog.condition.*
import com.bapegg.routinlog.workout.*
import com.bapegg.routinlog.security.AuthenticatedUser
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.*
import java.time.temporal.TemporalAdjusters
import java.util.UUID
import kotlin.test.*

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class WeeklyReportApiTest @Autowired constructor(private val reports:WeeklyReportService,private val accounts:UserAccountRepository,
    private val profiles:ProfileService,private val meals:MealService,private val body:BodyMeasurementService,
    private val conditions:ConditionService,private val workouts:WorkoutService,private val mvc:MockMvc) {
    private val today get()=LocalDate.now(ZoneId.of("Asia/Seoul"))
    private val week get()=today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1)
    private fun n(value:String)=BigDecimal(value)
    private fun user()=accounts.saveAndFlush(UserAccountEntity()).id.also { profiles.save(it,ProfileDto(
        age=32,sex=ProfileSex.MALE,heightCm=n("178"),initialWeightKg=n("83.2"),goal=ProfileGoal.MAINTAIN,
        activityLevel=ActivityLevel.LIGHT,exerciseDays=listOf(1,4),exerciseMinutes=40,experience=ExerciseExperience.BEGINNER,
        units=DisplayUnits.METRIC,nutritionMode=NutritionMode.MANUAL,dailyCalories=2400,carbohydrateG=n("280"),proteinG=n("170"),fatG=n("66.67"),
        termsVersion=CURRENT_POLICY_VERSION,privacyVersion=CURRENT_POLICY_VERSION,healthConsentVersion=CURRENT_POLICY_VERSION,timeZone="Asia/Seoul",effectiveFrom=week.minusWeeks(1))) }
    private fun auth(u:UUID)=authentication(UsernamePasswordAuthenticationToken(AuthenticatedUser(u),null,emptyList()))
    private fun food(u:UUID,nutrition:NutritionValues=NutritionValues(kcal=n("160"),proteinG=n("20")))=
        meals.putFood(u,UUID.randomUUID(),FoodWrite("food",basisGrams=n("80"),nutrition=nutrition,preparation=FoodPreparation.AS_SOLD))
    private fun eat(u:UUID,f:FoodDto,date:LocalDate=week,grams:BigDecimal?=n("120"))=meals.putMeal(u,UUID.randomUUID(),
        MealWrite(date,UUID.randomUUID().toString(),"점심",MealStatus.EATEN,listOf(MealItemWrite(UUID.randomUUID().toString(),f.id,grams))))
    @Test fun `endpoint is authenticated private and defaults to last complete account week`() {
        mvc.perform(get("/api/v1/reports/weekly")).andExpect(status().isUnauthorized)
        val u=user()
        mvc.perform(get("/api/v1/reports/weekly").with(auth(u))).andExpect(status().isOk)
            .andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.from").value(week.toString()))
            .andExpect(jsonPath("$.to").value(week.plusDays(6).toString()))
        mvc.perform(get("/api/v1/reports/weekly").param("week","wrong").with(auth(u))).andExpect(status().isBadRequest)
    }
    @Test fun `future non Monday and underflow weeks are rejected`() {
        val u=user()
        listOf(week.plusWeeks(2),week.plusDays(1),LocalDate.MIN,LocalDate.of(1900,1,1)).forEach { date ->
            assertEquals(400,assertFailsWith<ReportException>{reports.get(u,date)}.status.value())
        }
    }
    @Test fun `empty observations stay unknown even when target is known`() {
        val report=reports.get(user(),null)
        assertEquals(7,report.mealDays.size);assertTrue(report.nutrition.all { it.recorded==null })
        assertNull(report.weight.value);assertNull(report.weight.change);assertTrue(report.steps.isEmpty());assertTrue(report.conditions.isEmpty())
        assertEquals(n("16800"),report.nutrition.first().target)
    }
    @Test fun `targets follow each historical revision and not current target times seven`() {
        val u=user();val p=profiles.current(u)
        profiles.save(u,p.copy(dailyCalories=2600,effectiveFrom=week.plusDays(3)))
        val kcal=reports.get(u,null).nutrition.first()
        assertEquals(n("17600"),kcal.target);assertEquals(7,kcal.targetDays)
        profiles.save(u,profiles.current(u).copy(nutritionMode=NutritionMode.NONE,dailyCalories=null,carbohydrateG=null,proteinG=null,fatG=null,fiberG=null,effectiveFrom=week.plusDays(5)))
        val updated=reports.get(u,null).nutrition.first()
        assertEquals(n("12400"),updated.target);assertEquals(5,updated.targetDays)
    }
    @Test fun `nutrition uses original snapshots and distinguishes unknown grams and missing nutrients`() {
        val u=user();val f=food(u);eat(u,f);eat(u,f,week.plusDays(1),null)
        meals.putFood(u,UUID.fromString(f.id),FoodWrite("changed",basisGrams=n("80"),nutrition=NutritionValues(kcal=n("999")),preparation=FoodPreparation.AS_SOLD,version=f.version))
        val report=reports.get(u,null);val kcal=report.nutrition.first();val fiber=report.nutrition.last()
        assertEquals(n("240.00"),kcal.recorded);assertEquals(1,kcal.missingItems);assertNull(fiber.recorded);assertEquals(2,fiber.missingItems)
        assertEquals(2,report.mealDays.count { it.items.isNotEmpty() })
    }
    @Test fun `explicit skipped meals produce zero but never turn unknown nutrients into zero`() {
        val u=user()
        meals.putMeal(u,UUID.randomUUID(),MealWrite(week,UUID.randomUUID().toString(),"아침",MealStatus.SKIPPED,emptyList()))
        assertEquals(n("0.00"),reports.get(u,null).nutrition.first().recorded)
        eat(u,food(u,NutritionValues()))
        assertNull(reports.get(u,null).nutrition.first().recorded)
    }
    @Test fun `weekly sum rounds original proportions once rather than rounded daily totals`() {
        val u=user();val f=meals.putFood(u,UUID.randomUUID(),FoodWrite("fraction",basisGrams=n("3"),nutrition=NutritionValues(kcal=n("1")),preparation=FoodPreparation.RAW))
        repeat(3){eat(u,f,week.plusDays(it.toLong()),n("1"))}
        assertEquals(n("1.00"),reports.get(u,null).nutrition.first().recorded)
    }
    @Test fun `averages have independent sample counts and no missing day zero padding`() {
        val u=user();body.put(u,week.minusDays(1),PutBodyMeasurement(weightKg=n("85")))
        body.put(u,week,PutBodyMeasurement(weightKg=n("83"),waistCm=n("80")))
        body.put(u,week.plusDays(1),PutBodyMeasurement(weightKg=n("81")))
        conditions.save(u,week,ConditionWrite(ConditionValues(sleepMinutes=0,fatigue="HIGH")))
        val report=reports.get(u,null)
        assertEquals(n("82.000"),report.weight.value);assertEquals(n("-3.000"),report.weight.change);assertEquals(2,report.weight.count)
        assertEquals(1,report.waist.count);assertNull(report.waist.change);assertEquals(0,report.conditions.single().values.sleepMinutes)
        val other=reports.get(user(),null);assertNull(other.weight.value);assertTrue(other.conditions.isEmpty())
    }
    @Test fun `current week includes only dates up to today and reads later edits freshly`() {
        val u=user();val current=week.plusWeeks(1)
        assertEquals(today,reports.get(u,current).to)
        val meal=eat(u,food(u),today)
        assertEquals(n("240.00"),reports.get(u,current).nutrition.first().recorded)
        meals.deleteMeal(u,UUID.fromString(meal.id),meal.version)
        assertNull(reports.get(u,current).nutrition.first().recorded)
    }
    @Test fun `workout report retains session baseline and actual changed sets`() {
        val u=user();val e=workouts.putExercise(u,UUID.randomUUID(),ExerciseWrite("squat","smith","quads",RecordType.WEIGHT_REPS,LoadConvention.MACHINE))
        val set=PlannedSet(UUID.randomUUID().toString(),n("100"),10)
        val r=workouts.putRoutine(u,UUID.randomUUID(),RoutineWrite("legs",listOf(RoutineEntry(UUID.randomUUID().toString(),e.id,listOf(set)))))
        workouts.putOverride(u,week,WorkoutOverrideWrite(r.id,"equipment available"))
        val session=workouts.start(u,UUID.randomUUID(),WorkoutStartWrite(week))
        val entry=session.entries.single()
        workouts.putSession(u,UUID.fromString(session.id),WorkoutSessionWrite(listOf(WorkoutEntryWrite(entry.id,entry.plannedEntryId,e.id,
            listOf(ActualSet(entry.sets.single().id,set.id,SetStatus.DONE,n("80"),8)))),WorkoutStatus.COMPLETED,version=session.version))
        val day=reports.get(u,null).workouts.first()
        assertEquals(n("100"),day.session!!.planned!!.entries.single().sets.single().weightKg)
        assertEquals(n("80"),day.session.entries.single().sets.single().weightKg)
        assertNull(reports.get(user(),null).workouts.first().session)
    }
}
