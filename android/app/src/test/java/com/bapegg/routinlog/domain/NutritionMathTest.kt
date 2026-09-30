package com.bapegg.routinlog.domain

import com.bapegg.routinlog.data.LoggedMealItem
import com.bapegg.routinlog.data.NutritionValues
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class NutritionMathTest {
    @Test fun `source basis is scaled to supplied serving with exact decimal arithmetic`() {
        val item = NutritionMath.atGrams(NutritionValues(kcal = d("160"), proteinG = d("12.5")), d("80"), d("120"))
        assertEquals(d("240.000000"), item.kcal)
        assertEquals(d("18.750000"), item.proteinG)
        assertNull(item.fiberG)
    }

    @Test fun `sum uses six-place item values before two-place display rounding`() {
        val item = NutritionMath.atGrams(NutritionValues(kcal = d("1")), d("8"), d("1"))
        assertEquals(d("0.125000"), item.kcal)
        assertEquals(d("0.13"), NutritionMath.totals(listOf(item)).kcal.knownAmount)
        assertEquals(d("0.25"), NutritionMath.totals(listOf(item, item)).kcal.knownAmount)
        val third = NutritionMath.atGrams(NutritionValues(kcal = d("1")), d("3"), d("1"))
        assertEquals(d("1.00"), NutritionMath.totals(listOf(third, third, third)).kcal.knownAmount)
    }

    @Test fun `known zero and missing nutrient have different completeness`() {
        val knownZero = NutritionMath.atGrams(NutritionValues(fatG = BigDecimal.ZERO), d("100"), d("150"))
        val totals = NutritionMath.totals(listOf(knownZero, NutritionValues()))
        assertEquals(d("0.00"), totals.fatG.knownAmount)
        assertEquals(1, totals.fatG.knownItems)
        assertEquals(1, totals.fatG.missingItems)
        assertEquals(0, totals.fiberG.knownItems)
        assertEquals(2, totals.fiberG.missingItems)
    }

    @Test fun `unknown serving makes all nutrients unknown including labelled zero`() {
        val item = NutritionMath.atGrams(NutritionValues(d("100"), d("0"), d("5"), d("0"), d("0")), d("100"), null)
        assertEquals(NutritionValues(), item)
        val total = NutritionMath.totals(listOf(item))
        assertEquals(0, total.kcal.knownItems)
        assertEquals(1, total.kcal.missingItems)
        assertEquals(0, total.fatG.knownItems)
        assertEquals(1, total.fatG.missingItems)
    }

    @Test fun `empty day and unknown meal stay distinguishable`() {
        val empty = NutritionMath.totals(emptyList()).kcal
        val unknown = NutritionMath.totals(listOf(NutritionValues())).kcal
        assertEquals(d("0.00"), empty.knownAmount)
        assertEquals(0, empty.knownItems)
        assertEquals(0, empty.missingItems)
        assertEquals(1, unknown.missingItems)
    }

    @Test fun `each nutrient independently reports its partial known sum`() {
        val total = NutritionMath.totals(listOf(
            NutritionValues(kcal = d("10.1"), proteinG = d("1.2")),
            NutritionValues(kcal = d("20.2"), fatG = d("3.4")),
        ))
        assertEquals(d("30.30"), total.kcal.knownAmount)
        assertEquals(2, total.kcal.knownItems)
        assertEquals(0, total.kcal.missingItems)
        assertEquals(d("1.20"), total.proteinG.knownAmount)
        assertEquals(1, total.proteinG.missingItems)
        assertEquals(d("3.40"), total.fatG.knownAmount)
        assertEquals(1, total.fatG.missingItems)
    }

    @Test fun `logged draft calculation uses historical snapshot and preserves unknown serving`() {
        val snapshot = LoggedMealItem("item", "food", "테스트 음식", basisGrams = d("80"),
            nutrition = NutritionValues(kcal = d("160")), preparation = "AS_SOLD", grams = d("120"))
        val values = NutritionMath.loggedItems(listOf(snapshot, snapshot.copy(id = "item-2", grams = null)))
        assertEquals(d("240.000000"), values[0].kcal)
        assertEquals(NutritionValues(), values[1])
        assertEquals(1, NutritionMath.totals(values).kcal.missingItems)
    }

    @Test fun `invalid basis serving and negative nutrition are rejected`() {
        for (basis in listOf("0", "-1")) assertInvalid { NutritionMath.atGrams(NutritionValues(), d(basis), d("1")) }
        for (grams in listOf("0", "-1")) assertInvalid { NutritionMath.atGrams(NutritionValues(), d("100"), d(grams)) }
        assertInvalid { NutritionMath.atGrams(NutritionValues(kcal = d("-1")), d("100"), d("1")) }
    }

    private fun d(value: String) = BigDecimal(value)
    private fun assertInvalid(block: () -> Any?) {
        try { block(); fail("Expected invalid numeric input") } catch (_: IllegalArgumentException) { }
    }
}
