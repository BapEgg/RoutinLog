package com.bapegg.routinlog.review

import com.bapegg.routinlog.account.persistence.*
import com.bapegg.routinlog.profile.*
import com.bapegg.routinlog.workout.*
import com.bapegg.routinlog.condition.*
import com.bapegg.routinlog.security.AuthenticatedUser
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
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.time.*
import java.time.temporal.TemporalAdjusters
import java.util.UUID
import kotlin.test.*

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class WorkoutReviewApiTest @Autowired constructor(private val reviews:WorkoutReviewService,private val workouts:WorkoutService,
    private val conditions:ConditionService,private val profiles:ProfileService,private val accounts:UserAccountRepository,
    private val mvc:MockMvc,private val json:ObjectMapper,private val jdbc:JdbcTemplate) {
    private val today get()=LocalDate.now(ZoneId.of("Asia/Seoul"))
    private val week get()=today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1)
    private fun n(value:String)=BigDecimal(value)
    private fun id()=UUID.randomUUID().toString()
    private fun auth(u:UUID)=authentication(UsernamePasswordAuthenticationToken(AuthenticatedUser(u),null,emptyList()))
    private fun user(consent:Boolean=true)=accounts.saveAndFlush(UserAccountEntity()).id.also { if(consent)profiles.save(it,ProfileDto(
        age=32,sex=ProfileSex.MALE,heightCm=n("178"),initialWeightKg=n("83.2"),goal=ProfileGoal.GAIN,activityLevel=ActivityLevel.LIGHT,
        exerciseDays=listOf(1,4),exerciseMinutes=40,experience=ExerciseExperience.BEGINNER,units=DisplayUnits.METRIC,nutritionMode=NutritionMode.MANUAL,
        dailyCalories=2400,carbohydrateG=n("280"),proteinG=n("170"),fatG=n("66.67"),termsVersion=CURRENT_POLICY_VERSION,privacyVersion=CURRENT_POLICY_VERSION,
        healthConsentVersion=CURRENT_POLICY_VERSION,timeZone="Asia/Seoul",effectiveFrom=week.minusWeeks(1))) }
    private data class Fixture(val user:UUID,val routine:RoutineDto,val session:WorkoutSessionDto)
    private fun fixture(complete:Boolean=true,notes:String?=null,actualKg:String="80",actualReps:Int=8):Fixture {
        val u=user();val e=workouts.putExercise(u,UUID.randomUUID(),ExerciseWrite("스쿼트","스미스 머신","대퇴사두",RecordType.WEIGHT_REPS,LoadConvention.MACHINE))
        val routine=workouts.putRoutine(u,UUID.randomUUID(),RoutineWrite("하체",listOf(
            RoutineEntry(id(),e.id,listOf(PlannedSet(id(),n("20"),10,warmup=true),PlannedSet(id(),n("100"),10),PlannedSet(id(),n("100"),10))),
            RoutineEntry(id(),e.id,listOf(PlannedSet(id(),n("40"),12)))
        )))
        workouts.putOverride(u,week,WorkoutOverrideWrite(routine.id))
        val started=workouts.start(u,UUID.randomUUID(),WorkoutStartWrite(week))
        // Keep only the one actual entry: a duplicated exercise record is intentionally ineligible for a precise comparison.
        val entry=started.entries.first()
        val saved=workouts.putSession(u,UUID.fromString(started.id),WorkoutSessionWrite(listOf(WorkoutEntryWrite(entry.id,entry.plannedEntryId,e.id,
            entry.sets.mapIndexed { i,s->s.copy(status=if(i==0)SetStatus.SKIPPED else SetStatus.DONE,weightKg=if(i==0)null else n(actualKg),reps=if(i==0)null else actualReps,note=notes) })),
            if(complete)WorkoutStatus.COMPLETED else WorkoutStatus.IN_PROGRESS,version=started.version))
        workouts.putPlan(u,WorkoutPlanWrite((1..7).map { WorkoutPlanSlot(it,routine.id) }))
        return Fixture(u,routine,saved)
    }
    private fun prepare(f:Fixture)=reviews.prepare(f.user,week,ReviewPrepare())
    private fun command(r:WorkoutReviewDto,decision:String="APPLY",choice:String=r.suggestedChoice)=ReviewDecision(r.version,decision,choice,
        if(choice=="LAST_PERFORMANCE")r.alternative!! else r.planned)
    @Test fun `endpoints require authentication and preparing requires consent`() {
        mvc.perform(get("/api/v1/workout-reviews")).andExpect(status().isUnauthorized)
        mvc.perform(post("/api/v1/workout-reviews/$week/prepare").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized)
        mvc.perform(post("/api/v1/workout-reviews/$week/prepare").with(auth(user(false))).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden)
    }
    @Test fun `preparation links original source evidence and reduced candidate without changing plan`() {
        val f=fixture();val r=prepare(f)
        assertEquals("LAST_PERFORMANCE",r.suggestedChoice);assertEquals("DRAFT",r.status);assertEquals(today,r.targetDate)
        assertEquals(2,r.planned.size);assertTrue(r.alternative!!.all { it.weightKg!!.compareTo(n("80"))==0&&it.reps==8 })
        assertEquals("https://pubmed.ncbi.nlm.nih.gov/41843416/",r.evidence.single().url)
        assertEquals(0L,r.version);assertEquals(r,reviews.prepare(f.user,week,ReviewPrepare()))
        assertNull(workouts.days(f.user,today,today).items.single().override)
        mvc.perform(get("/api/v1/workout-reviews/$week").with(auth(f.user))).andExpect(status().isOk).andExpect(header().string("Cache-Control","no-store"))
    }
    @Test fun `applies only selected entry and date leaving warmup routine other entry and actual records unchanged`() {
        val f=fixture();val r=prepare(f);val before=workouts.days(f.user,today,today).items.single().planned!!
        val result=reviews.decide(f.user,week,command(r))
        assertEquals("APPLIED",result.status)
        val after=workouts.days(f.user,today,today).items.single().planned!!
        assertEquals(before.entries.first().sets.first(),after.entries.first().sets.first())
        assertTrue(after.entries.first().sets.drop(1).all { it.weightKg!!.compareTo(n("80"))==0&&it.reps==8 })
        assertEquals(before.entries[1],after.entries[1]);assertEquals(f.routine,workouts.routines(f.user).items.single())
        assertEquals(f.session,workouts.days(f.user,week,week).items.single().session)
        assertNull(workouts.days(f.user,today.plusDays(1),today.plusDays(1)).items.single().override)
    }
    @Test fun `decision retries are idempotent but a changed command cannot rewrite the first choice`() {
        val f=fixture();val r=prepare(f);val write=command(r)
        val first=reviews.decide(f.user,week,write)
        assertEquals(first,reviews.decide(f.user,week,write))
        assertEquals(0L,workouts.days(f.user,today,today).items.single().override!!.version)
        assertEquals(2,reviews.history(f.user).items.size)
        assertFailsWith<ReviewException>{reviews.decide(f.user,week,write.copy(reason="different"))}
    }
    @Test fun `custom targets and reason are retained beside original proposal`() {
        val f=fixture();val r=prepare(f)
        reviews.decide(f.user,week,ReviewDecision(r.version,"APPLY","CUSTOM",r.planned.map { it.copy(weightKg=n("75.5"),reps=9) },"시간이 부족해서"))
        val history=reviews.history(f.user).items
        assertEquals("CUSTOM",history.first().choice);assertEquals("시간이 부족해서",history.first().decisionReason)
        assertTrue(history.first().chosen!!.all { it.reps==9 });assertEquals("DRAFT",history.last().status);assertEquals(10,history.last().planned.first().reps)
    }
    @Test fun `hold saves choice without writing an override and can explicitly regenerate`() {
        val f=fixture();val r=prepare(f);val held=reviews.decide(f.user,week,command(r,"HOLD"))
        assertEquals("HELD",held.status);assertNull(workouts.days(f.user,today,today).items.single().override)
        val next=reviews.prepare(f.user,week,ReviewPrepare(held.version))
        assertEquals("DRAFT",next.status);assertEquals(2L,next.version);assertEquals(3,reviews.history(f.user).items.size)
    }
    @Test fun `changed condition or profile invalidates a prepared analysis`() {
        val f=fixture();val r=prepare(f);conditions.save(f.user,week,ConditionWrite(ConditionValues(fatigue="HIGH")))
        assertEquals("REVIEW_STALE",assertFailsWith<ReviewException>{reviews.decide(f.user,week,command(r))}.code)
        assertNull(workouts.days(f.user,today,today).items.single().override)
        val refreshed=reviews.prepare(f.user,week,ReviewPrepare(r.version));assertNull(refreshed.alternative)
        val p=profiles.current(f.user);profiles.save(f.user,p.copy(goal=ProfileGoal.MAINTAIN))
        assertEquals("REVIEW_STALE",assertFailsWith<ReviewException>{reviews.decide(f.user,week,command(refreshed))}.code)
    }
    @Test fun `changed target plan or started session cannot be overwritten`() {
        val f=fixture();val r=prepare(f)
        workouts.putOverride(f.user,today,WorkoutOverrideWrite(null,"일정 변경"))
        assertEquals("REVIEW_STALE",assertFailsWith<WorkoutApiException>{reviews.decide(f.user,week,command(r))}.code)
        val g=fixture();val q=prepare(g);workouts.start(g.user,UUID.randomUUID(),WorkoutStartWrite(today))
        assertEquals("REVIEW_STALE",assertFailsWith<WorkoutApiException>{reviews.decide(g.user,week,command(q))}.code)
    }
    @Test fun `partial sessions notes fatigue and larger repetitions do not trigger a reduction recommendation`() {
        listOf(fixture(complete=false),fixture(notes="무릎 불편"),fixture(actualKg="80",actualReps=15)).forEach { f->assertNull(prepare(f).alternative) }
        val f=fixture();conditions.save(f.user,week,ConditionWrite(ConditionValues(soreness="HIGH")));assertNull(prepare(f).alternative)
    }
    @Test fun `old source edits invalidate evidence and regenerated view preserves old version in history`() {
        val f=fixture();val r=prepare(f)
        workouts.deleteSession(f.user,UUID.fromString(f.session.id),f.session.version)
        assertEquals("REVIEW_STALE",assertFailsWith<ReviewException>{reviews.decide(f.user,week,command(r))}.code)
        val updated=reviews.prepare(f.user,week,ReviewPrepare(r.version));assertNull(updated.alternative)
        assertEquals(2,reviews.history(f.user).items.size)
        assertEquals("VERSION_CONFLICT",assertFailsWith<ReviewException>{reviews.decide(f.user,week,command(r))}.code)
    }
    @Test fun `uninterpreted condition notes and changed equipment prevent precise performance matching`() {
        val f=fixture();conditions.save(f.user,week,ConditionWrite(ConditionValues(memo="무릎이 조금 불편했음")))
        assertNull(prepare(f).alternative)
        val g=fixture();val exercise=g.session.entries.first().exercise
        workouts.putExercise(g.user,UUID.fromString(exercise.id),ExerciseWrite(exercise.name,"바벨",exercise.target,RecordType.WEIGHT_REPS,LoadConvention.TOTAL,version=0))
        workouts.putPlan(g.user,WorkoutPlanWrite((1..7).map { WorkoutPlanSlot(it,g.routine.id) },version=0))
        assertNull(prepare(g).alternative)
    }
    @Test fun `raising the future goal does not turn a completed original goal into a missed target`() {
        val f=fixture(actualKg="100",actualReps=10)
        workouts.putRoutine(f.user,UUID.fromString(f.routine.id),RoutineWrite(f.routine.name,f.routine.entries.map { e->e.copy(sets=e.sets.map { s->if(s.warmup)s else s.copy(weightKg=n("120")) }) },version=f.routine.version))
        workouts.putPlan(f.user,WorkoutPlanWrite((1..7).map { WorkoutPlanSlot(it,f.routine.id) },version=0))
        assertNull(prepare(f).alternative)
    }
    @Test fun `untrusted set identity values and mislabeled choices fail atomically`() {
        val f=fixture();val r=prepare(f)
        listOf(command(r).copy(targets=r.alternative!!.drop(1)),command(r).copy(targets=r.alternative.map { it.copy(setId=id()) }),
            command(r).copy(choice="KEEP"),command(r).copy(choice="CUSTOM",targets=r.planned.map { it.copy(weightKg=n("-1")) }),
            command(r).copy(choice="CUSTOM",targets=r.planned.map { it.copy(reps=0) }),command(r).copy(reason="x".repeat(1001))).forEach {
            assertFailsWith<ReviewException>{reviews.decide(f.user,week,it)}
        }
        assertEquals("DRAFT",reviews.get(f.user,week).status);assertNull(workouts.days(f.user,today,today).items.single().override)
    }
    @Test fun `empty plan can be held but cannot masquerade as an applied plan`() {
        val u=user();val r=reviews.prepare(u,week,ReviewPrepare());assertNull(r.targetDate)
        assertEquals("REVIEW_NO_PLAN",assertFailsWith<ReviewException>{reviews.decide(u,week,command(r))}.code)
        assertEquals("HELD",reviews.decide(u,week,command(r,"HOLD")).status)
        assertFailsWith<ReviewException>{reviews.prepare(u,week.minusWeeks(1),ReviewPrepare())}
    }
    @Test fun `owner isolation and account deletion cover drafts decisions and evidence snapshots`() {
        val f=fixture();prepare(f);val other=user()
        assertTrue(reviews.history(other).items.isEmpty())
        mvc.perform(get("/api/v1/workout-reviews/$week").with(auth(other))).andExpect(status().isNotFound)
        mvc.perform(post("/api/v1/workout-reviews/not-date/prepare").with(auth(other)).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest)
        jdbc.update("DELETE FROM user_accounts WHERE id=?",f.user)
        assertEquals(0L,jdbc.queryForObject("SELECT COUNT(*) FROM workout_review_events WHERE user_id=?",Long::class.java,f.user))
        assertEquals(0L,jdbc.queryForObject("SELECT COUNT(*) FROM workout_reviews WHERE user_id=?",Long::class.java,f.user))
    }
}
