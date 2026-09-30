package com.bapegg.routinlog.ui

import androidx.lifecycle.viewModelScope
import com.bapegg.routinlog.data.*
import com.bapegg.routinlog.domain.NutritionMath
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class MealViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeMeals
    private lateinit var viewModel: MealViewModel
    private val today get() = LocalDate.now(ZoneOffset.UTC).toString()

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeMeals()
        viewModel = MealViewModel(repository)
    }

    @After fun tearDown() {
        viewModel.viewModelScope.cancel()
        Dispatchers.resetMain()
    }

    @Test fun `sign-out immediately removes account records library and unsent draft`() = runTest(dispatcher) {
        repository.records = listOf(meal())
        load()
        viewModel.beginMeal("slot-a", "첫 끼니")
        viewModel.setDraftNote("전송하지 않은 메모")
        assertNotNull(viewModel.state.value.draft)

        viewModel.bind(null)

        with(viewModel.state.value) {
            assertNull(userId)
            assertNull(day)
            assertNull(plan)
            assertNull(draft)
            assertTrue(foods.isEmpty())
            assertTrue(templates.isEmpty())
            assertFalse(loaded)
            assertFalse(busy)
        }
        advanceUntilIdle()
        assertNull(viewModel.state.value.day)
    }

    @Test fun `late previous-account read cannot populate the new account`() = runTest(dispatcher) {
        val oldRead = CompletableDeferred<MealDayDto>()
        repository.nextDayGate = oldRead
        viewModel.bind("account-a", "UTC")
        runCurrent()
        assertTrue(viewModel.state.value.loading)

        repository.foods = listOf(food().copy(id = "new-account-food", name = "다른 계정 음식"))
        viewModel.bind("account-b", "UTC")
        advanceUntilIdle()
        oldRead.complete(day(listOf(meal())))
        advanceUntilIdle()

        assertEquals("account-b", viewModel.state.value.userId)
        assertEquals("new-account-food", viewModel.state.value.foods.single().id)
        assertTrue(viewModel.state.value.day!!.items.isEmpty())
    }

    @Test fun `date switch hides the previous day before loading and after failure`() = runTest(dispatcher) {
        repository.records = listOf(meal())
        load()
        viewModel.beginMeal("slot-a", "첫 끼니")
        val pending = CompletableDeferred<MealDayDto>()
        repository.nextDayGate = pending

        viewModel.loadDate("2000-01-01")

        with(viewModel.state.value) {
            assertEquals("2000-01-01", date)
            assertNull(day)
            assertNull(draft)
            assertFalse(loaded)
            assertTrue(loading)
        }
        runCurrent()
        pending.completeExceptionally(AccountException(AccountErrorKind.NETWORK, "다시 연결해주세요."))
        advanceUntilIdle()
        assertNull(viewModel.state.value.day)
        assertFalse(viewModel.state.value.loaded)
        assertEquals("다시 연결해주세요.", viewModel.state.value.error)
    }

    @Test fun `failed save preserves draft and confirmed record then success clears draft`() = runTest(dispatcher) {
        val original = meal()
        repository.records = listOf(original)
        load()
        viewModel.beginMeal("slot-a", "첫 끼니")
        viewModel.setDraftGrams("item-a", "120.50")
        viewModel.setDraftNote("내가 입력한 수정")
        val draft = viewModel.state.value.draft
        repository.nextMealFailure = AccountException(AccountErrorKind.CONFLICT, "최신 기록을 확인해주세요.", "VERSION_CONFLICT", 409)
        var callbacks = 0

        viewModel.saveMeal { callbacks++ }
        advanceUntilIdle()

        assertEquals(draft, viewModel.state.value.draft)
        assertEquals(original, viewModel.state.value.day!!.items.single())
        assertEquals(0, callbacks)
        assertFalse(viewModel.state.value.busy)
        repository.mealResult = meal(grams = d("120.50"), version = 1).copy(note = "내가 입력한 수정")

        viewModel.saveMeal { callbacks++ }
        advanceUntilIdle()

        assertNull(viewModel.state.value.draft)
        assertEquals(1, callbacks)
        assertEquals(1L, viewModel.state.value.day!!.items.single().version)
        assertEquals(d("120.50"), repository.mealWrites.last().second.items.single().grams)
        assertEquals(0L, repository.mealWrites.last().second.version)
    }

    @Test fun `editing food library retains historical snapshot in open and reopened draft`() = runTest(dispatcher) {
        val recorded = meal()
        repository.records = listOf(recorded)
        load()
        viewModel.beginMeal("slot-a", "첫 끼니")
        repository.foodResult = food().copy(name = "변경된 음식", nutrition = NutritionValues(kcal = d("500")), version = 1)

        viewModel.saveFood("food-a", FoodWrite("변경된 음식", basisGrams = d("100"),
            nutrition = NutritionValues(kcal = d("500")), preparation = "AS_SOLD", version = 0)) { }
        advanceUntilIdle()

        assertEquals(d("500"), viewModel.state.value.foods.single().nutrition.kcal)
        assertEquals(recorded, viewModel.state.value.day!!.items.single())
        assertEquals(d("100"), viewModel.state.value.draft!!.items.single().snapshot.nutrition.kcal)
        assertEquals(d("100.00"), viewModel.draftTotals().kcal.knownAmount)
        viewModel.discardDraft()
        viewModel.beginMeal("slot-a", "첫 끼니")
        assertEquals(d("100"), viewModel.state.value.draft!!.items.single().snapshot.nutrition.kcal)
    }

    @Test fun `skipped meal remains explicit and contributes nothing to actual day totals`() = runTest(dispatcher) {
        repository.records = listOf(meal(), meal(id = "meal-b", slotId = "slot-b", grams = d("50")))
        load()
        assertEquals(d("150.00"), viewModel.state.value.day!!.totals.kcal.knownAmount)
        repository.mealResult = meal(status = "SKIPPED", version = 1)

        viewModel.skipMeal("slot-a", "첫 끼니")
        advanceUntilIdle()

        val written = repository.mealWrites.single().second
        assertEquals("SKIPPED", written.status)
        assertTrue(written.items.isEmpty())
        val current = viewModel.state.value.day!!
        assertEquals(2, current.items.size)
        assertEquals("SKIPPED", current.items.first { it.slotId == "slot-a" }.status)
        assertEquals(d("50.00"), current.totals.kcal.knownAmount)
        assertEquals(1, current.totals.kcal.knownItems)
        assertEquals(0, current.totals.kcal.missingItems)
    }

    @Test fun `blank serving saves null and reports unknown instead of zero intake`() = runTest(dispatcher) {
        repository.records = listOf(meal())
        load()
        viewModel.beginMeal("slot-a", "첫 끼니")
        viewModel.setDraftGrams("item-a", "")
        assertEquals(0, viewModel.draftTotals().kcal.knownItems)
        assertEquals(1, viewModel.draftTotals().kcal.missingItems)
        repository.mealResult = meal(grams = null, version = 1)

        viewModel.saveMeal { }
        advanceUntilIdle()

        assertNull(repository.mealWrites.single().second.items.single().grams)
        assertEquals(0, viewModel.state.value.day!!.totals.kcal.knownItems)
        assertEquals(1, viewModel.state.value.day!!.totals.kcal.missingItems)
        assertNull(viewModel.state.value.day!!.items.single().items.single().grams)
    }

    @Test fun `template update changes future draft without rewriting existing actual meal`() = runTest(dispatcher) {
        val original = meal()
        repository.records = listOf(original)
        load()
        repository.templateResult = template().copy(items = listOf(TemplateItem("food-a", d("250"))), version = 1)

        viewModel.saveTemplate("template-a", MealTemplateWrite("수정한 식사", listOf(TemplateItem("food-a", d("250"))), version = 0)) { }
        advanceUntilIdle()

        assertEquals(original, viewModel.state.value.day!!.items.single())
        viewModel.beginMeal("slot-a", "첫 끼니")
        assertEquals("100", viewModel.state.value.draft!!.items.single().grams)
        assertEquals(0L, viewModel.state.value.draft!!.version)
        viewModel.discardDraft()
        viewModel.beginMeal("slot-b", "다음 끼니")
        assertEquals("250", viewModel.state.value.draft!!.items.single().grams)
        assertNull(viewModel.state.value.draft!!.version)
        assertTrue(repository.mealWrites.isEmpty())
    }

    @Test fun `invalid nonblank serving remains editable and never reaches server`() = runTest(dispatcher) {
        repository.records = listOf(meal())
        load()
        viewModel.beginMeal("slot-a", "첫 끼니")
        for (value in listOf("0", "-1", "1.234", "100000.01", "not-a-number")) {
            viewModel.setDraftGrams("item-a", value)
            viewModel.saveMeal { fail("Invalid draft must not be saved") }
            advanceUntilIdle()
            assertEquals(value, viewModel.state.value.draft!!.items.single().grams)
            assertNotNull(viewModel.state.value.error)
        }
        assertTrue(repository.mealWrites.isEmpty())
    }

    @Test fun `missing assigned template blocks a new meal instead of making empty draft`() = runTest(dispatcher) {
        repository.templates = emptyList()
        load()
        viewModel.beginMeal("slot-a", "첫 끼니")
        assertNull(viewModel.state.value.draft)
        assertNotNull(viewModel.state.value.error)
        assertTrue(repository.mealWrites.isEmpty())
    }

    @Test fun `one missing template food blocks the entire draft instead of dropping that food`() = runTest(dispatcher) {
        repository.templates = listOf(template().copy(items = listOf(
            TemplateItem("food-a", d("100")), TemplateItem("missing-food", d("50")),
        )))
        load()
        viewModel.beginMeal("slot-a", "첫 끼니")
        assertNull(viewModel.state.value.draft)
        assertNotNull(viewModel.state.value.error)
        assertTrue(repository.mealWrites.isEmpty())
    }

    @Test fun `existing actual snapshot remains editable after library and template removal`() = runTest(dispatcher) {
        repository.records = listOf(meal())
        repository.foods = emptyList()
        repository.templates = emptyList()
        load()
        viewModel.beginMeal("slot-a", "첫 끼니")
        assertEquals("기록 당시 음식", viewModel.state.value.draft!!.items.single().snapshot.name)
        assertEquals(d("100.00"), viewModel.draftTotals().kcal.knownAmount)
        assertNull(viewModel.state.value.error)
    }

    @Test fun `profile revision reloads target while preserving unsent draft and skips duplicate bind`() = runTest(dispatcher) {
        repository.records = listOf(meal())
        repository.target = NutritionValues(kcal = d("1000"))
        viewModel.bind("account-a", "UTC", 0)
        advanceUntilIdle()
        viewModel.beginMeal("slot-a", "첫 끼니")
        viewModel.setDraftNote("목표 수정 중에도 보존할 메모")
        val draft = viewModel.state.value.draft
        val reads = repository.dayReads
        repository.target = NutritionValues(kcal = d("2000"))

        viewModel.bind("account-a", "UTC", 1)
        assertEquals(draft, viewModel.state.value.draft)
        advanceUntilIdle()

        assertEquals(d("2000"), viewModel.state.value.day!!.target!!.kcal)
        assertEquals(draft, viewModel.state.value.draft)
        assertEquals(reads + 1, repository.dayReads)
        viewModel.bind("account-a", "UTC", 1)
        advanceUntilIdle()
        assertEquals(reads + 1, repository.dayReads)
    }

    @Test fun `profile revision during save defers target refresh until write finishes`() = runTest(dispatcher) {
        repository.records = listOf(meal())
        repository.target = NutritionValues(kcal = d("1000"))
        viewModel.bind("account-a", "UTC", 0)
        advanceUntilIdle()
        viewModel.beginMeal("slot-a", "첫 끼니")
        viewModel.setDraftGrams("item-a", "120")
        val pending = CompletableDeferred<MealDto>()
        repository.nextMealGate = pending
        val reads = repository.dayReads
        viewModel.saveMeal { }
        runCurrent()
        assertTrue(viewModel.state.value.busy)
        repository.target = NutritionValues(kcal = d("2000"))

        viewModel.bind("account-a", "UTC", 1)
        runCurrent()
        assertEquals(reads, repository.dayReads)
        assertEquals(d("1000"), viewModel.state.value.day!!.target!!.kcal)
        val saved = meal(grams = d("120"), version = 1)
        repository.records = listOf(saved)
        pending.complete(saved)
        advanceUntilIdle()

        assertEquals(reads + 1, repository.dayReads)
        assertEquals(d("2000"), viewModel.state.value.day!!.target!!.kcal)
        assertEquals(1L, viewModel.state.value.day!!.items.single().version)
        assertNull(viewModel.state.value.draft)
        assertFalse(viewModel.state.value.busy)
    }

    @Test fun `skipping then deleting another meal preserves the current unsent draft`() = runTest(dispatcher) {
        repository.records = listOf(meal(), meal(id = "meal-b", slotId = "slot-b"))
        load()
        viewModel.beginMeal("slot-a", "첫 끼니")
        viewModel.setDraftGrams("item-a", "125")
        viewModel.setDraftNote("다른 끼니 작업과 별개인 수정")
        val draft = viewModel.state.value.draft
        val skipped = meal(id = "meal-b", slotId = "slot-b", status = "SKIPPED", version = 1)
        repository.mealResult = skipped
        viewModel.skipMeal("slot-b", "다음 끼니")
        advanceUntilIdle()
        assertEquals(draft, viewModel.state.value.draft)

        viewModel.deleteMeal(skipped)
        advanceUntilIdle()
        assertEquals(draft, viewModel.state.value.draft)
        assertEquals(listOf("meal-b" to 1L), repository.mealDeletes)
        assertEquals("meal-a", viewModel.state.value.day!!.items.single().id)
    }

    private fun TestScope.load() {
        viewModel.bind("account-a", "UTC")
        advanceUntilIdle()
        assertTrue(viewModel.state.value.loaded)
    }

    private fun d(value: String) = BigDecimal(value)
    private fun food() = FoodDto("food-a", "테스트 음식", basisGrams = d("100"), nutrition = NutritionValues(kcal = d("100")), preparation = "AS_SOLD", version = 0)
    private fun template() = MealTemplateDto("template-a", "테스트 식사", listOf(TemplateItem("food-a", d("100"))), version = 0)
    private fun meal(id: String = "meal-a", slotId: String = "slot-a", grams: BigDecimal? = d("100"), version: Long = 0, status: String = "EATEN"): MealDto {
        val items = if (status == "SKIPPED") emptyList() else listOf(LoggedMealItem("item-a", "food-a", "기록 당시 음식",
            basisGrams = d("100"), nutrition = NutritionValues(kcal = d("100")), preparation = "AS_SOLD", grams = grams))
        return MealDto(id, today, slotId, "기록 당시 끼니", status, items, version = version,
            totals = NutritionMath.totals(NutritionMath.loggedItems(items)))
    }
    private fun day(records: List<MealDto>, date: String = today) = MealDayDto(date, records,
        NutritionMath.totals(NutritionMath.loggedItems(records.filter { it.status == "EATEN" }.flatMap { it.items })))

    /** Fake responses are explicit test fixtures; this class does not impersonate production auth. */
    private inner class FakeMeals : MealDataSource {
        var foods = listOf(food())
        var templates = listOf(template())
        var plan = MealPlanDto(listOf(MealSlot("slot-a", "첫 끼니", "template-a"), MealSlot("slot-b", "다음 끼니", "template-a")), 0)
        var records = emptyList<MealDto>()
        var nextDayGate: CompletableDeferred<MealDayDto>? = null
        var nextMealGate: CompletableDeferred<MealDto>? = null
        var target: NutritionValues? = null
        var dayReads = 0
        var nextMealFailure: AccountException? = null
        var mealResult: MealDto? = null
        var foodResult: FoodDto? = null
        var templateResult: MealTemplateDto? = null
        val mealWrites = mutableListOf<Pair<String, MealWrite>>()
        val mealDeletes = mutableListOf<Pair<String, Long>>()

        override suspend fun listFoods() = foods
        override suspend fun listMealTemplates() = templates
        override suspend fun getMealPlan() = plan
        override suspend fun getMealDay(date: String): MealDayDto {
            dayReads++
            val gate = nextDayGate
            nextDayGate = null
            return gate?.await() ?: day(records.map { it.copy(date = date) }, date).copy(target = target)
        }
        override suspend fun saveMeal(id: String, meal: MealWrite): MealDto {
            mealWrites += id to meal
            nextMealFailure?.let { nextMealFailure = null; throw it }
            nextMealGate?.let { nextMealGate = null; return it.await() }
            return checkNotNull(mealResult) { "Test must supply a confirmed meal response" }
        }
        override suspend fun saveFood(id: String, food: FoodWrite) = checkNotNull(foodResult)
        override suspend fun saveMealTemplate(id: String, template: MealTemplateWrite) = checkNotNull(templateResult)
        override suspend fun saveMealPlan(plan: MealPlanWrite) = error("Unused operation")
        override suspend fun deleteMeal(id: String, version: Long) { mealDeletes += id to version }
        override suspend fun deleteFood(id: String, version: Long) = error("Unused operation")
        override suspend fun deleteMealTemplate(id: String, version: Long) = error("Unused operation")
    }
}
