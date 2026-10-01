package com.bapegg.routinlog.domain

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class NutritionLabelParserTest {
    private fun parse(text: String) = NutritionLabelParser.parse(text.trimIndent())
    @Test fun `single g basis preserves numbers and real zero without inventing fiber`() {
        val draft = parse("""
            영양정보
            총 내용량 300 g
            100 g당 160 kcal
            탄수화물 20 g 6%
            당류 3 g 3%
            지방 0 g 0%
            포화지방 0 g 0%
            트랜스지방 0 g
            단백질 20 g 36%
            1일 영양성분 기준치에 대한 비율은 2,000 kcal 기준입니다
        """)
        assertEquals(BigDecimal("1E+2"), draft.basisGrams)
        assertEquals(0, BigDecimal("160").compareTo(draft.nutrition.kcal))
        assertEquals(0, BigDecimal("20").compareTo(draft.nutrition.proteinG))
        assertEquals(BigDecimal.ZERO, draft.nutrition.fatG)
        assertNull(draft.nutrition.fiberG)
    }
    @Test fun `total package weight alone is not a nutrition basis`() {
        val draft = parse("총 내용량 300g\n열량 160kcal\n단백질 20g")
        assertNull(draft.basisGrams)
        assertEquals(0, BigDecimal("20").compareTo(draft.nutrition.proteinG))
    }
    @Test fun `explicit total-content basis can use the package weight`() {
        val draft = parse("총 내용량 80g\n총 내용량당 160 kcal\n지방 7.50g")
        assertEquals(0, BigDecimal("80").compareTo(draft.basisGrams))
        assertEquals(BigDecimal("7.5"), draft.nutrition.fatG)
    }
    @Test fun `saturated and trans fats never become total fat`() {
        val draft = parse("100g당\n포화지방 2g\n트랜스지방 0g\n불포화지방 5g")
        assertNull(draft.nutrition.fatG)
    }
    @Test fun `percent only and less-than amounts remain unknown`() {
        val draft = parse("100g당\n단백질 36%\n지방 0.5g 미만\n탄수화물 <1g\n식이섬유 미량\n열량 -100kcal")
        assertNull(draft.nutrition.proteinG); assertNull(draft.nutrition.fatG)
        assertNull(draft.nutrition.carbsG); assertNull(draft.nutrition.fiberG); assertNull(draft.nutrition.kcal)
    }
    @Test fun `volume basis never prefills nutrition for a gram form`() {
        val draft = parse("100ml당 60kcal\n단백질 3g")
        assertNull(draft.basisGrams); assertNull(draft.nutrition.kcal); assertNull(draft.nutrition.proteinG)
        assertTrue(draft.warnings.first().contains("mL"))
    }
    @Test fun `two nutrition columns require manual selection even when one heading is missed`() {
        listOf("100g당 160kcal\n80g당 128kcal\n단백질 20g 16g", "100g당\n단백질 20g 20g").forEach {
            val draft = parse(it)
            assertNull(draft.basisGrams); assertNull(draft.nutrition.proteinG)
        }
    }
    @Test fun `conflicting rows and unsupported precision are left empty`() {
        val draft = parse("100g당\n단백질 10g\n단백질 20g\n지방 0.123g\n식이섬유소 2.5g")
        assertNull(draft.nutrition.proteinG); assertNull(draft.nutrition.fatG)
        assertEquals(BigDecimal("2.5"), draft.nutrition.fiberG)
    }
    @Test fun `large daily reference calories are excluded and serving quantity is kept`() {
        val draft = parse("1회 제공량 (80g)\n열량 1,200 kcal\n1일 영양성분 기준치 2,000kcal")
        assertEquals(0, BigDecimal("80").compareTo(draft.basisGrams))
        assertEquals(0, BigDecimal("1200").compareTo(draft.nutrition.kcal))
    }
    @Test fun `visual rows restore column block reading order without merging adjacent rows`() {
        val text = labelReadingOrder(listOf(
            LabelTextLine("단백질", 0, 10, 90, 40), LabelTextLine("지방", 0, 60, 90, 90),
            LabelTextLine("20g", 150, 12, 190, 40), LabelTextLine("0g", 150, 60, 190, 90),
            LabelTextLine("36%", 230, 10, 290, 40)))
        assertEquals("단백질 20g 36%\n지방 0g", text)
        val draft = parse(text)
        assertEquals(0, BigDecimal("20").compareTo(draft.nutrition.proteinG))
        assertEquals(BigDecimal.ZERO, draft.nutrition.fatG)
    }
    @Test(expected = IllegalArgumentException::class) fun `oversized recognition is rejected`() { parse("x".repeat(16001)) }
    @Test fun `invalid negative or comma decimal basis never turns into its numeric suffix`() {
        assertNull(parse("-100g당\n단백질 20g").basisGrams)
        assertNull(parse("80,5g당\n단백질 20g").basisGrams)
    }
}
