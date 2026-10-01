package com.bapegg.routinlog.workout

import com.bapegg.routinlog.account.persistence.UserAccountEntity
import com.bapegg.routinlog.account.persistence.UserAccountRepository
import com.bapegg.routinlog.profile.*
import com.bapegg.routinlog.security.AuthenticatedUser
import jakarta.persistence.EntityManager
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.*

/** H2 transactional/HTTP contract tests; real PostgreSQL migration is checked separately. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class WorkoutFlowTest @Autowired constructor(
    private val mvc:MockMvc,private val mapper:ObjectMapper,private val accounts:UserAccountRepository,
    private val profiles:ProfileService,private val workouts:WorkoutService,private val jdbc:JdbcTemplate,private val entities:EntityManager,
) {
    private val today get()=LocalDate.now(ZoneId.of("Asia/Seoul"))
    private fun id()=UUID.randomUUID().toString()
    private fun auth(user:UUID)=authentication(UsernamePasswordAuthenticationToken(AuthenticatedUser(user),null,emptyList()))
    private fun user(consent:Boolean=true)=accounts.saveAndFlush(UserAccountEntity()).id.also { if(consent) profiles.save(it,ProfileDto(
        age=32,sex=ProfileSex.MALE,heightCm=BigDecimal("178"),initialWeightKg=BigDecimal("83.2"),goal=ProfileGoal.MAINTAIN,
        activityLevel=ActivityLevel.LIGHT,exerciseDays=listOf(1,4),exerciseMinutes=40,experience=ExerciseExperience.BEGINNER,
        units=DisplayUnits.METRIC,nutritionMode=NutritionMode.MANUAL,dailyCalories=2400,carbohydrateG=BigDecimal("280"),proteinG=BigDecimal("170"),fatG=BigDecimal("66.67"),
        termsVersion=CURRENT_POLICY_VERSION,privacyVersion=CURRENT_POLICY_VERSION,healthConsentVersion=CURRENT_POLICY_VERSION,timeZone="Asia/Seoul",effectiveFrom=today,
    )) }
    private fun exerciseRequest(type:RecordType=RecordType.WEIGHT_REPS)=ExerciseWrite("스미스 스쿼트","스미스 머신","대퇴사두",type,LoadConvention.MACHINE)
    private fun exercise(user:UUID,type:RecordType=RecordType.WEIGHT_REPS)=workouts.putExercise(user,UUID.randomUUID(),exerciseRequest(type))
    private fun sets()=listOf(PlannedSet(id(),BigDecimal("60"),5,warmup=true),PlannedSet(id(),BigDecimal("100"),10),PlannedSet(id(),BigDecimal("100"),10))
    private fun routine(user:UUID,exercise:ExerciseDto)=workouts.putRoutine(user,UUID.randomUUID(),RoutineWrite("하체 A",listOf(RoutineEntry(id(),exercise.id,sets()))))
    private fun slots(routine:RoutineDto?)=(1..7).map { WorkoutPlanSlot(it,routine?.id) }
    private fun planned(user:UUID):WorkoutSessionDto {
        val e=exercise(user); val r=routine(user,e); workouts.putPlan(user,WorkoutPlanWrite(slots(r)))
        return workouts.start(user,UUID.randomUUID(),WorkoutStartWrite(today))
    }
    private fun WorkoutEntryDto.write()=WorkoutEntryWrite(id,plannedEntryId,exercise.id,sets,restSeconds,note,replacementReason)
    private fun WorkoutSessionDto.write()=WorkoutSessionWrite(entries.map { it.write() },status,note,version)
    private fun done(set:ActualSet)=set.copy(status=SetStatus.DONE,weightKg=BigDecimal("80"),reps=8)
    private fun putJson(path:String,user:UUID,body:Any)=mvc.perform(put(path).with(auth(user)).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
    private fun day(user:UUID,date:LocalDate=today)=workouts.days(user,date,date).items.single()

    @Test fun `all workout endpoints require the authenticated account and writes require health consent`() {
        listOf("/workout-exercises","/workout-routines","/workout-plan","/workout-days?from=$today&to=$today","/workout-history?exerciseId=${id()}&before=$today").forEach {
            mvc.perform(get("/api/v1$it")).andExpect(status().isUnauthorized)
        }
        listOf("/workout-exercises/${id()}","/workout-routines/${id()}","/workout-plan","/workout-overrides/$today","/workout-sessions/${id()}","/workout-sessions/${id()}/start").forEach {
            mvc.perform(put("/api/v1$it").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized)
        }
        val u=user(false)
        mvc.perform(get("/api/v1/workout-exercises").with(auth(u))).andExpect(status().isOk).andExpect(jsonPath("$.items").isEmpty)
        mvc.perform(get("/api/v1/workout-plan").with(auth(u))).andExpect(status().isOk).andExpect(jsonPath("$.slots.length()").value(7)).andExpect(jsonPath("$.version").isEmpty)
        putJson("/api/v1/workout-exercises/${id()}",u,exerciseRequest()).andExpect(status().isForbidden).andExpect(jsonPath("$.code").value("PROFILE_REQUIRED"))
        putJson("/api/v1/workout-sessions/${id()}/start",u,WorkoutStartWrite(today)).andExpect(status().isForbidden)
    }

    @Test fun `starting captures planned targets but never claims that planned sets were performed`() {
        val u=user(); val session=planned(u); val entry=session.entries.single()
        assertEquals(3,entry.sets.size); assertEquals(BigDecimal("100"),session.planned!!.entries.single().sets[1].weightKg)
        assertTrue(entry.sets.all { it.status==SetStatus.PENDING && it.weightKg==null && it.reps==null && it.durationSeconds==null })
        assertTrue(entry.sets.first().planSetId==session.planned.entries.single().sets.first().id)
        assertTrue(session.planned.entries.single().sets.first().warmup)
        putJson("/api/v1/workout-sessions/${session.id}/start",u,WorkoutStartWrite(today)).andExpect(status().isOk)
            .andExpect(jsonPath("$.startedAt").value(session.startedAt.toString())).andExpect(header().string("Cache-Control",containsString("no-store")))
        assertEquals(session,day(u).session)
        putJson("/api/v1/workout-sessions/${id()}/start",u,WorkoutStartWrite(today)).andExpect(status().isConflict).andExpect(jsonPath("$.code").value("SESSION_EXISTS"))
        putJson("/api/v1/workout-sessions/${session.id}/start",u,WorkoutStartWrite(today.minusDays(1))).andExpect(status().isBadRequest)
    }

    @Test fun `plan revisions project by effective date and same day latest wins while started baseline stays immutable`() {
        val u=user(); val first=planned(u); val oldPlan=jdbc.queryForObject("SELECT payload FROM workout_plan_revisions WHERE user_id=? AND version=0",String::class.java,u)!!
        val historic=mapper.readValue(oldPlan,PlanRevision::class.java).copy(effectiveFrom=today.minusDays(7))
        jdbc.update("UPDATE workout_plan_revisions SET effective_from=?,payload=? WHERE user_id=? AND version=0",historic.effectiveFrom,mapper.writeValueAsString(historic),u)
        assertNull(day(u,today.minusDays(8)).planned)
        assertEquals("하체 A",day(u,today.minusDays(1)).planned!!.routineName)
        val currentRoutine=workouts.routines(u).items.single()
        workouts.putRoutine(u,workoutUuid(currentRoutine.id),RoutineWrite("수정된 루틴",currentRoutine.entries.map { it.copy(sets=it.sets.map { s -> s.copy(reps=15) }) },version=0))
        val currentExercise=workouts.exercises(u).items.single()
        workouts.putExercise(u,workoutUuid(currentExercise.id),exerciseRequest().copy(name="새 종목 이름",version=0))
        assertEquals("하체 A",day(u,today.plusDays(1)).planned!!.routineName)
        workouts.putPlan(u,WorkoutPlanWrite(slots(currentRoutine),0))
        assertEquals("수정된 루틴",day(u).planned!!.routineName)
        assertEquals("새 종목 이름",day(u).planned!!.entries.single().exercise.name)
        assertEquals("하체 A",day(u,today.minusDays(1)).planned!!.routineName)
        assertEquals(first.planned,day(u).session!!.planned)
        workouts.putPlan(u,WorkoutPlanWrite(slots(null),1))
        assertNull(day(u).planned); assertNotNull(day(u).session!!.planned)
        assertEquals(3L,jdbc.queryForObject("SELECT COUNT(*) FROM workout_plan_revisions WHERE user_id=?",Long::class.java,u))
    }

    @Test fun `dated rest and substitute overrides affect projection but never rewrite session baseline`() {
        val u=user(); val first=planned(u); val r=workouts.routines(u).items.single()
        val rest=workouts.putOverride(u,today,WorkoutOverrideWrite(note="일정 변경"))
        assertNull(day(u).planned); assertEquals(rest,day(u).override); assertEquals(first.planned,day(u).session!!.planned)
        putJson("/api/v1/workout-overrides/$today",u,WorkoutOverrideWrite(r.id,version=9)).andExpect(status().isConflict)
        workouts.putOverride(u,today,WorkoutOverrideWrite(r.id,version=0))
        assertEquals(r.id,day(u).planned!!.routineId)
        mvc.perform(delete("/api/v1/workout-overrides/$today").param("version","0").with(auth(u))).andExpect(status().isConflict)
        mvc.perform(delete("/api/v1/workout-overrides/$today").param("version","1").with(auth(u))).andExpect(status().isNoContent)
        assertNull(day(u).override); assertEquals(first.planned,day(u).planned)
        assertEquals(first.planned,day(u).session!!.planned)
    }

    @Test fun `recorded sets allow actual reductions and completion never fabricates missing performance`() {
        val u=user(); val session=planned(u); val initial=session.entries.single().write()
        val entry=initial.copy(sets=listOf(done(initial.sets[0]),initial.sets[1].copy(status=SetStatus.SKIPPED),initial.sets[2]))
        val updated=workouts.putSession(u,workoutUuid(session.id),WorkoutSessionWrite(listOf(entry),WorkoutStatus.COMPLETED,version=0))
        assertEquals(BigDecimal("80"),updated.entries.single().sets.first().weightKg)
        assertEquals(8,updated.entries.single().sets.first().reps)
        assertEquals(listOf(SetStatus.DONE,SetStatus.SKIPPED,SetStatus.PENDING),updated.entries.single().sets.map { it.status })
        assertNotNull(updated.finishedAt)
        val edited=workouts.putSession(u,workoutUuid(session.id),updated.write().copy(note="완료 메모"))
        assertEquals(updated.finishedAt,edited.finishedAt)
        val reopened=workouts.putSession(u,workoutUuid(session.id),edited.write().copy(status=WorkoutStatus.IN_PROGRESS))
        assertNull(reopened.finishedAt); assertEquals(session.startedAt,reopened.startedAt)
        assertEquals(session.planned,reopened.planned)
        putJson("/api/v1/workout-sessions/${session.id}",u,session.write()).andExpect(status().isConflict).andExpect(jsonPath("$.code").value("VERSION_CONFLICT"))
    }

    @Test fun `replacement after completed sets must keep original entry and capture separate replacement target`() {
        val u=user(); val session=planned(u); val original=session.entries.single().write()
        val saved=workouts.putSession(u,workoutUuid(session.id),session.write().copy(entries=listOf(original.copy(sets=original.sets.mapIndexed { i,s -> if(i==0) done(s) else s }))))
        val replacement=workouts.putExercise(u,UUID.randomUUID(),exerciseRequest().copy(name="런지",equipment="덤벨",target="둔근",loadConvention=LoadConvention.PER_HAND))
        val swap=saved.entries.single().write().copy(exerciseId=replacement.id,replacementReason="기구 사용 중")
        putJson("/api/v1/workout-sessions/${session.id}",u,saved.write().copy(entries=listOf(swap))).andExpect(status().isConflict).andExpect(jsonPath("$.code").value("REPLACEMENT_REQUIRES_NEW_ENTRY"))
        val remainder=swap.copy(id=id(),sets=listOf(done(original.sets[1].copy(id=id()))))
        val preserved=saved.entries.single().write().copy(sets=saved.entries.single().sets.map { if(it.status==SetStatus.PENDING) it.copy(status=SetStatus.SKIPPED) else it })
        val result=workouts.putSession(u,workoutUuid(session.id),saved.write().copy(entries=listOf(preserved,remainder)))
        assertEquals(2,result.entries.size)
        assertEquals("대퇴사두",result.entries.first().exercise.target); assertEquals("둔근",result.entries.last().exercise.target)
        assertEquals(BigDecimal("80"),result.entries.first().sets.first().weightKg)
        assertEquals(session.planned,result.planned)
        assertEquals("기구 사용 중",result.entries.last().replacementReason)
    }

    @Test fun `pending-only replacement requires reason and retains immutable planned mapping`() {
        val u=user(); val session=planned(u); val original=session.entries.single().write(); val replacement=exercise(u)
        val swapped=original.copy(exerciseId=replacement.id)
        putJson("/api/v1/workout-sessions/${session.id}",u,session.write().copy(entries=listOf(swapped))).andExpect(status().isBadRequest)
        val saved=workouts.putSession(u,workoutUuid(session.id),session.write().copy(entries=listOf(swapped.copy(replacementReason="혼잡"))))
        assertEquals(replacement.id,saved.entries.single().exercise.id)
        putJson("/api/v1/workout-sessions/${session.id}",u,saved.write().copy(entries=listOf(saved.entries.single().write().copy(plannedEntryId=null)))).andExpect(status().isBadRequest)
        putJson("/api/v1/workout-sessions/${session.id}",u,saved.write().copy(entries=listOf(saved.entries.single().write().copy(sets=listOf(ActualSet(id(),id())))))).andExpect(status().isBadRequest)
    }

    @Test fun `current definitions can be removed without removing history and same entry preserves deleted snapshot`() {
        val u=user(); val session=planned(u); val exercise=workouts.exercises(u).items.single(); val routine=workouts.routines(u).items.single()
        mvc.perform(delete("/api/v1/workout-exercises/${exercise.id}").param("version","0").with(auth(u))).andExpect(status().isConflict).andExpect(jsonPath("$.code").value("RESOURCE_IN_USE"))
        mvc.perform(delete("/api/v1/workout-routines/${routine.id}").param("version","0").with(auth(u))).andExpect(status().isConflict)
        workouts.putOverride(u,today.plusDays(1),WorkoutOverrideWrite(routine.id))
        workouts.putPlan(u,WorkoutPlanWrite(slots(null),0))
        workouts.deleteRoutine(u,workoutUuid(routine.id),0)
        workouts.putExercise(u,workoutUuid(exercise.id),exerciseRequest().copy(name="새 이름",recordType=RecordType.DURATION,version=0))
        workouts.deleteExercise(u,workoutUuid(exercise.id),1)
        val existing=session.entries.single().write().copy(sets=session.entries.single().sets.map(::done))
        val updated=workouts.putSession(u,workoutUuid(session.id),session.write().copy(entries=listOf(existing)))
        assertEquals(exercise.name,updated.entries.single().exercise.name); assertEquals(RecordType.WEIGHT_REPS,updated.entries.single().exercise.recordType)
        assertEquals(routine.id,day(u,today.plusDays(1)).planned!!.routineId)
        assertEquals(1,workouts.history(u,workoutUuid(exercise.id),today.plusDays(1)).items.size)
        putJson("/api/v1/workout-sessions/${session.id}",u,updated.write().copy(entries=listOf(existing.copy(id=id())))).andExpect(status().isNotFound).andExpect(jsonPath("$.code").value("EXERCISE_NOT_FOUND"))
    }

    @Test fun `metrics enforce record type quantity and pending semantics without leaking user input`() {
        val u=user(); val session=planned(u); val entry=session.entries.single().write(); val set=entry.sets.first()
        val invalidSets=listOf(set.copy(weightKg=BigDecimal.ZERO),set.copy(status=SetStatus.SKIPPED,reps=5),set.copy(status=SetStatus.DONE,reps=5),
            done(set).copy(weightKg=BigDecimal("-1")),done(set).copy(weightKg=BigDecimal("2000.001")),done(set).copy(weightKg=BigDecimal("80.0001")),
            done(set).copy(reps=0),done(set).copy(reps=1001),done(set).copy(durationSeconds=20),done(set).copy(note="private-marker\u0000"))
        invalidSets.forEach { invalid -> putJson("/api/v1/workout-sessions/${session.id}",u,session.write().copy(entries=listOf(entry.copy(sets=listOf(invalid))))).andExpect(status().isBadRequest).andExpect(content().string(not(containsString("private-marker")))) }
        val rep=exercise(u,RecordType.REPS); val duration=exercise(u,RecordType.DURATION)
        val valid=listOf(
            WorkoutEntryWrite(id(),exerciseId=rep.id,sets=listOf(ActualSet(id(),status=SetStatus.DONE,reps=20))),
            WorkoutEntryWrite(id(),exerciseId=duration.id,sets=listOf(ActualSet(id(),status=SetStatus.DONE,durationSeconds=600))),
        )
        val result=workouts.putSession(u,workoutUuid(session.id),session.write().copy(entries=valid))
        assertNull(result.entries.first().sets.single().weightKg)
        putJson("/api/v1/workout-sessions/${session.id}",u,result.write().copy(entries=listOf(valid.first().copy(sets=listOf(valid.first().sets.single().copy(weightKg=BigDecimal.ZERO)))))).andExpect(status().isBadRequest)
        putJson("/api/v1/workout-sessions/${session.id}",u,result.write().copy(entries=listOf(valid.last().copy(sets=listOf(valid.last().sets.single().copy(durationSeconds=86401)))))).andExpect(status().isBadRequest)
    }

    @Test fun `identifiers mappings counts labels rest and dates are validated`() {
        val u=user(); val session=planned(u); val entry=session.entries.single().write()
        listOf(entry.copy(id="not-uuid"),entry.copy(restSeconds=-1),entry.copy(restSeconds=1801),entry.copy(sets=emptyList()),entry.copy(sets=List(31) { ActualSet(id()) }),entry.copy(plannedEntryId=id()),entry.copy(note="x".repeat(1001))).forEach {
            putJson("/api/v1/workout-sessions/${session.id}",u,session.write().copy(entries=listOf(it))).andExpect(status().isBadRequest)
        }
        putJson("/api/v1/workout-sessions/${session.id}",u,session.write().copy(entries=listOf(entry,entry))).andExpect(status().isBadRequest)
        putJson("/api/v1/workout-sessions/${session.id}",u,session.write().copy(entries=listOf(entry,entry.copy(id=id())))).andExpect(status().isBadRequest)
        putJson("/api/v1/workout-sessions/${session.id}",u,session.write().copy(entries=List(41) { entry.copy(id=id(),sets=listOf(ActualSet(id()))) })).andExpect(status().isBadRequest)
        putJson("/api/v1/workout-exercises/${id()}",u,exerciseRequest().copy(name=" ")).andExpect(status().isBadRequest)
        putJson("/api/v1/workout-exercises/${id()}",u,exerciseRequest().copy(equipment="x".repeat(81))).andExpect(status().isBadRequest)
        putJson("/api/v1/workout-plan",u,WorkoutPlanWrite(List(7) { WorkoutPlanSlot(1) },0)).andExpect(status().isBadRequest)
        putJson("/api/v1/workout-sessions/${id()}/start",u,WorkoutStartWrite(today.plusDays(1))).andExpect(status().isBadRequest)
        putJson("/api/v1/workout-sessions/${id()}/start",u,WorkoutStartWrite(LocalDate.of(1899,12,31))).andExpect(status().isBadRequest)
        putJson("/api/v1/workout-overrides/${today.plusDays(367)}",u,WorkoutOverrideWrite()).andExpect(status().isBadRequest)
        mvc.perform(get("/api/v1/workout-days").param("from",today.toString()).param("to",today.plusDays(31).toString()).with(auth(u))).andExpect(status().isBadRequest)
        assertEquals(31,workouts.days(u,today,today.plusDays(30)).items.size)
        mvc.perform(get("/api/v1/workout-days").param("from","bad-date").param("to",today.toString()).with(auth(u))).andExpect(status().isBadRequest).andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
    }

    @Test fun `references lists histories and mutations remain isolated between accounts`() {
        val a=user(); val b=user(); val session=planned(a); val r=workouts.routines(a).items.single(); val exercise=workouts.exercises(a).items.single()
        putJson("/api/v1/workout-routines/${id()}",b,RoutineWrite("다른 계정",r.entries)).andExpect(status().isNotFound).andExpect(jsonPath("$.code").value("EXERCISE_NOT_FOUND"))
        putJson("/api/v1/workout-plan",b,WorkoutPlanWrite(slots(r))).andExpect(status().isNotFound).andExpect(jsonPath("$.code").value("ROUTINE_NOT_FOUND"))
        putJson("/api/v1/workout-overrides/$today",b,WorkoutOverrideWrite(r.id)).andExpect(status().isNotFound)
        putJson("/api/v1/workout-sessions/${session.id}",b,session.write()).andExpect(status().isNotFound).andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"))
        mvc.perform(delete("/api/v1/workout-sessions/${session.id}").param("version","0").with(auth(b))).andExpect(status().isNotFound)
        assertTrue(workouts.exercises(b).items.isEmpty()); assertTrue(workouts.routines(b).items.isEmpty()); assertNull(day(b).session)
        val bSession=workouts.start(b,UUID.randomUUID(),WorkoutStartWrite(today))
        putJson("/api/v1/workout-sessions/${bSession.id}",b,bSession.write().copy(entries=listOf(WorkoutEntryWrite(id(),exerciseId=exercise.id,sets=listOf(ActualSet(id()))))))
            .andExpect(status().isNotFound).andExpect(jsonPath("$.code").value("EXERCISE_NOT_FOUND"))
        assertTrue(workouts.history(b,workoutUuid(exercise.id),today.plusDays(1)).items.isEmpty())
        assertNotNull(day(a).session)
    }

    @Test fun `history is recent prior dates only with done sets and caps at ten`() {
        val u=user(); val exercise=exercise(u)
        (1..12).forEach { offset ->
            val session=workouts.start(u,UUID.randomUUID(),WorkoutStartWrite(today.minusDays(offset.toLong())))
            val entry=WorkoutEntryWrite(id(),exerciseId=exercise.id,sets=listOf(ActualSet(id(),status=SetStatus.DONE,weightKg=BigDecimal("50"),reps=offset)))
            workouts.putSession(u,workoutUuid(session.id),session.write().copy(entries=listOf(entry)))
        }
        val pending=workouts.start(u,UUID.randomUUID(),WorkoutStartWrite(today))
        workouts.putSession(u,workoutUuid(pending.id),pending.write().copy(entries=listOf(WorkoutEntryWrite(id(),exerciseId=exercise.id,sets=listOf(ActualSet(id()))))))
        val history=workouts.history(u,workoutUuid(exercise.id),today.plusDays(1)).items
        assertEquals(10,history.size); assertEquals(today.minusDays(1),history.first().date); assertEquals(today.minusDays(10),history.last().date)
        assertEquals(today.minusDays(6),workouts.history(u,workoutUuid(exercise.id),today.minusDays(5)).items.first().date)
    }

    @Test fun `free sessions completion with zero done and explicit deletion remain distinguishable`() {
        val u=user(); val session=workouts.start(u,UUID.randomUUID(),WorkoutStartWrite(today))
        assertNull(session.planned); assertTrue(session.entries.isEmpty())
        val completed=workouts.putSession(u,workoutUuid(session.id),session.write().copy(status=WorkoutStatus.COMPLETED))
        assertNotNull(completed.finishedAt); assertTrue(completed.entries.isEmpty())
        mvc.perform(delete("/api/v1/workout-sessions/${session.id}").param("version","0").with(auth(u))).andExpect(status().isConflict)
        mvc.perform(delete("/api/v1/workout-sessions/${session.id}").param("version","1").with(auth(u))).andExpect(status().isNoContent)
        assertNull(day(u).session)
    }

    @Test fun `session version is mandatory and malformed enums never echo content`() {
        val u=user(); val session=planned(u)
        listOf("{\"entries\":[]}","{\"entries\":[],\"version\":null}","{\"entries\":[],\"version\":0,\"status\":\"private-marker\"}").forEach { body ->
            mvc.perform(put("/api/v1/workout-sessions/${session.id}").with(auth(u)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest).andExpect(content().string(not(containsString("private-marker"))))
        }
        assertEquals(0,day(u).session!!.version)
    }

    @Test fun `stale exercise routine plan and override writes reject destructive overwrite`() {
        val u=user(); val exercise=exercise(u); val r=routine(u,exercise)
        workouts.putExercise(u,workoutUuid(exercise.id),exerciseRequest().copy(name="최신 이름",version=0))
        putJson("/api/v1/workout-exercises/${exercise.id}",u,exerciseRequest().copy(version=0)).andExpect(status().isConflict)
        putJson("/api/v1/workout-routines/${r.id}",u,RoutineWrite("덮어쓰기",r.entries)).andExpect(status().isConflict)
        workouts.putPlan(u,WorkoutPlanWrite(slots(r)))
        putJson("/api/v1/workout-plan",u,WorkoutPlanWrite(slots(null))).andExpect(status().isConflict)
        workouts.putOverride(u,today,WorkoutOverrideWrite(r.id))
        putJson("/api/v1/workout-overrides/$today",u,WorkoutOverrideWrite()).andExpect(status().isConflict)
        assertEquals("최신 이름",workouts.exercises(u).items.single().name)
        assertEquals(r.id,day(u).planned!!.routineId)
    }

    @Test fun `account deletion removes every exercise routine revision override and recorded snapshot`() {
        val u=user(); planned(u); val r=workouts.routines(u).items.single()
        workouts.putOverride(u,today.plusDays(1),WorkoutOverrideWrite(r.id))
        entities.flush(); entities.clear(); accounts.deleteById(u); entities.flush()
        listOf("workout_exercises","workout_routines","workout_routine_exercises","workout_plan_revisions","workout_overrides","workout_sessions").forEach {
            assertEquals(0L,jdbc.queryForObject("SELECT COUNT(*) FROM $it WHERE user_id=?",Long::class.java,u),it)
        }
    }

    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    fun `concurrent starts create exactly one session and safe retries return same snapshot`() {
        val u=user(); val ready=CountDownLatch(2); val release=CountDownLatch(1); val pool=Executors.newFixedThreadPool(2)
        try {
            val results=(1..2).map { pool.submit<Int> { ready.countDown(); check(release.await(10,TimeUnit.SECONDS)); putJson("/api/v1/workout-sessions/${id()}/start",u,WorkoutStartWrite(today)).andReturn().response.status } }
            check(ready.await(10,TimeUnit.SECONDS)); release.countDown()
            assertEquals(listOf(200,409),results.map { it.get(20,TimeUnit.SECONDS) }.sorted())
            val saved=day(u).session!!
            assertEquals(saved,workouts.start(u,workoutUuid(saved.id),WorkoutStartWrite(today)))
        } finally { release.countDown(); pool.shutdownNow(); pool.awaitTermination(5,TimeUnit.SECONDS); accounts.deleteById(u) }
    }
}
