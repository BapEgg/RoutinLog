package com.bapegg.routinlog.nutrition

import java.math.BigDecimal
import java.math.RoundingMode

/** Values in the original source's serving basis. Null means not provided, not zero. */
data class Nutrients(
    val energyKcal: BigDecimal? = null,
    val carbohydrateGrams: BigDecimal? = null,
    val proteinGrams: BigDecimal? = null,
    val fatGrams: BigDecimal? = null,
    val fiberGrams: BigDecimal? = null,
    val sodiumMilligrams: BigDecimal? = null,
) {
    init {
        listOf(energyKcal, carbohydrateGrams, proteinGrams, fatGrams, fiberGrams, sodiumMilligrams)
            .filterNotNull()
            .forEach { validateAmount(it, "nutrient amount") }
    }
}

data class NutritionBasis(val grams: BigDecimal, val nutrients: Nutrients) {
    init {
        validateAmount(grams, "reference grams")
        require(grams.signum() > 0) { "Reference grams must be greater than zero" }
    }
}

/** Retain the original basis alongside the calculation; never rewrite source label quantities. */
data class ScaledNutrition(
    val original: NutritionBasis,
    val consumedGrams: BigDecimal,
    val nutrients: Nutrients,
)

object NutrientScaler {
    const val OUTPUT_SCALE = 6

    /** No macro-to-energy derivation or missing nutrient inference is performed here. */
    fun scale(original: NutritionBasis, consumedGrams: BigDecimal): ScaledNutrition {
        validateAmount(consumedGrams, "consumed grams")

        fun scaled(value: BigDecimal?): BigDecimal? = value?.multiply(consumedGrams)
            ?.divide(original.grams, OUTPUT_SCALE, RoundingMode.HALF_UP)

        return ScaledNutrition(
            original = original,
            consumedGrams = consumedGrams,
            nutrients = with(original.nutrients) {
                Nutrients(
                    energyKcal = scaled(energyKcal),
                    carbohydrateGrams = scaled(carbohydrateGrams),
                    proteinGrams = scaled(proteinGrams),
                    fatGrams = scaled(fatGrams),
                    fiberGrams = scaled(fiberGrams),
                    sodiumMilligrams = scaled(sodiumMilligrams),
                )
            },
        )
    }
}

private fun validateAmount(value: BigDecimal, field: String) {
    require(value.signum() >= 0) { "$field must not be negative" }
    // Technical input bounds, not a dietary target or a clinical recommendation.
    require(value.precision() <= 25 && value.scale() in -6..6) {
        "$field must have at most 25 significant digits and at most 6 decimal places"
    }
    require(value <= BigDecimal("1000000000000000000")) { "$field exceeds the supported range" }
}
