package com.bapegg.routinlog.nutrition

import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

class NutrientScalerTest {
    private fun decimal(value: String) = BigDecimal(value)

    @Test
    fun `80 gram label with 160 kcal scales to 240 kcal for 120 grams`() {
        val original = NutritionBasis(
            grams = decimal("80"),
            nutrients = Nutrients(
                energyKcal = decimal("160"),
                carbohydrateGrams = decimal("24"),
                proteinGrams = decimal("12"),
                fatGrams = decimal("4"),
                sodiumMilligrams = decimal("8"),
            ),
        )

        val result = NutrientScaler.scale(original, decimal("120"))

        assertEquals(decimal("240.000000"), result.nutrients.energyKcal)
        assertEquals(decimal("36.000000"), result.nutrients.carbohydrateGrams)
        assertEquals(decimal("18.000000"), result.nutrients.proteinGrams)
        assertEquals(decimal("6.000000"), result.nutrients.fatGrams)
        assertEquals(decimal("12.000000"), result.nutrients.sodiumMilligrams)
        assertNull(result.nutrients.fiberGrams)
        assertSame(original, result.original)
        assertEquals(decimal("80"), result.original.grams)
        assertEquals(decimal("160"), result.original.nutrients.energyKcal)
    }

    @Test
    fun `unknown nutrients stay unknown while declared zero remains zero`() {
        val source = NutritionBasis(decimal("100"), Nutrients(fatGrams = BigDecimal.ZERO))
        for (consumed in listOf("0", "50", "150")) {
            val result = NutrientScaler.scale(source, decimal(consumed)).nutrients
            assertEquals(decimal("0.000000"), result.fatGrams)
            assertNull(result.energyKcal)
            assertNull(result.proteinGrams)
            assertNull(result.carbohydrateGrams)
            assertNull(result.fiberGrams)
        }
    }

    @Test
    fun `rounding happens once at the final result and never on the ratio`() {
        val source = NutritionBasis(decimal("3"), Nutrients(energyKcal = decimal("2")))
        assertEquals(decimal("6.666667"), NutrientScaler.scale(source, decimal("10")).nutrients.energyKcal)
    }

    @Test
    fun `decimal label values and portions use decimal arithmetic`() {
        val source = NutritionBasis(decimal("80.5"), Nutrients(energyKcal = decimal("160.25")))
        val result = NutrientScaler.scale(source, decimal("120.75"))
        assertEquals(decimal("240.375000"), result.nutrients.energyKcal)
        assertEquals(decimal("80.5"), source.grams)
        assertEquals(decimal("160.25"), source.nutrients.energyKcal)
    }

    @Test
    fun `zero portion is allowed but zero or negative reference mass is rejected`() {
        val source = NutritionBasis(decimal("80"), Nutrients(energyKcal = decimal("160")))
        assertEquals(decimal("0.000000"), NutrientScaler.scale(source, BigDecimal.ZERO).nutrients.energyKcal)
        listOf("0", "-1").forEach {
            assertFailsWith<IllegalArgumentException> { NutritionBasis(decimal(it), Nutrients()) }
        }
    }

    @Test
    fun `negative intake and negative source nutrients are rejected`() {
        val source = NutritionBasis(decimal("100"), Nutrients())
        assertFailsWith<IllegalArgumentException> { NutrientScaler.scale(source, decimal("-0.1")) }
        assertFailsWith<IllegalArgumentException> { Nutrients(proteinGrams = decimal("-1")) }
        assertFailsWith<IllegalArgumentException> { Nutrients(sodiumMilligrams = decimal("-1")) }
    }

    @Test
    fun `the accepted upper bound can be scaled to an unchanged portion`() {
        val maximum = decimal("1000000000000000000")
        val source = NutritionBasis(decimal("100"), Nutrients(energyKcal = maximum))
        assertEquals(maximum.setScale(6), NutrientScaler.scale(source, decimal("100")).nutrients.energyKcal)
    }

    @Test
    fun `unbounded precision and magnitude are rejected`() {
        val source = NutritionBasis(decimal("100"), Nutrients())
        listOf("0.0000001", "1E+20", "1000000000000000001").forEach {
            assertFailsWith<IllegalArgumentException> { NutrientScaler.scale(source, decimal(it)) }
        }
        assertFailsWith<IllegalArgumentException> {
            Nutrients(energyKcal = decimal("9999999999999999999"))
        }
    }
}
