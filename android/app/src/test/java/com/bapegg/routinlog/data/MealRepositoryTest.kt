package com.bapegg.routinlog.data

import com.bapegg.routinlog.auth.SessionStore
import com.bapegg.routinlog.auth.StoredSession
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/** Contract-only synthetic fixtures, not a food database or scientific nutrition reference. */
class MealRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: AccountRepository
    private lateinit var store: MemoryStore
    private val foodId = "10000000-0000-0000-0000-000000000001"
    private val templateId = "20000000-0000-0000-0000-000000000001"
    private val slotId = "30000000-0000-0000-0000-000000000001"
    private val itemId = "40000000-0000-0000-0000-000000000001"
    private val mealId = "50000000-0000-0000-0000-000000000001"

    @Before fun setUp() = runBlocking {
        server = MockWebServer().apply { start(InetAddress.getByName("127.0.0.1"), 0) }
        store = MemoryStore(StoredSession("fake-old-access", "fake-old-refresh", 9000, "fake-user"))
        val url = server.url("/").newBuilder().host("127.0.0.1").build().toString()
        repository = AccountRepository.forTesting(url, store) { 1000 }
        server.enqueue(json(tokens("1")))
        repository.restoreSession()
        take()
        Unit
    }

    @After fun tearDown() { server.shutdown() }

    @Test fun `food list keeps decimals null nutrition and declared source`() = runBlocking {
        server.enqueue(json("""{"items":[$foodJson]}"""))
        val food = repository.listFoods().single()
        val request = take()
        assertEquals("/api/v1/foods", request.path)
        assertEquals("Bearer fake-access-1", request.getHeader("Authorization"))
        assertEquals(BigDecimal("80.25"), food.basisGrams)
        assertEquals(BigDecimal("160.01"), food.nutrition.kcal)
        assertNull(food.nutrition.fiberG)
        assertEquals("USER_ENTERED", food.source)
    }

    @Test fun `manual food writes numbers and explicit unknown without claiming verified source`() = runBlocking {
        server.enqueue(json(foodJson))
        repository.saveFood(foodId, FoodWrite("직접 입력 테스트", basisGrams = BigDecimal("80.25"),
            nutrition = NutritionValues(kcal = BigDecimal("160.01"), fatG = BigDecimal.ZERO), preparation = "AS_SOLD"))
        val request = take()
        val body = body(request)
        assertEquals("PUT", request.method)
        assertEquals("/api/v1/foods/$foodId", request.path)
        assertTrue(body.get("basisGrams").asJsonPrimitive.isNumber)
        assertEquals("80.25", body.get("basisGrams").asString)
        assertTrue(body.get("version").isJsonNull)
        assertTrue(body.getAsJsonObject("nutrition").get("fiberG").isJsonNull)
        assertEquals(BigDecimal.ZERO, body.getAsJsonObject("nutrition").get("fatG").asBigDecimal)
        assertFalse(body.has("source"))
    }

    @Test fun `template writes carry food references quantity and optimistic version`() = runBlocking {
        val templateJson = """{"id":"$templateId","name":"저장 식사","items":[{"foodId":"$foodId","grams":120.50}],"memo":null,"version":3}"""
        server.enqueue(json(templateJson))
        assertEquals(3L, repository.saveMealTemplate(templateId, MealTemplateWrite("저장 식사", listOf(TemplateItem(foodId, BigDecimal("120.50"))), version = 2)).version)
        val write = take()
        assertEquals("/api/v1/meal-templates/$templateId", write.path)
        assertEquals(2L, body(write).get("version").asLong)
        server.enqueue(json("""{"items":[$templateJson]}"""))
        assertEquals(BigDecimal("120.50"), repository.listMealTemplates().single().items.single().grams)
        assertEquals("/api/v1/meal-templates", take().path)
        server.enqueue(MockResponse().setResponseCode(204))
        repository.deleteMealTemplate(templateId, 3)
        assertEquals("/api/v1/meal-templates/$templateId?version=3", take().path)
    }

    @Test fun `meal plan supports arbitrary slots and null initial version`() = runBlocking {
        val plan = """{"slots":[{"id":"$slotId","label":"운동 후","templateId":null}],"version":null}"""
        server.enqueue(json(plan))
        val loaded = repository.getMealPlan()
        assertEquals("운동 후", loaded.slots.single().label)
        assertNull(loaded.version)
        assertEquals("/api/v1/meal-plan", take().path)
        server.enqueue(json(plan.replace("\"version\":null", "\"version\":0")))
        repository.saveMealPlan(MealPlanWrite(loaded.slots))
        val request = take()
        assertEquals("PUT", request.method)
        assertEquals("/api/v1/meal-plan", request.path)
        assertTrue(body(request).get("version").isJsonNull)
    }

    @Test fun `day response retains authoritative partial totals historical snapshot and no target`() = runBlocking {
        server.enqueue(json("""{"date":"2026-09-30","items":[$mealJson],"totals":$totalsJson,"target":null}"""))
        val day = repository.getMealDay("2026-09-30")
        assertEquals("/api/v1/meal-records?date=2026-09-30", take().path)
        assertNull(day.target)
        assertEquals(1, day.totals.fiberG.missingItems)
        assertEquals(0, day.totals.fiberG.knownItems)
        assertEquals(BigDecimal("240.02"), day.totals.kcal.knownAmount)
        assertEquals(BigDecimal("80.25"), day.items.single().items.single().basisGrams)
        assertEquals("기록 당시 끼니", day.items.single().slotLabel)
    }

    @Test fun `meal write sends only references and unknown grams never raw food snapshot`() = runBlocking {
        server.enqueue(json(mealJson.replace("\"grams\":120.38", "\"grams\":null").replace(totalsJson, unknownTotalsJson)))
        val result = repository.saveMeal(mealId, MealWrite("2026-09-30", slotId, "기록 당시 끼니", "EATEN",
            listOf(MealItemWrite(itemId, foodId, null)), version = 0))
        val request = take()
        assertEquals("/api/v1/meal-records/$mealId", request.path)
        val item = body(request).getAsJsonArray("items").single().asJsonObject
        assertEquals(setOf("id", "foodId", "grams"), item.keySet())
        assertTrue(item.get("grams").isJsonNull)
        assertEquals(1L, result.version)
        assertNull(result.items.single().grams)
        assertEquals(0, result.totals.kcal.knownItems)
        assertEquals(1, result.totals.kcal.missingItems)
    }

    @Test fun `skipped meal is explicit empty items and deletion includes latest version`() = runBlocking {
        server.enqueue(json("""{"id":"$mealId","date":"2026-09-30","slotId":"$slotId","slotLabel":"운동 후","status":"SKIPPED","items":[],"note":null,"version":1,"totals":${unknownTotalsJson.replace("\"missingItems\":1", "\"missingItems\":0")}}"""))
        repository.saveMeal(mealId, MealWrite("2026-09-30", slotId, "운동 후", "SKIPPED", emptyList()))
        val written = body(take())
        assertEquals("SKIPPED", written.get("status").asString)
        assertTrue(written.getAsJsonArray("items").isEmpty)
        server.enqueue(MockResponse().setResponseCode(204))
        repository.deleteMeal(mealId, 1)
        val deleted = take()
        assertEquals("DELETE", deleted.method)
        assertEquals("/api/v1/meal-records/$mealId?version=1", deleted.path)
    }

    @Test fun `in-use food and missing consent errors preserve session and redact server text`() = runBlocking {
        server.enqueue(json("""{"code":"RESOURCE_IN_USE","message":"private food details"}""", 409))
        val conflict = failure { repository.deleteFood(foodId, 4) }
        assertEquals("RESOURCE_IN_USE", conflict.code)
        assertEquals(AccountErrorKind.CONFLICT, conflict.kind)
        assertFalse(conflict.userMessage.contains("private food details"))
        assertEquals("/api/v1/foods/$foodId?version=4", take().path)
        server.enqueue(json("""{"code":"PROFILE_REQUIRED"}""", 403))
        val consent = failure { repository.saveMealPlan(MealPlanWrite(listOf(MealSlot(slotId, "간식")))) }
        assertEquals("PROFILE_REQUIRED", consent.code)
        assertEquals(AccountErrorKind.VALIDATION, consent.kind)
        assertNotNull(repository.identity.value)
        assertNotNull(store.value)
    }

    @Test fun `meal endpoint reuses one refresh and retries with rotated bearer`() = runBlocking {
        server.enqueue(json("""{"code":"AUTHENTICATION_REQUIRED"}""", 401))
        server.enqueue(json(tokens("2")))
        server.enqueue(json("""{"items":[]}"""))
        assertTrue(repository.listFoods().isEmpty())
        assertEquals("Bearer fake-access-1", take().getHeader("Authorization"))
        assertEquals("/api/v1/auth/refresh", take().path)
        assertEquals("Bearer fake-access-2", take().getHeader("Authorization"))
        assertEquals("fake-refresh-2", store.value?.refreshToken)
    }

    private fun take(): RecordedRequest = checkNotNull(server.takeRequest(2, TimeUnit.SECONDS))
    private fun body(request: RecordedRequest) = JsonParser.parseString(request.body.readUtf8()).asJsonObject
    private fun json(value: String, code: Int = 200) = MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(value)
    private fun tokens(suffix: String) = """{"accessToken":"fake-access-$suffix","refreshToken":"fake-refresh-$suffix","expiresIn":3600,"userId":"fake-user"}"""
    private suspend fun failure(block: suspend () -> Any?): AccountException {
        try { block(); fail("Expected AccountException") } catch (error: AccountException) { return error }
        error("Unreachable")
    }
    private val foodJson get() = """{"id":"$foodId","name":"입력 예시","brand":null,"basisGrams":80.25,"nutrition":{"kcal":160.01,"carbsG":null,"proteinG":12.50,"fatG":0,"fiberG":null},"preparation":"AS_SOLD","sourceNote":null,"version":0,"source":"USER_ENTERED"}"""
    private val totalsJson = """{"kcal":{"knownAmount":240.02,"knownItems":1,"missingItems":0},"carbsG":{"knownAmount":0.00,"knownItems":0,"missingItems":1},"proteinG":{"knownAmount":18.75,"knownItems":1,"missingItems":0},"fatG":{"knownAmount":0.00,"knownItems":1,"missingItems":0},"fiberG":{"knownAmount":0.00,"knownItems":0,"missingItems":1}}"""
    private val unknownTotalsJson = """{"kcal":{"knownAmount":0.00,"knownItems":0,"missingItems":1},"carbsG":{"knownAmount":0.00,"knownItems":0,"missingItems":1},"proteinG":{"knownAmount":0.00,"knownItems":0,"missingItems":1},"fatG":{"knownAmount":0.00,"knownItems":0,"missingItems":1},"fiberG":{"knownAmount":0.00,"knownItems":0,"missingItems":1}}"""
    private val mealJson get() = """{"id":"$mealId","date":"2026-09-30","slotId":"$slotId","slotLabel":"기록 당시 끼니","status":"EATEN","items":[{"id":"$itemId","foodId":"$foodId","name":"기록 당시 이름","brand":null,"basisGrams":80.25,"nutrition":{"kcal":160.01,"carbsG":null,"proteinG":12.50,"fatG":0,"fiberG":null},"preparation":"AS_SOLD","sourceNote":null,"grams":120.38,"source":"USER_ENTERED"}],"note":null,"version":1,"totals":$totalsJson}"""
    private class MemoryStore(var value: StoredSession?) : SessionStore {
        override fun read() = value
        override fun write(session: StoredSession) { value = session }
        override fun clear() { value = null }
    }
}
