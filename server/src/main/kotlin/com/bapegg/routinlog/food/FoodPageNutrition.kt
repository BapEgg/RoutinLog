package com.bapegg.routinlog.food

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.time.Instant

/** Reads explicit structured metadata or a nutrition table, never marketing prose or image guesses. */
@Component
class FoodPageNutrition(private val json: ObjectMapper) {
    private data class Fields(val name: String, val brand: String?, val basis: String, val values: Map<String, String>, val excerpt: String)
    private val propertyLabels = linkedMapOf("calories" to "열량", "carbohydrateContent" to "탄수화물", "proteinContent" to "단백질", "fatContent" to "지방", "fiberContent" to "식이섬유")
    private val aliases = mapOf("열량" to "calories", "칼로리" to "calories", "에너지" to "calories", "calories" to "calories",
        "탄수화물" to "carbohydrateContent", "단백질" to "proteinContent", "지방" to "fatContent", "총지방" to "fatContent", "식이섬유" to "fiberContent",
        "carbohydrate" to "carbohydrateContent", "protein" to "proteinContent", "totalfat" to "fatContent", "dietaryfiber" to "fiberContent")
    private val number = "(?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?:\\.[0-9]+)?"
    private fun clean(value: String, max: Int = 200) = value.replace(Regex("[\\p{Cc}\\p{Cf}]"), " ").replace(Regex("\\s+"), " ").trim().take(max)
    private fun value(any: Any?): String = when (any) {
        is String -> clean(any)
        is Map<*, *> -> if (any["value"] is Number || any["value"] is String) clean("${any["value"]} ${any["unitText"] ?: ""}") else ""
        else -> ""
    }
    private fun amount(raw: String, energy: Boolean = false): BigDecimal? {
        val units = if (energy) "(?:kcal|calories|kilocalories|킬로칼로리)" else "(?:g|grams?|그램)"
        val match = Regex("^($number)\\s*$units(?:\\s+[0-9.]+\\s*%)?$", RegexOption.IGNORE_CASE).matchEntire(raw.trim()) ?: return null
        return match.groupValues[1].replace(",", "").toBigDecimalOrNull()?.stripTrailingZeros()
            ?.takeIf { it.signum() >= 0 && it <= BigDecimal("100000") && it.scale() <= 2 }
    }

