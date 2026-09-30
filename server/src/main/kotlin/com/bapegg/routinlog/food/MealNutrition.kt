package com.bapegg.routinlog.food

import java.math.BigDecimal
import java.math.RoundingMode

/** Arithmetic only: original nutrition values are never inferred from macros or similar foods. */
object MealNutrition {
    fun atGrams(item: LoggedMealItem): NutritionValues {
        val grams = item.grams ?: return NutritionValues()
        fun scale(value: BigDecimal?): BigDecimal? = value?.multiply(grams)?.divide(item.basisGrams, 6, RoundingMode.HALF_UP)
        return with(item.nutrition) { NutritionValues(scale(kcal), scale(carbsG), scale(proteinG), scale(fatG), scale(fiberG)) }
    }

    fun totals(items: List<LoggedMealItem>): NutritionTotals {
        val scaled = items.map(::atGrams)
        fun total(select: (NutritionValues) -> BigDecimal?): NutrientTotal {
            val known = scaled.mapNotNull(select)
            return NutrientTotal(known.fold(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP), known.size, scaled.size - known.size)
        }
        return NutritionTotals(total { it.kcal }, total { it.carbsG }, total { it.proteinG }, total { it.fatG }, total { it.fiberG })
    }
}
