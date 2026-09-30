package com.bapegg.routinlog

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.bapegg.routinlog.data.*
import com.bapegg.routinlog.domain.NutritionMath
import com.bapegg.routinlog.ui.MealViewModel
import com.bapegg.routinlog.ui.PreviewSession
import com.bapegg.routinlog.ui.screens.LiveFoodScreens
import com.bapegg.routinlog.ui.theme.RoutineLogTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/** Real meal screens; storage and all fabricated foods below exist only in this test source. */
class LiveFoodFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var model: MealViewModel
    private lateinit var ui: PreviewSession
    private lateinit var source: FakeMeals

    private fun start(fake: FakeMeals = FakeMeals()) {
        source = fake
        compose.runOnUiThread {
            val factory = viewModelFactory { initializer { MealViewModel(source) } }
            model = ViewModelProvider(compose.activity, factory)[MealViewModel::class.java]
            ui = ViewModelProvider(compose.activity)[PreviewSession::class.java]
            ui.accountMode = true
            ui.go("F01")
            model.bind("6ce6c7a1-6545-4ed3-8e6b-8042c612215d")
        }
        compose.setContent {
            RoutineLogTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        LiveFoodScreens(ui.route, ui, model)
                    }
                }
            }
        }
        compose.waitUntil(5_000) { model.state.value.loaded && !model.state.value.loading }
        compose.waitForIdle()
    }

    private fun click(text: String) = compose.onNodeWithText(text).performScrollTo().performClick()
    private fun enter(label: String, text: String) = compose.onNodeWithContentDescription(label).performScrollTo().performTextReplacement(text)

    @Test fun firstFoodToSavedTemplateToFlexiblePlanToActualMeal() {
        start()
        compose.onNodeWithText("자주 먹는 음식부터 하나씩").assertExists()
        click("첫 음식 등록")
        enter("음식 이름", "테스트 요거트")
        enter("브랜드 · 선택", "테스트 식품")
        enter("기준량", "80")
        enter("열량", "160")
        enter("지방", "0")
        click("내 음식에 저장")
        compose.runOnIdle {
            assertEquals("F01", ui.route)
            val food = source.foods.values.single()
            assertDecimal("80", food.basisGrams)
            assertDecimal("160", food.nutrition.kcal)
            assertDecimal("0", food.nutrition.fatG)
            assertNull(food.nutrition.fiberG)
        }
        click("기본 식단 설정")
        click("새 식단 만들기")
        enter("식단 이름", "아침 요거트")
        click("식단에 음식 추가")
        compose.onNodeWithText("테스트 요거트").performClick()
        enter("기본으로 먹을 양", "120")
        compose.onNodeWithText("240 kcal").assertExists()
        click("식단 저장")
        compose.onAllNodesWithText("기본 식단").onFirst().performScrollTo().performClick()
        // Match the sheet's selectable choice, not the same-named row behind it.
        compose.onNode(hasText("아침 요거트") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)).performClick()
        click("기본 식단 저장")
        click("이대로 먹었어요")
        compose.waitForIdle()
        compose.onNodeWithText("기록 완료").assertExists()
        compose.runOnIdle {
            val template = source.templates.values.single()
            assertDecimal("120", template.items.single().grams)
            assertEquals(template.id, source.plan.slots.first().templateId)
            val record = source.meals.values.single()
            assertEquals("EATEN", record.status)
            assertDecimal("120", record.items.single().grams)
            assertDecimal("240", record.totals.kcal.knownAmount)
            assertEquals(1, record.totals.fiberG.missingItems)
            assertEquals(0, record.totals.fiberG.knownItems)
            assertNull(model.state.value.draft)
        }
        capture("qa-live-food.png")
    }

    @Test fun unknownServingStaysUnknownInsteadOfBecomingZeroCalories() {
        start(FakeMeals.withTemplate())
        click("음식·양 바꿔 기록")
        click("먹은 양")
        click("먹은 양을 모르겠어요")
        click("이 양으로 적용")
        compose.onAllNodesWithText("정보 없음").onFirst().assertExists()
        click("이 식사 기록 저장")
        compose.waitForIdle()
        compose.onNodeWithText("0 kcal").assertDoesNotExist()
        compose.onNodeWithText("양을 모르는 음식이 있어요. 전체 섭취량은 아직 알 수 없어요.").assertExists()
        compose.runOnIdle {
            val sent = source.mealWrites.single().second
            assertNull(sent.items.single().grams)
            val record = source.meals.values.single()
            assertNull(record.items.single().grams)
            assertEquals(0, record.totals.kcal.knownItems)
            assertEquals(1, record.totals.kcal.missingItems)
            assertEquals(1, record.totals.fatG.missingItems)
            assertEquals("F01", ui.route)
        }
    }

    @Test fun historicalNutritionAndLatestVersionSurviveFailedSaveAndRetry() {
        val fake = FakeMeals.withTemplate()
        val food = fake.foods.values.single()
        val item = LoggedMealItem(newId(), food.id, food.name, food.brand, BigDecimal("80"),
            NutritionValues(kcal = BigDecimal("160"), fatG = BigDecimal.ZERO), "AS_SOLD", grams = BigDecimal("100"))
        val record = MealDto(newId(), fake.today, fake.plan.slots.first().id, "아침", "EATEN", listOf(item),
            version = 7, totals = totalOf(listOf(item)))
        fake.meals[record.id] = record
        // A later food edit must not rewrite the nutrition snapshot of the existing meal.
        fake.foods[food.id] = food.copy(nutrition = NutritionValues(kcal = BigDecimal("999")), version = 2)
        start(fake)
        click("구성·양 수정")
        compose.onNodeWithText("200 kcal").assertExists()
        click("먹은 양")
        enter("실제 먹은 양", "120")
        click("이 양으로 적용")
        compose.onNodeWithText("240 kcal").assertExists()
        compose.runOnIdle { source.saveFailure = AccountException(AccountErrorKind.NETWORK, SAVE_ERROR) }
        click("이 식사 기록 저장")
        compose.onNodeWithText(SAVE_ERROR).assertExists()
        compose.runOnIdle {
            assertEquals("F03", ui.route)
            assertEquals("120", model.state.value.draft?.items?.single()?.grams)
            assertEquals(7L, requireNotNull(model.state.value.draft?.version))
            assertDecimal("100", source.meals.getValue(record.id).items.single().grams)
            source.saveFailure = null
            source.saveGate = CompletableDeferred()
        }
        click("이 식사 기록 저장")
        compose.runOnIdle {
            assertTrue(model.state.value.busy)
            assertEquals("F03", ui.route)
            assertEquals(7L, source.meals.getValue(record.id).version)
            source.saveGate!!.complete(Unit)
        }
        compose.waitUntil(5_000) { !model.state.value.busy }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals("F01", ui.route)
            assertEquals(2, source.mealWrites.size)
            assertEquals(item.id, source.mealWrites.last().second.items.single().id)
            assertEquals(7L, requireNotNull(source.mealWrites.last().second.version))
            val saved = source.meals.getValue(record.id)
            assertEquals(8L, saved.version)
            assertDecimal("160", saved.items.single().nutrition.kcal)
            assertDecimal("240", saved.totals.kcal.knownAmount)
            assertDecimal("999", source.foods.getValue(food.id).nutrition.kcal)
        }
    }

    @Test fun fourthMealAndRemovedHistoricalSlotKeepDistinctRecordingStates() {
        val fake = FakeMeals.withTemplate()
        val food = fake.foods.values.single()
        val oldItem = LoggedMealItem(newId(), food.id, "지난 식사의 음식", null, food.basisGrams, food.nutrition, "AS_SOLD", grams = BigDecimal("80"))
        val old = MealDto(newId(), fake.today, newId(), "이전 야식", "EATEN", listOf(oldItem), version = 3, totals = totalOf(listOf(oldItem)))
        fake.meals[old.id] = old
        fake.plan = defaultPlan()
        start(fake)
        click("기본 식단 설정")
        click("끼니 추가")
        enter("4번째 끼니 이름", "운동 후")
        click("기본 식단 저장")
        compose.onNodeWithText("운동 후").assertExists()
        compose.onNodeWithText("이전 야식").assertExists()
        // Four current slots precede the preserved historical slot in this non-lazy list.
        compose.onAllNodesWithText("먹지 않았어요")[3].performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("먹지 않음").assertExists()
        compose.onAllNodesWithText("확인 전").assertCountEquals(3)
        compose.runOnIdle {
            assertEquals(4, source.plan.slots.size)
            val skipped = source.meals.values.single { it.slotLabel == "운동 후" }
            assertEquals("SKIPPED", skipped.status)
            assertTrue(skipped.items.isEmpty())
            assertEquals(old, source.meals[old.id])
            assertEquals(2, model.state.value.day?.items?.size)
            assertDecimal("160", model.state.value.day?.totals?.kcal?.knownAmount)
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        try { FileOutputStream(File(instrumentation.targetContext.cacheDir, name)).use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
        finally { bitmap.recycle() }
    }

    private class FakeMeals : MealDataSource {
        val today = LocalDate.now().toString()
        val foods = linkedMapOf<String, FoodDto>()
        val templates = linkedMapOf<String, MealTemplateDto>()
        val meals = linkedMapOf<String, MealDto>()
        var plan = defaultPlan()
        var saveFailure: AccountException? = null
        var saveGate: CompletableDeferred<Unit>? = null
        val mealWrites = mutableListOf<Pair<String, MealWrite>>()
        override suspend fun listFoods() = foods.values.toList()
        override suspend fun listMealTemplates() = templates.values.toList()
        override suspend fun getMealPlan() = plan
        override suspend fun getMealDay(date: String): MealDayDto {
            val rows = meals.values.filter { it.date == date }
            return MealDayDto(date, rows, totalOf(rows.filter { it.status == "EATEN" }.flatMap { it.items }),
                NutritionValues(kcal = BigDecimal("2400"), proteinG = BigDecimal("150")))
        }
        override suspend fun saveFood(id: String, food: FoodWrite): FoodDto {
            check(food.version == foods[id]?.version)
            return FoodDto(id, food.name, food.brand, food.basisGrams, food.nutrition, food.preparation, food.sourceNote,
                (foods[id]?.version ?: 0) + 1).also { foods[id] = it }
        }
        override suspend fun deleteFood(id: String, version: Long) {
            check(foods[id]?.version == version)
            check(templates.values.none { template -> template.items.any { it.foodId == id } })
            foods.remove(id)
        }
        override suspend fun saveMealTemplate(id: String, template: MealTemplateWrite): MealTemplateDto {
            check(template.version == templates[id]?.version)
            return MealTemplateDto(id, template.name, template.items, template.memo, (templates[id]?.version ?: 0) + 1).also { templates[id] = it }
        }
        override suspend fun deleteMealTemplate(id: String, version: Long) {
            check(templates[id]?.version == version)
            check(plan.slots.none { it.templateId == id })
            templates.remove(id)
        }
        override suspend fun saveMealPlan(plan: MealPlanWrite): MealPlanDto {
            check(plan.version == this.plan.version)
            return MealPlanDto(plan.slots, (this.plan.version ?: 0) + 1).also { this.plan = it }
        }
        override suspend fun saveMeal(id: String, meal: MealWrite): MealDto {
            mealWrites += id to meal
            saveGate?.await()
            saveFailure?.let { throw it }
            val existing = meals[id]
            check(meal.version == existing?.version)
            val items = meal.items.map { requested ->
                val original = existing?.items?.firstOrNull { it.id == requested.id && it.foodId == requested.foodId }
                if (original != null) original.copy(grams = requested.grams)
                else foods.getValue(requested.foodId).let { food ->
                    LoggedMealItem(requested.id, food.id, food.name, food.brand, food.basisGrams, food.nutrition, food.preparation, food.sourceNote, requested.grams)
                }
            }
            return MealDto(id, meal.date, meal.slotId, meal.slotLabel, meal.status, items, meal.note,
                (existing?.version ?: 0) + 1, totalOf(items)).also { meals[id] = it }
        }
        override suspend fun deleteMeal(id: String, version: Long) {
            check(meals[id]?.version == version)
            meals.remove(id)
        }
        companion object {
            fun withTemplate() = FakeMeals().apply {
                val food = FoodDto(newId(), "테스트 식품", "테스트 브랜드", BigDecimal("80"),
                    NutritionValues(kcal = BigDecimal("160"), fatG = BigDecimal.ZERO), "AS_SOLD", version = 1)
                foods[food.id] = food
                val template = MealTemplateDto(newId(), "평소 아침", listOf(TemplateItem(food.id, BigDecimal("120"))), version = 1)
                templates[template.id] = template
                plan = MealPlanDto(listOf(MealSlot(newId(), "아침", template.id)), version = 1)
            }
        }
    }

    private companion object {
        const val SAVE_ERROR = "저장하지 못했어요. 연결을 확인하고 다시 시도해주세요."
        fun newId() = UUID.randomUUID().toString()
        fun defaultPlan() = MealPlanDto(listOf("아침", "점심", "저녁").map { MealSlot(newId(), it) })
        fun totalOf(items: List<LoggedMealItem>) = NutritionMath.totals(NutritionMath.loggedItems(items))
        fun assertDecimal(expected: String, actual: BigDecimal?) {
            assertNotNull(actual)
            assertEquals("Decimal value differs", 0, BigDecimal(expected).compareTo(requireNotNull(actual)))
        }
    }
}
