package com.bapegg.routinlog.food

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

/** H2 HTTP/transaction contracts; final PostgreSQL migration smoke is separate. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MealFlowTest @Autowired constructor(
    private val mvc:MockMvc, private val mapper:ObjectMapper, private val accounts:UserAccountRepository,
    private val profiles:ProfileService, private val meals:MealService, private val jdbc:JdbcTemplate, private val entities:EntityManager,
) {
    private val today get()=LocalDate.now(ZoneId.of("Asia/Seoul"))
    private fun auth(id:UUID)=authentication(UsernamePasswordAuthenticationToken(AuthenticatedUser(id),null,emptyList()))
    private fun user(consented:Boolean=true):UUID=accounts.saveAndFlush(UserAccountEntity()).id.also { if(consented) profiles.save(it,profile()) }
    private fun profile(date:LocalDate=today,version:Long?=null,calories:Int=2400)=ProfileDto(
        age=32,sex=ProfileSex.MALE,heightCm=BigDecimal("178.00"),initialWeightKg=BigDecimal("83.200"),goal=ProfileGoal.MAINTAIN,
        activityLevel=ActivityLevel.LIGHT,exerciseDays=listOf(1,4),exerciseMinutes=40,experience=ExerciseExperience.BEGINNER,
        units=DisplayUnits.METRIC,nutritionMode=NutritionMode.MANUAL,dailyCalories=calories,carbohydrateG=BigDecimal("280"),proteinG=BigDecimal("170"),fatG=BigDecimal("66.67"),
        termsVersion=CURRENT_POLICY_VERSION,privacyVersion=CURRENT_POLICY_VERSION,healthConsentVersion=CURRENT_POLICY_VERSION,timeZone="Asia/Seoul",effectiveFrom=date,version=version,
    )
    private fun foodRequest(version:Long?=null)=FoodWrite("테스트 식품","직접 입력",BigDecimal("80"),NutritionValues(kcal=BigDecimal("160"),proteinG=BigDecimal("8"),fatG=BigDecimal.ZERO),FoodPreparation.AS_SOLD,"80g 제품 표기",version)
    private fun food(user:UUID,request:FoodWrite=foodRequest())=meals.putFood(user,UUID.randomUUID(),request)
    private fun mealRequest(food:FoodDto,grams:String?="120",slot:String=UUID.randomUUID().toString(),version:Long?=null)=MealWrite(
        today,slot,"점심",MealStatus.EATEN,listOf(MealItemWrite(UUID.randomUUID().toString(),food.id,grams?.let(::BigDecimal))),version=version,
    )
    private fun putJson(path:String,id:UUID,body:Any)=mvc.perform(put(path).with(auth(id)).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))

    @Test fun `all meal routes require authentication and consent gates writes but not empty reads`() {
        listOf("/api/v1/foods","/api/v1/meal-templates","/api/v1/meal-plan","/api/v1/meal-records?date=$today").forEach { mvc.perform(get(it)).andExpect(status().isUnauthorized) }
        mvc.perform(put("/api/v1/foods/${UUID.randomUUID()}").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(foodRequest()))).andExpect(status().isUnauthorized)
        val id=user(false)
        mvc.perform(get("/api/v1/foods").with(auth(id))).andExpect(status().isOk).andExpect(jsonPath("$.items").isEmpty)
        mvc.perform(get("/api/v1/meal-plan").with(auth(id))).andExpect(status().isOk)
            .andExpect(jsonPath("$.slots.length()").value(3)).andExpect(jsonPath("$.version").isEmpty)
            .andExpect(jsonPath("$.slots[0].id").value("00000000-0000-0000-0000-000000000001"))
        mvc.perform(get("/api/v1/meal-records").param("date",today.toString()).with(auth(id)))
            .andExpect(status().isOk).andExpect(jsonPath("$.target").isEmpty).andExpect(jsonPath("$.items").isEmpty)
        putJson("/api/v1/foods/${UUID.randomUUID()}",id,foodRequest()).andExpect(status().isForbidden).andExpect(jsonPath("$.code").value("PROFILE_REQUIRED"))
    }

    @Test fun `80g label at 160kcal produces 240kcal for 120g without inventing missing nutrients`() {
        val id=user(); val food=food(id); val record=UUID.randomUUID()
        putJson("/api/v1/meal-records/$record",id,mealRequest(food)).andExpect(status().isOk)
            .andExpect(jsonPath("$.totals.kcal.knownAmount").value(240)).andExpect(jsonPath("$.totals.proteinG.knownAmount").value(12))
            .andExpect(jsonPath("$.items[0].nutrition.kcal").value(160)).andExpect(jsonPath("$.items[0].basisGrams").value(80))
            .andExpect(jsonPath("$.items[0].source").value("USER_ENTERED")).andExpect(jsonPath("$.items[0].nutrition.fiberG").isEmpty)
            .andExpect(jsonPath("$.totals.fiberG.knownItems").value(0)).andExpect(jsonPath("$.totals.fiberG.missingItems").value(1))
            .andExpect(jsonPath("$.totals.fatG.knownItems").value(1)).andExpect(jsonPath("$.totals.fatG.knownAmount").value(0))
            .andExpect(header().string("Cache-Control",containsString("no-store")))
        assertEquals(BigDecimal("240.00"),meals.day(id,today).totals.kcal.knownAmount)
    }

    @Test fun `unknown quantity makes every nutrient missing even when the label contains zero`() {
        val id=user(); val f=food(id)
        meals.putMeal(id,UUID.randomUUID(),mealRequest(f,grams=null))
        val totals=meals.day(id,today).totals
        assertEquals(NutrientTotal(BigDecimal("0.00"),0,1),totals.kcal)
        assertEquals(NutrientTotal(BigDecimal("0.00"),0,1),totals.fatG)
        assertEquals(NutrientTotal(BigDecimal("0.00"),0,1),totals.fiberG)
    }

    @Test fun `daily aggregate uses unrounded items and reports partial information explicitly`() {
        val id=user()
        val fraction=food(id,foodRequest().copy(basisGrams=BigDecimal("3"),nutrition=NutritionValues(kcal=BigDecimal.ONE,fiberG=BigDecimal.ZERO)))
        meals.putMeal(id,UUID.randomUUID(),mealRequest(fraction,"1"))
        meals.putMeal(id,UUID.randomUUID(),mealRequest(fraction,"1"))
        val unknown=food(id,foodRequest().copy(nutrition=NutritionValues()))
        meals.putMeal(id,UUID.randomUUID(),mealRequest(unknown,"1"))
        val day=meals.day(id,today)
        assertEquals(BigDecimal("0.33"),day.items.first { it.items.single().foodId==fraction.id }.totals.kcal.knownAmount)
        assertEquals(NutrientTotal(BigDecimal("0.67"),2,1),day.totals.kcal)
        assertEquals(NutrientTotal(BigDecimal("0.00"),2,1),day.totals.fiberG)
        assertEquals(NutrientTotal(BigDecimal("0.00"),0,3),day.totals.proteinG)
    }

    @Test fun `flexible plan protects assigned templates and templates protect their referenced foods`() {
        val id=user(); val f=food(id); val t=meals.putTemplate(id,UUID.randomUUID(),MealTemplateWrite("평소 식사",listOf(TemplateItem(f.id,BigDecimal("120")))))
        val slots=(1..5).map { MealSlot(UUID.randomUUID().toString(),"식사 $it",t.id) }
        val plan=meals.putPlan(id,MealPlanWrite(slots))
        assertEquals(5,plan.slots.size); assertEquals(0L,plan.version)
        mvc.perform(delete("/api/v1/foods/${f.id}").param("version","0").with(auth(id)))
            .andExpect(status().isConflict).andExpect(jsonPath("$.code").value("RESOURCE_IN_USE"))
        mvc.perform(delete("/api/v1/meal-templates/${t.id}").param("version","0").with(auth(id)))
            .andExpect(status().isConflict).andExpect(jsonPath("$.code").value("RESOURCE_IN_USE"))
        meals.putPlan(id,MealPlanWrite(listOf(slots.first().copy(templateId=null)),0))
        meals.deleteTemplate(id,uuid(t.id),0); meals.deleteFood(id,uuid(f.id),0)
        assertTrue(meals.foods(id).items.isEmpty()); assertTrue(meals.templates(id).items.isEmpty())
    }

    @Test fun `old food nutrition and meal slot labels survive food template and plan edits`() {
        val id=user(); val original=food(id); val replacement=food(id,foodRequest().copy(name="다른 음식",nutrition=NutritionValues(kcal=BigDecimal("400"))))
        val t=meals.putTemplate(id,UUID.randomUUID(),MealTemplateWrite("기본",listOf(TemplateItem(original.id,BigDecimal("120")))))
        val slot=MealSlot(UUID.randomUUID().toString(),"점심",t.id)
        meals.putPlan(id,MealPlanWrite(listOf(slot)))
        val recordId=UUID.randomUUID(); val request=mealRequest(original,slot=slot.id)
        meals.putMeal(id,recordId,request)
        meals.putFood(id,uuid(original.id),foodRequest(0).copy(name="변경한 이름",nutrition=NutritionValues(kcal=BigDecimal("999"))))
        meals.putTemplate(id,uuid(t.id),MealTemplateWrite("새 구성",listOf(TemplateItem(replacement.id,BigDecimal("80"))),version=0))
        meals.putPlan(id,MealPlanWrite(listOf(MealSlot(UUID.randomUUID().toString(),"저녁")),0))
        meals.deleteFood(id,uuid(original.id),1)
        val updated=meals.putMeal(id,recordId,request.copy(items=request.items.map { it.copy(grams=BigDecimal("160")) },slotLabel="새 점심",version=0))
        assertEquals("테스트 식품",updated.items.single().name)
        assertEquals(BigDecimal("160.00"),updated.items.single().nutrition.kcal)
        assertEquals(BigDecimal("320.00"),updated.totals.kcal.knownAmount)
        assertEquals("점심",updated.slotLabel)
        // Once removed from this record, an old item id cannot recreate a deleted food's snapshot.
        val next=request.copy(items=listOf(MealItemWrite(UUID.randomUUID().toString(),replacement.id,BigDecimal("80"))),version=1)
        meals.putMeal(id,recordId,next)
        putJson("/api/v1/meal-records/$recordId",id,request.copy(version=2)).andExpect(status().isNotFound).andExpect(jsonPath("$.code").value("FOOD_NOT_FOUND"))
        assertEquals(replacement.id,meals.day(id,today).items.single().items.single().foodId)
    }

    @Test fun `food template plan and record references never cross account boundaries`() {
        val a=user(); val b=user(); val f=food(a)
        val template=meals.putTemplate(a,UUID.randomUUID(),MealTemplateWrite("개인 식사",listOf(TemplateItem(f.id,BigDecimal("80")))))
        val record=meals.putMeal(a,UUID.randomUUID(),mealRequest(f))
        putJson("/api/v1/meal-templates/${UUID.randomUUID()}",b,MealTemplateWrite("침입",listOf(TemplateItem(f.id,BigDecimal.ONE))))
            .andExpect(status().isNotFound).andExpect(jsonPath("$.code").value("FOOD_NOT_FOUND"))
        putJson("/api/v1/meal-plan",b,MealPlanWrite(listOf(MealSlot(UUID.randomUUID().toString(),"점심",template.id))))
            .andExpect(status().isNotFound).andExpect(jsonPath("$.code").value("TEMPLATE_NOT_FOUND"))
        putJson("/api/v1/meal-records/${UUID.randomUUID()}",b,mealRequest(f)).andExpect(status().isNotFound)
        mvc.perform(delete("/api/v1/meal-records/${record.id}").param("version","0").with(auth(b))).andExpect(status().isNotFound)
        assertTrue(meals.foods(b).items.isEmpty()); assertTrue(meals.templates(b).items.isEmpty()); assertTrue(meals.day(b,today).items.isEmpty())
        assertEquals(1,meals.day(a,today).items.size)
    }

    @Test fun `optimistic versions reject stale edits and deletes without overwriting records`() {
        val id=user(); val f=food(id)
        meals.putFood(id,uuid(f.id),foodRequest(0).copy(name="최신 음식"))
        putJson("/api/v1/foods/${f.id}",id,foodRequest(0)).andExpect(status().isConflict).andExpect(jsonPath("$.code").value("VERSION_CONFLICT"))
        mvc.perform(delete("/api/v1/foods/${f.id}").param("version","0").with(auth(id))).andExpect(status().isConflict)
        val t=meals.putTemplate(id,UUID.randomUUID(),MealTemplateWrite("기본",listOf(TemplateItem(f.id,BigDecimal.ONE))))
        putJson("/api/v1/meal-templates/${t.id}",id,MealTemplateWrite("덮어쓰기",t.items)).andExpect(status().isConflict)
        meals.putPlan(id,MealPlanWrite(listOf(MealSlot(UUID.randomUUID().toString(),"식사"))))
        putJson("/api/v1/meal-plan",id,MealPlanWrite(listOf(MealSlot(UUID.randomUUID().toString(),"대체")))).andExpect(status().isConflict)
        val recordId=UUID.randomUUID(); val request=mealRequest(f)
        meals.putMeal(id,recordId,request)
        meals.putMeal(id,recordId,request.copy(note="새 메모",version=0))
        putJson("/api/v1/meal-records/$recordId",id,request.copy(version=0)).andExpect(status().isConflict)
        mvc.perform(delete("/api/v1/meal-records/$recordId").param("version","0").with(auth(id))).andExpect(status().isConflict)
        assertEquals("새 메모",meals.day(id,today).items.single().note)
    }

    @Test fun `skipped and unrecorded meals remain distinct and deleting restores unrecorded state`() {
        val id=user(); val record=UUID.randomUUID(); val request=MealWrite(today,UUID.randomUUID().toString(),"야식",MealStatus.SKIPPED,emptyList())
        assertTrue(meals.day(id,today).items.isEmpty())
        val skipped=meals.putMeal(id,record,request)
        assertEquals(MealStatus.SKIPPED,skipped.status); assertEquals(0,skipped.totals.kcal.knownItems); assertEquals(0,skipped.totals.kcal.missingItems)
        putJson("/api/v1/meal-records/$record",id,request.copy(status=MealStatus.EATEN,version=0)).andExpect(status().isBadRequest)
        val f=food(id)
        putJson("/api/v1/meal-records/$record",id,request.copy(items=mealRequest(f).items,version=0)).andExpect(status().isBadRequest)
        mvc.perform(delete("/api/v1/meal-records/$record").param("version","0").with(auth(id))).andExpect(status().isNoContent)
        assertTrue(meals.day(id,today).items.isEmpty())
    }

    @Test fun `daily targets select the effective profile for that date and keep absent targets absent`() {
        val id=user(false)
        profiles.save(id,profile(today.minusDays(2),calories=2200))
        profiles.save(id,profile(today,version=0,calories=2500))
        assertEquals(BigDecimal("2200"),meals.day(id,today.minusDays(1)).target!!.kcal)
        assertEquals(BigDecimal("2500"),meals.day(id,today).target!!.kcal)
        assertNull(meals.day(id,today.minusDays(3)).target)
        profiles.save(id,profile(today,version=1).copy(nutritionMode=NutritionMode.NONE,dailyCalories=null,carbohydrateG=null,proteinG=null,fatG=null))
        assertNull(meals.day(id,today).target)
        assertEquals(BigDecimal("2200"),meals.day(id,today.minusDays(1)).target!!.kcal)
    }

    @Test fun `invalid quantity precision names slots dates and item lists are rejected without echoes`() {
        val id=user(); val marker="private-marker"
        listOf(
            foodRequest().copy(name="$marker\u0000"),foodRequest().copy(name="   "),
            foodRequest().copy(basisGrams=BigDecimal.ZERO),foodRequest().copy(basisGrams=BigDecimal("80.001")),
            foodRequest().copy(nutrition=NutritionValues(kcal=BigDecimal("-1"))),foodRequest().copy(nutrition=NutritionValues(kcal=BigDecimal("100000.01"))),
        ).forEach { putJson("/api/v1/foods/${UUID.randomUUID()}",id,it).andExpect(status().isBadRequest).andExpect(content().string(not(containsString(marker)))) }
        putJson("/api/v1/foods/not-a-uuid",id,foodRequest()).andExpect(status().isBadRequest).andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        putJson("/api/v1/meal-plan",id,MealPlanWrite(emptyList())).andExpect(status().isBadRequest)
        putJson("/api/v1/meal-plan",id,MealPlanWrite(listOf(MealSlot(UUID.randomUUID().toString()," 점심 "),MealSlot(UUID.randomUUID().toString(),"점심")))).andExpect(status().isBadRequest)
        val f=food(id); val request=mealRequest(f)
        listOf(request.copy(date=today.plusDays(1)),request.copy(date=LocalDate.of(1899,12,31)),request.copy(items=request.items+request.items),request.copy(items=request.items.map { it.copy(grams=BigDecimal.ZERO) }))
            .forEach { putJson("/api/v1/meal-records/${UUID.randomUUID()}",id,it).andExpect(status().isBadRequest) }
        val recordId=UUID.randomUUID(); meals.putMeal(id,recordId,request)
        putJson("/api/v1/meal-records/$recordId",id,request.copy(date=today.minusDays(1),version=0)).andExpect(status().isBadRequest)
        putJson("/api/v1/meal-records/$recordId",id,request.copy(slotId=UUID.randomUUID().toString(),version=0)).andExpect(status().isBadRequest)
        putJson("/api/v1/meal-templates/${UUID.randomUUID()}",id,MealTemplateWrite("중복",listOf(TemplateItem(f.id,BigDecimal.ONE),TemplateItem(f.id,BigDecimal.ONE)))).andExpect(status().isBadRequest)
        assertEquals(1,meals.day(id,today).items.size)
    }

    @Test fun `account deletion cascades foods templates plans and immutable meal snapshots`() {
        val id=user(); val f=food(id); val t=meals.putTemplate(id,UUID.randomUUID(),MealTemplateWrite("기본",listOf(TemplateItem(f.id,BigDecimal.ONE))))
        meals.putPlan(id,MealPlanWrite(listOf(MealSlot(UUID.randomUUID().toString(),"식사",t.id))))
        meals.putMeal(id,UUID.randomUUID(),mealRequest(f))
        // Account deletion is a new HTTP transaction; do not retain the earlier profile entity in this test's persistence context.
        entities.flush(); entities.clear(); accounts.deleteById(id); entities.flush()
        listOf("foods","meal_templates","meal_template_items","meal_plans","meal_plan_slots","meal_records","meal_record_items").forEach { table ->
            assertEquals(0L,jdbc.queryForObject("SELECT COUNT(*) FROM $table WHERE user_id=?",Long::class.java,id),table)
        }
    }

    @Test
    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    fun `simultaneous first records for one date and slot create one record and return one conflict`() {
        val id=user(); val f=food(id); val request=mealRequest(f)
        val ready=CountDownLatch(2); val start=CountDownLatch(1); val pool=Executors.newFixedThreadPool(2)
        try {
            val results=(1..2).map { pool.submit<Int> { ready.countDown(); check(start.await(10,TimeUnit.SECONDS)); putJson("/api/v1/meal-records/${UUID.randomUUID()}",id,request).andReturn().response.status } }
            check(ready.await(10,TimeUnit.SECONDS)); start.countDown()
            assertEquals(listOf(200,409),results.map { it.get(20,TimeUnit.SECONDS) }.sorted())
            assertEquals(1,meals.day(id,today).items.size)
        } finally { start.countDown(); pool.shutdownNow(); pool.awaitTermination(5,TimeUnit.SECONDS); accounts.deleteById(id) }
    }
}
