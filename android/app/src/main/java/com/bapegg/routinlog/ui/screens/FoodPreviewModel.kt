package com.bapegg.routinlog.ui.screens

/** UI fixture values only. This model does not query a food database or infer missing nutrients. */
internal data class PreviewFood(
    val name: String, val base: Double, val kcal: Double?, val carbs: Double?,
    val protein: Double?, val fat: Double?, val fiber: Double?, val source: String,
) {
    fun atGrams(grams: Double): PreviewNutrients {
        require(base.isFinite() && base > 0 && grams.isFinite() && grams >= 0)
        val ratio = grams / base
        fun scale(value: Double?): Double? = value?.also { require(it.isFinite() && it >= 0) }?.times(ratio)
        return PreviewNutrients(scale(kcal), scale(carbs), scale(protein), scale(fat), scale(fiber))
    }
}

internal data class PreviewServing(val foodName: String, val grams: Double) {
    init { require(foodName.isNotBlank() && grams.isFinite() && grams > 0) }
}

internal data class PreviewNutrients(val kcal: Double?, val carbs: Double?, val protein: Double?, val fat: Double?, val fiber: Double?)

internal data class NutrientSum(val known: Double, val knownItems: Int, val missingItems: Int) {
    val amount: Double? get() = if (knownItems == 0 && missingItems > 0) null else known
    val partial: Boolean get() = knownItems > 0 && missingItems > 0
    val missing: Boolean get() = missingItems > 0
}

internal data class PreviewMealTotals(val kcal: NutrientSum, val carbs: NutrientSum, val protein: NutrientSum, val fat: NutrientSum, val fiber: NutrientSum) {
    val hasMissing: Boolean get() = listOf(kcal, carbs, protein, fat, fiber).any { it.missing }
}

internal object FoodPreviewMath {
    fun totals(values: List<PreviewNutrients>): PreviewMealTotals {
        fun sum(pick: (PreviewNutrients) -> Double?): NutrientSum {
            val selected = values.map(pick)
            return NutrientSum(selected.filterNotNull().sum(), selected.count { it != null }, selected.count { it == null })
        }
        return PreviewMealTotals(sum { it.kcal }, sum { it.carbs }, sum { it.protein }, sum { it.fat }, sum { it.fiber })
    }

    /** Editing an existing ingredient replaces its amount instead of appending it a second time. */
    fun upsert(servings: List<PreviewServing>, item: PreviewServing): List<PreviewServing> =
        if (servings.any { it.foodName == item.foodName }) servings.map { if (it.foodName == item.foodName) item else it }
        else servings + item

    fun portion(servings: List<PreviewServing>, finishedGrams: Double, eatenGrams: Double): List<PreviewServing> {
        require(finishedGrams.isFinite() && finishedGrams > 0 && eatenGrams.isFinite() && eatenGrams > 0 && eatenGrams <= finishedGrams)
        val ratio = eatenGrams / finishedGrams
        return servings.map { it.copy(grams = it.grams * ratio) }
    }
}
