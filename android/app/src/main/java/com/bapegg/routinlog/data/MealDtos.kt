package com.bapegg.routinlog.data

import androidx.annotation.Keep
import java.math.BigDecimal

/** Original label values. Null means unknown, including when calories happen to be known. */
@Keep data class NutritionValues(
    val kcal: BigDecimal? = null,
    val carbsG: BigDecimal? = null,
    val proteinG: BigDecimal? = null,
    val fatG: BigDecimal? = null,
    val fiberG: BigDecimal? = null,
) {
    override fun toString() = "NutritionValues(redacted)"
}

@Keep data class NutrientTotal(val knownAmount: BigDecimal, val knownItems: Int, val missingItems: Int)
@Keep data class NutritionTotals(
    val kcal: NutrientTotal,
    val carbsG: NutrientTotal,
    val proteinG: NutrientTotal,
    val fatG: NutrientTotal,
    val fiberG: NutrientTotal,
)

@Keep data class FoodWrite(
    val name: String,
    val brand: String? = null,
    val basisGrams: BigDecimal,
    val nutrition: NutritionValues,
    val preparation: String,
    val sourceNote: String? = null,
    val version: Long? = null,
) {
    override fun toString() = "FoodWrite(redacted)"
}
@Keep data class FoodDto(
    val id: String,
    val name: String,
    val brand: String? = null,
    val basisGrams: BigDecimal,
    val nutrition: NutritionValues,
    val preparation: String,
    val sourceNote: String? = null,
    val version: Long,
    val source: String = "USER_ENTERED",
) {
    override fun toString() = "FoodDto(redacted)"
}
@Keep data class FoodListDto(val items: List<FoodDto>)

@Keep data class TemplateItem(val foodId: String, val grams: BigDecimal)
@Keep data class MealTemplateWrite(val name: String, val items: List<TemplateItem>, val memo: String? = null, val version: Long? = null) {
    override fun toString() = "MealTemplateWrite(redacted)"
}
@Keep data class MealTemplateDto(val id: String, val name: String, val items: List<TemplateItem>, val memo: String? = null, val version: Long) {
    override fun toString() = "MealTemplateDto(redacted)"
}
@Keep data class MealTemplateListDto(val items: List<MealTemplateDto>)

@Keep data class MealSlot(val id: String, val label: String, val templateId: String? = null)
@Keep data class MealPlanWrite(val slots: List<MealSlot>, val version: Long? = null)
@Keep data class MealPlanDto(val slots: List<MealSlot>, val version: Long? = null)

@Keep data class MealItemWrite(val id: String, val foodId: String, val grams: BigDecimal?)
/** Server-owned food snapshot. Never send its nutrition back as a trusted write payload. */
@Keep data class LoggedMealItem(
    val id: String,
    val foodId: String,
    val name: String,
    val brand: String? = null,
    val basisGrams: BigDecimal,
    val nutrition: NutritionValues,
    val preparation: String,
    val sourceNote: String? = null,
    val grams: BigDecimal?,
    val source: String = "USER_ENTERED",
) {
    override fun toString() = "LoggedMealItem(redacted)"
}
@Keep data class MealWrite(
    val date: String,
    val slotId: String,
    val slotLabel: String,
    val status: String,
    val items: List<MealItemWrite>,
    val note: String? = null,
    val version: Long? = null,
) {
    override fun toString() = "MealWrite(redacted)"
}
@Keep data class MealDto(
    val id: String,
    val date: String,
    val slotId: String,
    val slotLabel: String,
    val status: String,
    val items: List<LoggedMealItem>,
    val note: String? = null,
    val version: Long,
    val totals: NutritionTotals,
) {
    override fun toString() = "MealDto(redacted)"
}
@Keep data class PlannedMeal(val slotId:String,val slotLabel:String,val name:String,val items:List<LoggedMealItem>,val version:Long=0)
@Keep data class MealDayDto(val date: String, val items: List<MealDto>, val totals: NutritionTotals, val target: NutritionValues? = null,val plannedMeals:List<PlannedMeal> = emptyList()) {
    override fun toString() = "MealDayDto(redacted)"
}
