package com.bapegg.routinlog.food

import java.math.BigDecimal
import java.time.LocalDate

enum class FoodPreparation { RAW, COOKED, AS_SOLD, UNKNOWN }
enum class MealStatus { EATEN, SKIPPED }

data class NutritionValues(
    val kcal: BigDecimal? = null, val carbsG: BigDecimal? = null, val proteinG: BigDecimal? = null,
    val fatG: BigDecimal? = null, val fiberG: BigDecimal? = null,
)
data class NutrientTotal(val knownAmount: BigDecimal, val knownItems: Int, val missingItems: Int)
data class NutritionTotals(val kcal: NutrientTotal, val carbsG: NutrientTotal, val proteinG: NutrientTotal, val fatG: NutrientTotal, val fiberG: NutrientTotal)
data class FoodWrite(
    val name: String, val brand: String? = null, val basisGrams: BigDecimal, val nutrition: NutritionValues,
    val preparation: FoodPreparation, val sourceNote: String? = null, val version: Long? = null,
)
data class FoodDto(
    val id: String, val name: String, val brand: String?, val basisGrams: BigDecimal, val nutrition: NutritionValues,
    val preparation: FoodPreparation, val sourceNote: String?, val version: Long, val source: String = "USER_ENTERED",
)
data class FoodListDto(val items: List<FoodDto>)
data class TemplateItem(val foodId: String, val grams: BigDecimal)
data class MealTemplateWrite(val name: String, val items: List<TemplateItem>, val memo: String? = null, val version: Long? = null)
data class MealTemplateDto(val id: String, val name: String, val items: List<TemplateItem>, val memo: String?, val version: Long)
data class MealTemplateListDto(val items: List<MealTemplateDto>)
data class MealSlot(val id: String, val label: String, val templateId: String? = null)
data class MealPlanWrite(val slots: List<MealSlot>, val version: Long? = null)
data class MealPlanDto(val slots: List<MealSlot>, val version: Long? = null)
data class MealItemWrite(val id: String, val foodId: String, val grams: BigDecimal?)
data class LoggedMealItem(
    val id: String, val foodId: String, val name: String, val brand: String?, val basisGrams: BigDecimal,
    val nutrition: NutritionValues, val preparation: FoodPreparation, val sourceNote: String?, val grams: BigDecimal?,
    val source: String = "USER_ENTERED",
)
data class MealWrite(
    val date: LocalDate, val slotId: String, val slotLabel: String, val status: MealStatus,
    val items: List<MealItemWrite>, val note: String? = null, val version: Long? = null,
)
data class MealDto(
    val id: String, val date: LocalDate, val slotId: String, val slotLabel: String, val status: MealStatus,
    val items: List<LoggedMealItem>, val note: String?, val version: Long, val totals: NutritionTotals,
)
data class PlannedMeal(val slotId:String,val slotLabel:String,val name:String,val items:List<LoggedMealItem>,val version:Long=0)
data class MealDayDto(val date: LocalDate, val items: List<MealDto>, val totals: NutritionTotals, val target: NutritionValues?,val plannedMeals:List<PlannedMeal> = emptyList())