    fun extract(page: FetchedFoodPage, checkedAt: Instant): FoodUrlDraft {
        if (page.html.length > SafeFoodPageFetcher.MAX_BYTES) urlFail(FoodUrlFailure.TOO_LARGE)
        val document = Jsoup.parse(page.html)
        val pageName = clean(document.selectFirst("meta[property=og:title]")?.attr("content") ?: document.title(), 80)
        val candidates = mutableListOf<Fields>()
        document.select("script[type=application/ld+json]").take(20).forEach { script ->
            val content = script.data()
            if (content.length > 100_000) return@forEach
            val root = runCatching { json.readValue(content, Any::class.java) }.getOrNull() ?: return@forEach
            val queue = ArrayDeque<Pair<Any?, Int>>(); queue.add(root to 0)
            var visited = 0
            while (queue.isNotEmpty() && ++visited <= 3000) {
                val (node, depth) = queue.removeFirst()
                if (depth > 20) continue
                when (node) {
                    is Map<*, *> -> {
                        val type = (node["@type"] as? String)?.substringAfterLast('/')
                        val nutrition = node["nutrition"] as? Map<*, *>
                        if (type in setOf("Product", "Recipe", "MenuItem") && nutrition != null) {
                            val name = clean(node["name"] as? String ?: pageName, 80)
                            val brand = clean((node["brand"] as? String) ?: ((node["brand"] as? Map<*, *>)?.get("name") as? String).orEmpty(), 80).ifBlank { null }
                            val basis = value(nutrition["servingSize"])
                            val values = propertyLabels.keys.associateWith { value(nutrition[it]) }
                            val excerpt = listOf("기준량: $basis") + propertyLabels.map { (key, label) -> "$label: ${values[key].orEmpty().ifBlank { "표기 없음" }}" }
                            candidates += Fields(name, brand, basis, values, excerpt.joinToString("\n"))
                        }
                        node.values.forEach { queue.add(it to depth + 1) }
                    }
                    is List<*> -> node.forEach { queue.add(it to depth + 1) }
                }
            }
        }
        // HTML tables are also examined, so conflicting visible and structured values are not silently preferred.
        document.select("script,style,noscript,template,[hidden],[aria-hidden=true]").remove()
        document.select("table").take(40).forEach { table ->
            val rows = table.select("tr").filter { it.closest("table") == table }
            val values = mutableMapOf<String, String>()
            rows.take(80).forEach { row ->
                val cells = row.children().filter { it.tagName() in setOf("td", "th") }
                val key = cells.firstOrNull()?.text()?.lowercase()?.replace(Regex("\\s+"), "")?.let { aliases[it] }
                if (key != null && cells.size >= 2) {
                    val raw = clean(cells.drop(1).joinToString(" ") { it.text() })
                    if (key in values && values[key] != raw) urlFail(FoodUrlFailure.AMBIGUOUS)
                    if (Regex("$number\\s*(?:g|kcal|grams?)", RegexOption.IGNORE_CASE).findAll(raw).count() > 1) urlFail(FoodUrlFailure.AMBIGUOUS)
                    values[key] = raw
                }
            }
            if (values.isNotEmpty()) {
                val header = clean(table.select("caption").text() + " " + rows.take(2).joinToString(" ") { it.text() }, 500)
                val bases = Regex("(?<![0-9.,-])($number)\\s*(g|ml)\\s*(?:당|기준)", RegexOption.IGNORE_CASE).findAll(header)
                    .map { "${it.groupValues[1]} ${it.groupValues[2]}" }.distinct().toList()
                if (bases.size > 1) urlFail(FoodUrlFailure.AMBIGUOUS)
                candidates += Fields(pageName, null, bases.singleOrNull().orEmpty(), values,
                    (listOf("기준량: ${bases.singleOrNull().orEmpty()}") + propertyLabels.map { (key, label) -> "$label: ${values[key].orEmpty().ifBlank { "표기 없음" }}" }).joinToString("\n"))
            }
        }
        if (candidates.isEmpty()) urlFail(FoodUrlFailure.NO_NUTRITION)
        val parsed = candidates.map { fields ->
            if (Regex("\\b(?:ml|liters?|cups?|oz)\\b|밀리리터|리터", RegexOption.IGNORE_CASE).containsMatchIn(fields.basis)) urlFail(FoodUrlFailure.UNSUPPORTED_BASIS)
            val quantities = Regex("(?<![0-9.,-])($number)\\s*(?:g|grams?|그램)(?![a-z])", RegexOption.IGNORE_CASE).findAll(fields.basis).toList()
            if (quantities.size > 1) urlFail(FoodUrlFailure.AMBIGUOUS)
            val basis = quantities.singleOrNull()?.groupValues?.get(1)?.let { amount("$it g") }?.takeIf { it.signum() > 0 }
            val n = NutritionValues(amount(fields.values["calories"].orEmpty(), true), amount(fields.values["carbohydrateContent"].orEmpty()),
                amount(fields.values["proteinContent"].orEmpty()), amount(fields.values["fatContent"].orEmpty()), amount(fields.values["fiberContent"].orEmpty()))
            Triple(fields, basis, n)
        }
        val distinct = parsed.distinctBy { it.second to it.third }
        if (distinct.size != 1 || parsed.map { it.first.name }.filter { it.isNotBlank() }.distinct().size > 1) urlFail(FoodUrlFailure.AMBIGUOUS)
        val (fields, basis, nutrition) = distinct.single()
        if (listOf(nutrition.kcal, nutrition.carbsG, nutrition.proteinG, nutrition.fatG, nutrition.fiberG).all { it == null }) urlFail(FoodUrlFailure.NO_NUTRITION)
        val warnings = mutableListOf("판매처 페이지에서 읽은 값이에요. 실제 제품의 기준량·영양성분표와 비교해주세요.")
        if (basis == null) warnings += "몇 g 기준인지 확인되지 않았어요. 총 포장 무게 대신 영양표 기준량을 직접 입력해주세요."
        if (listOf(nutrition.kcal, nutrition.carbsG, nutrition.proteinG, nutrition.fatG, nutrition.fiberG).any { it == null }) warnings += "원문에 없거나 정확히 읽지 못한 성분은 빈칸으로 남겨요."
        return FoodUrlDraft(fields.name, fields.brand, basis, nutrition, page.url, PublicFoodAddress.parse(page.url).host,
            checkedAt.toString(), fields.excerpt.take(2000), warnings)
    }
}
