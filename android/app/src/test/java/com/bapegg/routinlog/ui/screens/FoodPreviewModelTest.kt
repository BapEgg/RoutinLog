package com.bapegg.routinlog.ui.screens

import org.junit.Assert.*
import org.junit.Test

class FoodPreviewModelTest {
    private val food = PreviewFood("sample", 80.0, 160.0, 4.0, 20.0, 7.0, null, "fixture")

    @Test fun `reference 80g 160kcal scales to 120g 240kcal without inventing fiber`() {
        val result = food.atGrams(120.0)
        assertEquals(240.0, result.kcal!!, 0.000001)
        assertEquals(30.0, result.protein!!, 0.000001)
        assertNull(result.fiber)
    }

    @Test fun `known zero remains zero but a missing nutrient remains unknown`() {
        val result = food.copy(fat = 0.0).atGrams(120.0)
        assertEquals(0.0, result.fat!!, 0.0)
        val sum = FoodPreviewMath.totals(listOf(result))
        assertNull(sum.fiber.amount)
        assertTrue(sum.fiber.missing)
        assertFalse(sum.fiber.partial)
    }

    @Test fun `mixed known and missing values have a partial total`() {
        val a = food.atGrams(80.0)
        val b = food.copy(fiber = 2.0).atGrams(80.0)
        val sum = FoodPreviewMath.totals(listOf(a, b))
        assertEquals(320.0, sum.kcal.amount!!, 0.0)
        assertFalse(sum.kcal.partial)
        assertEquals(2.0, sum.fiber.amount!!, 0.0)
        assertTrue(sum.fiber.partial)
    }

    @Test fun `editing one ingredient replaces it and adding another contributes once`() {
        val initial = listOf(PreviewServing("sample", 80.0))
        val edited = FoodPreviewMath.upsert(initial, PreviewServing("sample", 120.0))
        assertEquals(1, edited.size)
        assertEquals(80.0, initial.single().grams, 0.0)
        val added = FoodPreviewMath.upsert(edited, PreviewServing("second", 40.0))
        val sum = FoodPreviewMath.totals(added.map { food.atGrams(it.grams) })
        assertEquals(2, added.size)
        assertEquals(320.0, sum.kcal.amount!!, 0.000001)
    }

    @Test fun `recipe serving uses finished weight once and saved portions do not change source`() {
        val original = listOf(PreviewServing("sample", 200.0), PreviewServing("other", 100.0))
        val portion = FoodPreviewMath.portion(original, 600.0, 150.0)
        assertEquals(50.0, portion[0].grams, 0.0)
        assertEquals(25.0, portion[1].grams, 0.0)
        assertEquals(200.0, original[0].grams, 0.0)
        assertEquals(150.0, FoodPreviewMath.totals(portion.map { food.atGrams(it.grams) }).kcal.amount!!, 0.0)
    }

    @Test fun `empty recorded list has no consumed calories`() {
        val sum = FoodPreviewMath.totals(emptyList())
        assertEquals(0.0, sum.kcal.amount!!, 0.0)
        assertFalse(sum.hasMissing)
    }

    @Test fun `invalid reference and recipe portions are rejected`() {
        listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach { base ->
            assertTrue(runCatching { food.copy(base = base).atGrams(100.0) }.isFailure)
        }
        assertTrue(runCatching { FoodPreviewMath.portion(listOf(PreviewServing("sample", 100.0)), 100.0, 120.0) }.isFailure)
        assertTrue(runCatching { FoodPreviewMath.portion(listOf(PreviewServing("sample", 100.0)), 0.0, 120.0) }.isFailure)
    }
}
