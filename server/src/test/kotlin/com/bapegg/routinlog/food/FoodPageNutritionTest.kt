package com.bapegg.routinlog.food

import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import kotlin.test.*

class FoodPageNutritionTest {
    private val extractor = FoodPageNutrition(jacksonObjectMapper())
    private val now = Instant.parse("2026-10-01T00:00:00Z")
    private fun parse(html: String) = extractor.extract(FetchedFoodPage("https://example.com/product", html), now)
    private fun structured(nutrition: String, name: String = "테스트 제품") = """<script type="application/ld+json">{"@type":"Product","name":"$name","brand":{"name":"테스트 브랜드"},"nutrition":{$nutrition}}</script>"""
    @Test fun `JSON-LD keeps original serving quantity source null nutrients and true zero`() {
        val result = parse(structured(""""servingSize":"1 serving (80 g)","calories":"160 kcal","proteinContent":"20 g","fatContent":"0 g""""))
        assertEquals("테스트 제품", result.name); assertEquals("테스트 브랜드", result.brand)
        assertEquals(0, BigDecimal("80").compareTo(result.basisGrams)); assertEquals(0, BigDecimal("160").compareTo(result.nutrition.kcal))
        assertEquals(BigDecimal.ZERO, result.nutrition.fatG); assertNull(result.nutrition.fiberG)
        assertEquals(now.toString(), result.checkedAt); assertTrue(result.excerpt.contains("80 g"))
    }
    @Test fun `unitless percent ranges less-than values and saturated fat cannot be inferred`() {
        val result = parse(structured(""""servingSize":"80 g","calories":"160 kcal","proteinContent":"36%","fatContent":"less than 0.5 g","saturatedFatContent":"3 g","carbohydrateContent":20"""))
        assertNull(result.nutrition.proteinG); assertNull(result.nutrition.fatG); assertNull(result.nutrition.carbsG)
    }
    @Test fun `unknown serving is not confused with total package weight`() {
        val result = parse(structured(""""servingSize":"1 serving","calories":"160 calories"""") + "<p>총 내용량 300g</p>")
        assertNull(result.basisGrams); assertTrue(result.warnings.any { it.contains("기준량") })
    }
    @Test fun `mL and multiple serving quantities cannot be used as grams`() {
        assertEquals(FoodUrlFailure.UNSUPPORTED_BASIS, assertFailsWith<FoodUrlProblem> { parse(structured(""""servingSize":"100 ml","calories":"60 kcal"""")) }.reason)
        assertEquals(FoodUrlFailure.AMBIGUOUS, assertFailsWith<FoodUrlProblem> { parse(structured(""""servingSize":"100 g / 80 g","calories":"60 kcal"""")) }.reason)
    }
    @Test fun `visible nutrition table is parsed without consuming recommended intake footnotes`() {
        val result = parse("""<title>테스트 밥</title><table><caption>100g당 영양정보</caption><tr><th>열량</th><td>160 kcal</td></tr><tr><th>단백질</th><td>20 g</td><td>36%</td></tr><tr><th>지방</th><td>0 g</td></tr></table><p>1일 영양성분 기준치 2,000kcal</p>""")
        assertEquals(0, BigDecimal("160").compareTo(result.nutrition.kcal)); assertEquals(0, BigDecimal("20").compareTo(result.nutrition.proteinG))
        assertEquals(BigDecimal.ZERO, result.nutrition.fatG); assertNull(result.nutrition.fiberG)
    }
    @Test fun `conflicting metadata multiple products and multi column tables require manual verification`() {
        assertFailsWith<FoodUrlProblem> { parse(structured(""""servingSize":"80 g","calories":"160 kcal"""", "제품1") + structured(""""servingSize":"80 g","calories":"160 kcal"""", "제품2")) }
        assertFailsWith<FoodUrlProblem> { parse("<table><caption>100g당</caption><tr><th>단백질</th><td>20g</td><td>25g</td></tr></table>") }
    }
    @Test fun `script prose and remote image links are not treated as verified nutrition`() {
        assertEquals(FoodUrlFailure.NO_NUTRITION, assertFailsWith<FoodUrlProblem> { parse("<script>단백질 20g</script><img src='https://127.0.0.1/private'><p>단백질 20g 함유!</p>") }.reason)
    }
    @Test fun `preview is rate limited and an unavailable page never produces a fabricated draft`() {
        var requests = 0
        val service = FoodUrlPreview(FoodPageFetcher { requests++; FetchedFoodPage(it, "<html>no nutrition</html>") }, extractor)
        val owner = UUID.randomUUID()
        repeat(5) { val result = service.preview(owner, "https://example.com/a"); assertNull(result.draft); assertEquals("NO_NUTRITION", result.reasonCode) }
        assertEquals("TOO_MANY_REQUESTS", service.preview(owner, "https://example.com/a").reasonCode); assertEquals(5, requests)
        assertEquals("UNSAFE_ADDRESS", service.preview(UUID.randomUUID(), "https://127.0.0.1/").reasonCode); assertEquals(5, requests)
    }
}
