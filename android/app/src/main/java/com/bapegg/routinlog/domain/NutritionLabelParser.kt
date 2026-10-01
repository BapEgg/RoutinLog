package com.bapegg.routinlog.domain

import com.bapegg.routinlog.data.NutritionValues
import java.math.BigDecimal

/** Unverified transcription only. Never infers nutrients, serving conversions, or a product name. */
data class NutritionLabelDraft(
    val rawText: String,
    val basisGrams: BigDecimal?,
    val nutrition: NutritionValues,
    val warnings: List<String>,
)

object NutritionLabelParser {
    private const val number = "(?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?:\\.[0-9]+)?"
    private val labels = Regex("트랜스지방|포화지방|불포화지방|탄수화물|단백질|식이섬유소|식이섬유|지방|나트륨|당류|콜레스테롤|열량|칼로리")
    private fun amount(text: String): BigDecimal? = text.replace(",", "").toBigDecimalOrNull()
        ?.stripTrailingZeros()?.takeIf { it.signum() >= 0 && it <= BigDecimal("100000") && it.scale() <= 2 }

    fun parse(text: String): NutritionLabelDraft {
        require(text.length <= 16000) { "Label text too long" }
        val raw = text.trim()
        val lines = raw.lines().map { it.lowercase().replace(Regex("\\s+"), "") }
        val compact = lines.joinToString("\n")
        val warnings = mutableListOf<String>()
        val bases = Regex("(?<![0-9.,-])($number)(g|ml)(?:당|기준)").findAll(compact)
            .map { it.groupValues[1] to it.groupValues[2] }.toMutableList()
        Regex("1회제공량[:：]?[\\(]?($number)(g|ml)").findAll(compact)
            .forEach { bases += it.groupValues[1] to it.groupValues[2] }
        if (compact.contains("총내용량당")) {
            Regex("총내용량[:：]?[\\(]?($number)(g|ml)").findAll(compact)
                .forEach { bases += it.groupValues[1] to it.groupValues[2] }
        }
        val distinctBases = bases.map { amount(it.first) to it.second }.distinct()
        val basis = distinctBases.singleOrNull()?.takeIf { it.second == "g" }?.first?.takeIf { it.signum() > 0 }
        // Multiple amounts in one nutrient row indicate a multi-column label, even if the numbers match.
        var multipleColumns = false
        fun read(label: String, unit: String): BigDecimal? {
            val values = mutableListOf<BigDecimal?>()
            lines.forEach { line ->
                val matches = labels.findAll(line).toList()
                matches.forEachIndexed { i, match ->
                    if (match.value != label) return@forEachIndexed
                    val segment = line.substring(match.range.last + 1, matches.getOrNull(i + 1)?.range?.first ?: line.length)
                    val amounts = Regex("(?<![0-9.,])($number)$unit(?![a-z])").findAll(segment).toList()
                    if (amounts.size > 1) multipleColumns = true
                    val exact = Regex("^[:：]?($number)$unit(?:[0-9.]+%)?$").matchEntire(segment)
                    values += exact?.groupValues?.get(1)?.let(::amount)
                }
            }
            return values.takeIf { it.isNotEmpty() && it.none { value -> value == null } }?.distinct()?.singleOrNull()
        }
        val carbs = read("탄수화물", "g")
        val protein = read("단백질", "g")
        val fat = read("지방", "g")
        val fiber = read("식이섬유", "g") ?: read("식이섬유소", "g")
        // kcal can appear without a heading. Ignore the common daily-reference 2,000 kcal footnote.
        val kcalRows = lines.filterNot { it.contains("1일") || it.contains("영양성분기준치") || it.contains("기준으로") }
        val kcalCandidates = kcalRows.flatMap { line ->
            Regex("(?<![0-9.,<>≤≥-])($number)kcal(?![a-z])").findAll(line).map { match ->
                if (line.contains("미만") || line.contains("이하") || line.contains("이상")) null else amount(match.groupValues[1])
            }.toList()
        }
        if (kcalRows.any { Regex("kcal").findAll(it).count() > 1 }) multipleColumns = true
        val kcal = kcalCandidates.takeIf { it.isNotEmpty() && it.none { v -> v == null } }?.distinct()?.singleOrNull()
        val unsupportedBasis = distinctBases.size > 1 || distinctBases.any { it.second != "g" } || multipleColumns
        if (unsupportedBasis) warnings += "여러 기준량 또는 mL 표기가 있어요. 자동 입력하지 않았으니 g 기준 영양정보를 직접 확인해주세요."
        else if (basis == null) warnings += "영양정보가 몇 g 기준인지 확인하지 못했어요. 총 포장 무게와 영양표 기준량을 구분해주세요."
        val nutrition = if (unsupportedBasis) NutritionValues() else NutritionValues(kcal, carbs, protein, fat, fiber)
        if (listOf(nutrition.kcal, nutrition.carbsG, nutrition.proteinG, nutrition.fatG, nutrition.fiberG).any { it == null })
            warnings += "읽지 못했거나 정확한 수치가 아닌 항목은 비워두었어요. 원본에 없는 값은 그대로 빈칸으로 남겨주세요."
        return NutritionLabelDraft(raw, if (unsupportedBasis) null else basis, nutrition, warnings)
    }
}

data class LabelTextLine(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int)

/** OCR may return each nutrition column as a block. Rejoin only lines sharing a visual row. */
fun labelReadingOrder(lines: List<LabelTextLine>): String {
    val rows = mutableListOf<MutableList<LabelTextLine>>()
    lines.filter { it.bottom > it.top && it.right > it.left }.sortedWith(compareBy({ it.top }, { it.left })).forEach { line ->
        val row = rows.lastOrNull()?.takeIf { parts ->
            val anchor = parts.first()
            val overlap = minOf(anchor.bottom, line.bottom) - maxOf(anchor.top, line.top)
            overlap >= minOf(anchor.bottom - anchor.top, line.bottom - line.top) * 0.6 && parts.all { it.right <= line.left || line.right <= it.left }
        }
        if (row == null) rows += mutableListOf(line) else row += line
    }
    return rows.joinToString("\n") { row -> row.sortedBy { it.left }.joinToString(" ") { it.text } }
}
