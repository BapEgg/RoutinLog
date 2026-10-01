package com.bapegg.routinlog.review

import com.bapegg.routinlog.account.persistence.*
import com.bapegg.routinlog.food.*
import com.bapegg.routinlog.profile.*
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
class MealReviewApiTest @Autowired constructor(private val reviews:MealReviewService,private val meals:MealService,
    private val profiles:ProfileService,private val accounts:UserAccountRepository,private val jdbc:JdbcTemplate,private val mvc:MockMvc,private val json:ObjectMapper) {
    private val today get()=LocalDate.now(ZoneId.of("Asia/Seoul"))
    private val week get()=today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1)
    private fun n(s:String)=BigDecimal(s)
    private fun id()=UUID.randomUUID()
    private fun auth(u:UUID)=authentication(UsernamePasswordAuthenticationToken(AuthenticatedUser(u),null,emptyList()))
    private fun user(consent:Boolean=true)=accounts.saveAndFlush(UserAccountEntity()).id.also { if(consent)profiles.save(it,ProfileDto(32,ProfileSex.MALE,n("178"),n("83"),goal=ProfileGoal.MAINTAIN,
        activityLevel=ActivityLevel.LIGHT,exerciseDays=listOf(1,4),exerciseMinutes=40,experience=ExerciseExperience.BEGINNER,units=DisplayUnits.METRIC,nutritionMode=NutritionMode.MANUAL,
        dailyCalories=1200,carbohydrateG=n("100"),proteinG=n("60"),fatG=n("60"),fiberG=n("10"),termsVersion=CURRENT_POLICY_VERSION,privacyVersion=CURRENT_POLICY_VERSION,
        healthConsentVersion=CURRENT_POLICY_VERSION,timeZone="Asia/Seoul",effectiveFrom=week.minusWeeks(1))) }
    private data class Fixture(val user:UUID,val original:FoodDto,val alternative:FoodDto,val plan:MealPlanDto,val alternativeTemplate:MealTemplateDto)
    private fun fixture(days:Int=7,missing:Boolean=false):Fixture {
        val u=user()
        val a=meals.putFood(u,id(),FoodWrite("기본 음식",basisGrams=n("100"),nutrition=NutritionValues(n("1000"),n("100"),n("40"),n("60"),n("10")),preparation=FoodPreparation.COOKED))
        val b=meals.putFood(u,id(),FoodWrite("대체 음식",basisGrams=n("100"),nutrition=NutritionValues(n("1200"),n("100"),n("60"),n("60"),if(missing)null else n("10")),preparation=FoodPreparation.RAW))
        val t=meals.putTemplate(u,id(),MealTemplateWrite("기본 점심",listOf(TemplateItem(a.id,n("100")))))
        val other=meals.putTemplate(u,id(),MealTemplateWrite("대체 점심",listOf(TemplateItem(b.id,n("100")))))
        val plan=meals.putPlan(u,MealPlanWrite(listOf(MealSlot(id().toString(),"점심",t.id))))
        repeat(days){ offset->meals.putMeal(u,id(),MealWrite(week.plusDays(offset.toLong()),plan.slots.single().id,"점심",MealStatus.EATEN,listOf(MealItemWrite(id().toString(),a.id,n("100"))))) }
        return Fixture(u,a,b,plan,other)
    }
    private fun prepare(f:Fixture)=reviews.prepare(f.user,week,ReviewPrepare())
    private fun command(r:MealReviewDto,decision:String="APPLY",optionId:String?=r.suggestedOptionId):MealReviewDecision {
        val o=r.options.firstOrNull { it.id==optionId }
        return MealReviewDecision(r.version,decision,o?.id,o?.items.orEmpty().map { MealReviewAmount(it.foodId,it.grams!!) })
    }
    @Test fun `all routes require auth and writes require consent`() {
        mvc.perform(get("/api/v1/meal-reviews")).andExpect(status().isUnauthorized)
        mvc.perform(post("/api/v1/meal-reviews/$week/prepare").with(auth(user(false))).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden)
    }
    @Test fun `complete records compare saved alternatives against unchanged targets`() {
        val f=fixture();val r=prepare(f)
        assertEquals(f.alternativeTemplate.id,r.suggestedOptionId);assertEquals(today,r.targetDate)
        assertEquals(n("1200"),r.target!!.kcal);assertEquals("DRAFT",r.status)
        assertEquals(r,reviews.prepare(f.user,week,ReviewPrepare()))
        assertTrue(meals.day(f.user,today).plannedMeals.isEmpty())
        mvc.perform(get("/api/v1/meal-reviews/$week").with(auth(f.user))).andExpect(status().isOk).andExpect(header().string("Cache-Control","no-store"))
    }
    @Test fun `missing days or nutrients do not masquerade as deficiency or zero`() {
        val a=fixture(days=3);assertEquals("KEEP",prepare(a).suggestedOptionId)
        val b=fixture(missing=true);val r=prepare(b);assertEquals("KEEP",r.suggestedOptionId)
        val option=r.options.single { it.id==b.alternativeTemplate.id };assertEquals(1,option.totals.fiberG.missingItems)
    }
    @Test fun `apply only adds a dated meal plan not actual records profile or base plan`() {
        val f=fixture();val r=prepare(f);val before=profiles.current(f.user)
        val applied=reviews.decide(f.user,week,command(r).copy(amounts=listOf(MealReviewAmount(f.alternative.id,n("120"))),reason="재료가 없어서"))
        val day=meals.day(f.user,today)
        assertTrue(day.items.isEmpty());assertEquals(1,day.plannedMeals.size);assertEquals(0,day.totals.kcal.knownItems)
        assertEquals(n("1440.00"),MealNutrition.totals(day.plannedMeals.single().items).kcal.knownAmount)
        assertTrue(meals.day(f.user,today.plusDays(1)).plannedMeals.isEmpty())
        assertEquals(before,profiles.current(f.user));assertEquals(f.plan,meals.plan(f.user));assertEquals(1,meals.day(f.user,week).items.size)
        assertEquals("재료가 없어서",applied.decisionReason)
    }
    @Test fun `applied plan label snapshot survives food changes and is used when actually recorded`() {
        val f=fixture();val r=prepare(f);reviews.decide(f.user,week,command(r))
        meals.putFood(f.user,UUID.fromString(f.alternative.id),FoodWrite("수정된 음식",basisGrams=n("80"),nutrition=NutritionValues(kcal=n("10")),preparation=FoodPreparation.AS_SOLD,version=0))
        val planned=meals.day(f.user,today).plannedMeals.single()
        val recorded=meals.putMeal(f.user,id(),MealWrite(today,planned.slotId,planned.slotLabel,MealStatus.EATEN,planned.items.map { MealItemWrite(it.id,it.foodId,n("120")) }))
        assertEquals("대체 음식",recorded.items.single().name);assertEquals(FoodPreparation.RAW,recorded.items.single().preparation)
        assertEquals(n("1440.00"),recorded.totals.kcal.knownAmount)
    }
    @Test fun `retries are idempotent and changed commands cannot overwrite an accepted choice`() {
        val f=fixture();val r=prepare(f);val cmd=command(r);val a=reviews.decide(f.user,week,cmd)
        assertEquals(a,reviews.decide(f.user,week,cmd));assertEquals(2,reviews.history(f.user).items.size)
        assertFailsWith<ReviewException>{reviews.decide(f.user,week,cmd.copy(reason="다른 선택"))}
        assertEquals(1,meals.day(f.user,today).plannedMeals.size)
    }
    @Test fun `hold persists preference without a plan and explicit regeneration preserves prior decisions`() {
        val f=fixture();val r=prepare(f);val held=reviews.decide(f.user,week,command(r,"HOLD"))
        assertTrue(meals.day(f.user,today).plannedMeals.isEmpty());assertEquals("HELD",held.status)
        val draft=reviews.prepare(f.user,week,ReviewPrepare(held.version));assertEquals(2L,draft.version);assertEquals(3,reviews.history(f.user).items.size)
    }
    @Test fun `changed food information or actual source meal invalidates draft`() {
        val f=fixture();val r=prepare(f)
        meals.putFood(f.user,UUID.fromString(f.original.id),FoodWrite("변경",basisGrams=n("100"),nutrition=f.original.nutrition,preparation=FoodPreparation.COOKED,version=0))
        assertEquals("REVIEW_STALE",assertFailsWith<ReviewException>{reviews.decide(f.user,week,command(r))}.code)
        val a=reviews.prepare(f.user,week,ReviewPrepare(r.version));val record=meals.day(f.user,week).items.single()
        meals.deleteMeal(f.user,UUID.fromString(record.id),record.version)
        assertEquals("REVIEW_STALE",assertFailsWith<ReviewException>{reviews.decide(f.user,week,command(a))}.code)
        assertTrue(meals.day(f.user,today).plannedMeals.isEmpty())
    }
    @Test fun `recorded target slot or changed profile cannot be overwritten`() {
        val f=fixture();val r=prepare(f)
        meals.putMeal(f.user,id(),MealWrite(today,r.slotId!!,r.slotLabel!!,MealStatus.SKIPPED,emptyList()))
        assertEquals("REVIEW_STALE",assertFailsWith<ReviewException>{reviews.decide(f.user,week,command(r))}.code)
        val g=fixture();val a=prepare(g);profiles.save(g.user,profiles.current(g.user).copy(dailyCalories=1300))
        assertEquals("REVIEW_STALE",assertFailsWith<ReviewException>{reviews.decide(g.user,week,command(a))}.code)
    }
    @Test fun `tampered options foreign food duplicate rows and invalid quantities fail atomically`() {
        val f=fixture();val r=prepare(f);val cmd=command(r)
        listOf(cmd.copy(optionId=id().toString()),cmd.copy(amounts=cmd.amounts+cmd.amounts),cmd.copy(amounts=listOf(MealReviewAmount(id().toString(),n("100")))),
            cmd.copy(amounts=cmd.amounts.map { it.copy(grams=BigDecimal.ZERO) }),cmd.copy(amounts=cmd.amounts.map { it.copy(grams=n("1.001")) }),cmd.copy(reason="x".repeat(1001))).forEach {
            assertFailsWith<ReviewException>{reviews.decide(f.user,week,it)}
        }
        assertEquals("DRAFT",reviews.get(f.user,week).status);assertTrue(meals.day(f.user,today).plannedMeals.isEmpty())
    }
    @Test fun `future meal plans are readable but eating cannot be recorded in the future`() {
        val f=fixture();val thisWeek=week.plusWeeks(1);val r=reviews.prepare(f.user,thisWeek,ReviewPrepare())
        reviews.decide(f.user,thisWeek,command(r));val future=meals.day(f.user,r.targetDate!!);assertEquals(1,future.plannedMeals.size)
        assertFailsWith<MealApiException>{meals.putMeal(f.user,id(),MealWrite(r.targetDate,r.slotId!!,r.slotLabel!!,MealStatus.SKIPPED,emptyList()))}
        assertFailsWith<MealApiException>{meals.day(f.user,today.plusDays(15))}
    }
    @Test fun `no plan can be held but cannot be applied and old weeks cannot prepare`() {
        val u=user();val r=reviews.prepare(u,week,ReviewPrepare());assertNull(r.targetDate)
        assertEquals("REVIEW_NO_PLAN",assertFailsWith<ReviewException>{reviews.decide(u,week,command(r))}.code)
        assertEquals("HELD",reviews.decide(u,week,command(r,"HOLD")).status)
        assertFailsWith<ReviewException>{reviews.prepare(u,week.minusWeeks(1),ReviewPrepare())}
    }
    @Test fun `account isolation and deletion include all meal plans and decisions`() {
        val f=fixture();val r=prepare(f);reviews.decide(f.user,week,command(r));val other=user()
        mvc.perform(get("/api/v1/meal-reviews/$week").with(auth(other))).andExpect(status().isNotFound)
        assertTrue(reviews.history(other).items.isEmpty());assertTrue(meals.day(other,today).plannedMeals.isEmpty())
        mvc.perform(post("/api/v1/meal-reviews/no-date/prepare").with(auth(f.user)).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest)
        jdbc.update("DELETE FROM user_accounts WHERE id=?",f.user)
        listOf("meal_reviews","meal_review_events","meal_day_plans").forEach { assertEquals(0L,jdbc.queryForObject("SELECT COUNT(*) FROM $it WHERE user_id=?",Long::class.java,f.user)) }
    }
    @Test fun `http decision applies a plan and authenticated cancellation retains decision history`() {
        val f=fixture();val r=prepare(f)
        mvc.perform(post("/api/v1/meal-reviews/$week/decision").with(auth(f.user)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(command(r))))
            .andExpect(status().isOk).andExpect(jsonPath("$.status").value("APPLIED")).andExpect(header().string("Cache-Control","no-store"))
        val path="/api/v1/meal-day-plans/$today/${r.slotId}?version=0"
        mvc.perform(delete(path)).andExpect(status().isUnauthorized)
        mvc.perform(delete(path).with(auth(user()))).andExpect(status().isNoContent)
        assertEquals(1,meals.day(f.user,today).plannedMeals.size)
        mvc.perform(delete(path).with(auth(f.user))).andExpect(status().isNoContent)
        mvc.perform(delete(path).with(auth(f.user))).andExpect(status().isNoContent)
        assertTrue(meals.day(f.user,today).plannedMeals.isEmpty());assertEquals("APPLIED",reviews.history(f.user).items.first().status)
    }
    @Test fun `dated plan cannot be cancelled after recording the meal`() {
        val f=fixture();val r=prepare(f);reviews.decide(f.user,week,command(r))
        meals.putMeal(f.user,id(),MealWrite(today,r.slotId!!,r.slotLabel!!,MealStatus.SKIPPED,emptyList()))
        assertEquals("REVIEW_STALE",assertFailsWith<MealApiException>{meals.deleteDayPlan(f.user,today,UUID.fromString(r.slotId),0)}.code)
        assertEquals(1,meals.day(f.user,today).plannedMeals.size)
    }
}
