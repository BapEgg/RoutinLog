package com.bapegg.routinlog.domain

import com.bapegg.routinlog.data.LoggedMealItem
import com.bapegg.routinlog.data.NutrientTotal
import com.bapegg.routinlog.data.NutritionTotals
import com.bapegg.routinlog.data.NutritionValues
import java.math.BigDecimal
import java.math.RoundingMode

/** Draft preview only; persisted totals remain authoritative server responses. No inferred nutrients. */
object NutritionMath {
    fun atGrams(nutrition: NutritionValues, basis: BigDecimal, grams: BigDecimal?): NutritionValues {
        require(basis.signum() > 0) { "Food basis must be positive" }
        if (grams == null) return NutritionValues()
        require(grams.signum() > 0) { "Serving must be positive or unknown" }
        fun amount(value: BigDecimal?): BigDecimal? = value?.let {
            require(it.signum() >= 0) { "Nutrition cannot be negative" }
            it.multiply(grams).divide(basis, 6, RoundingMode.HALF_UP)
        }
        return NutritionValues(amount(nutrition.kcal), amount(nutrition.carbsG), amount(nutrition.proteinG), amount(nutrition.fatG), amount(nutrition.fiberG))
    }

    fun totals(items: List<NutritionValues>): NutritionTotals {
        fun sum(field: (NutritionValues) -> BigDecimal?): NutrientTotal {
            val known = items.mapNotNull(field)
            return NutrientTotal(known.fold(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP), known.size, items.size - known.size)
        }
        return NutritionTotals(sum { it.kcal }, sum { it.carbsG }, sum { it.proteinG }, sum { it.fatG }, sum { it.fiberG })
    }

    /** Includes unknown quantities as missing values, using the historical snapshot for edited logs. */
    fun loggedItems(items: List<LoggedMealItem>): List<NutritionValues> =
        items.map { atGrams(it.nutrition, it.basisGrams, it.grams) }
}
