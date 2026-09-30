package com.bapegg.routinlog.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bapegg.routinlog.ui.*
import com.bapegg.routinlog.ui.theme.Muted
import kotlin.math.roundToInt

private data class CardioPreset(val name: String, val met: Double, val source: String)
private val cardioPresets = listOf(
    CardioPreset("실내 자전거 · 50 W", 4.0, "https://pacompendium.com/bicycling/"),
    CardioPreset("실내 자전거 · 90–100 W", 6.0, "https://pacompendium.com/bicycling/"),
    CardioPreset("실내 자전거 · 126–150 W", 8.0, "https://pacompendium.com/bicycling/"),
    CardioPreset("야외 자전거 · 16 km/h 미만 여가 주행", 4.0, "https://pacompendium.com/bicycling/"),
    CardioPreset("실내 트랙·야외 걷기 · 스스로 정한 속도", 4.0, "https://pacompendium.com/walking/"),
)
private data class CardioResult(val minutes: Double = 0.0, val active: Double? = null, val gross: Double? = null, val source: String = "", val error: String? = null)
private fun number(ui: PreviewSession, key: String, fallback: String = "") = ui.get("w.cardio.$key", fallback).toDoubleOrNull()?.takeIf(Double::isFinite)
private fun segmentCount(ui: PreviewSession) = ui.get("w.cardio.segments", "1").toIntOrNull()?.coerceIn(1, 12) ?: 1
private fun whole(value: Double) = if (value % 1.0 == 0.0) value.toInt().toString() else String.format(java.util.Locale.ROOT, "%.1f", value)
private fun kcal(value: Double) = ((value / 10).roundToInt() * 10).toString()

/** Port of design-reference's published cardio estimate; never mutates a food target or sums device sources. */
private fun cardioResult(ui: PreviewSession): CardioResult {
    val mode = ui.get("w.cardio.mode", "소비량 계산")
    if (mode != "소비량 계산") {
        val minutes = number(ui, "minutes", "30") ?: return CardioResult(error = "실제 운동 시간을 입력해주세요.")
        if (minutes <= 0 || minutes > 1440) return CardioResult(error = "운동 시간을 0보다 크고 하루 이내로 입력해주세요.")
        if (mode == "시간만 기록") {
            if (ui.get("w.cardio.activity", "트레드밀 걷기").isBlank()) return CardioResult(error = "운동 이름을 적어주세요.")
            return CardioResult(minutes = minutes, source = "시간·체감 직접 기록")
        }
        val active = number(ui, "deviceActive") ?: return CardioResult(error = "기기에 표시된 활동 칼로리를 입력해주세요.")
        if (active < 0 || active > 100000) return CardioResult(error = "활동 칼로리와 단위를 확인해주세요.")
        if (ui.get("w.cardio.deviceName").isBlank()) return CardioResult(error = "기기 또는 앱 이름을 적어주세요.")
        return CardioResult(minutes, active = active, source = "${ui.get("w.cardio.deviceName")} · 직접 입력")
    }
    val weight = number(ui, "weight", "83.2") ?: return CardioResult(error = "현재 체중을 입력해주세요.")
    if (weight <= 0 || weight > 1000) return CardioResult(error = "체중과 단위를 확인해주세요.")
    if (ui.get("w.cardio.method", "트레드밀 걷기") == "다른 유산소") {
        val minutes = number(ui, "minutes", "30") ?: return CardioResult(error = "실제 운동 시간을 입력해주세요.")
        if (minutes <= 0 || minutes > 1440) return CardioResult(error = "운동 시간을 확인해주세요.")
        val preset = cardioPresets.firstOrNull { it.name == ui.get("w.cardio.preset", cardioPresets.first().name) } ?: return CardioResult(error = "활동 조건을 선택해주세요.")
        return CardioResult(minutes, (preset.met - 1) * 3.5 * weight / 200 * minutes, preset.met * 3.5 * weight / 200 * minutes, "2024 Adult Compendium · MET")
    }
    var minutesTotal = 0.0; var activeTotal = 0.0; var grossTotal = 0.0
    for (index in 0 until segmentCount(ui)) {
        val speed = number(ui, "segment.$index.speed", "5")
        val grade = number(ui, "segment.$index.grade", "5")
        val minutes = number(ui, "segment.$index.minutes", "30")
        if (speed == null || speed !in 3.2..5.6) return CardioResult(error = "${index + 1}구간: 보행식은 3.2–5.6 km/h 걷기에 사용해요. 범위 밖 운동은 기기값이나 시간만 기록해주세요.")
        if (grade == null || grade !in 0.0..15.0) return CardioResult(error = "${index + 1}구간: 경사 0–15%의 걷기를 입력해주세요.")
        if (minutes == null || minutes <= 0 || minutes > 1440) return CardioResult(error = "${index + 1}구간의 실제 시간을 확인해주세요.")
        val metresPerMinute = speed * 1000 / 60
        val vo2 = .1 * metresPerMinute + 1.8 * metresPerMinute * (grade / 100) + 3.5
        minutesTotal += minutes
        grossTotal += vo2 * weight / 200 * minutes
        activeTotal += (vo2 - 3.5) * weight / 200 * minutes
    }
    if (minutesTotal > 1440) return CardioResult(error = "전체 운동 시간이 하루를 넘어요. 구간별 시간을 확인해주세요.")
    return CardioResult(minutesTotal, activeTotal, grossTotal, "ACSM 보행식 · 입력 조건 기반")
}

@Composable
internal fun WorkoutCardioScreen(ui: PreviewSession) {
    MutedText("오늘 한 만큼 기록해요. 소비 열량은 참고용 추정치예요.")
    Chips(listOf("소비량 계산", "기기 기록 입력", "시간만 기록"), ui.get("w.cardio.mode", "소비량 계산")) { ui.set("w.cardio.mode", it) }
    val mode = ui.get("w.cardio.mode", "소비량 계산")
    UiCard {
        if (mode == "소비량 계산") {
            Chips(listOf("트레드밀 걷기", "다른 유산소"), ui.get("w.cardio.method", "트레드밀 걷기")) { ui.set("w.cardio.method", it) }
            CardioInput("현재 체중", "weight", "83.2", "kg", ui)
            if (ui.get("w.cardio.method", "트레드밀 걷기") == "트레드밀 걷기") {
                SectionTitle("실제로 걸은 구간")
                MutedText("속도·경사가 같으면 1구간")
                repeat(segmentCount(ui)) { index ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("${index + 1}구간", fontWeight = FontWeight.SemiBold); if (index > 0 && index == segmentCount(ui) - 1) TextButton(onClick = { ui.set("w.cardio.segments", index.toString()) }) { Text("구간 삭제") } }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f)) { CardioInput("속도", "segment.$index.speed", "5", "km/h", ui) }
                        Column(Modifier.weight(1f)) { CardioInput("경사", "segment.$index.grade", "5", "%", ui) }
                        Column(Modifier.weight(1f)) { CardioInput("시간", "segment.$index.minutes", "30", "분", ui) }
                    }
                    DividerLine()
                }
                UiButton("+ 속도·경사가 바뀐 구간 추가", { ui.set("w.cardio.segments", (segmentCount(ui) + 1).coerceAtMost(12).toString()) }, primary = false, enabled = segmentCount(ui) < 12)
                MutedText("걷기 3.2–5.6 km/h · 경사 0–15%\n손잡이에 체중을 싣거나 짧게 강도를 바꾸면 오차가 커져요.")
            } else {
                SectionTitle("내 운동과 일치하는 조건")
                cardioPresets.forEach { preset -> Choice(preset.name, selected = ui.get("w.cardio.preset", cardioPresets.first().name) == preset.name, onClick = { ui.set("w.cardio.preset", preset.name) }) }
                CardioInput("실제 시간", "minutes", "30", "분", ui)
            }
        } else {
            if (mode == "기기 기록 입력") {
                Input("기기 또는 앱 이름", ui.get("w.cardio.deviceName"), { ui.set("w.cardio.deviceName", it) })
                CardioInput("기기에 표시된 활동 칼로리", "deviceActive", "", "kcal", ui)
                MutedText("휴식분을 포함한 총 소비 열량과 구분해 입력해주세요. 기기와 자동 연동되는 기능은 아니에요.")
            } else Input("운동 이름", ui.get("w.cardio.activity", "트레드밀 걷기"), { ui.set("w.cardio.activity", it) })
            CardioInput("실제 시간", "minutes", "30", "분", ui)
        }
    }
    val result = cardioResult(ui)
    if (result.error != null) {
        UiCard { Text("입력 조건을 확인해주세요", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error); MutedText(result.error) }
    } else UiCard {
        MutedText(if (result.active == null) "시간만 기록" else if (mode == "기기 기록 입력") "활동 칼로리 · 기기값 입력" else "활동 칼로리 · 추정")
        if (result.active == null) HeroNumber(whole(result.minutes), "분") else HeroNumber("${if (mode == "기기 기록 입력") "" else "약 "}${kcal(result.active)}", "kcal")
        MutedText("${whole(result.minutes)}분 · ${result.source}")
        if (result.gross != null) { DividerLine(); KeyValue("휴식분 포함 총 소비", "약 ${kcal(result.gross)} kcal"); Text("위 숫자는 쉬어도 소비되는 열량을 뺀 추정치예요.", color = Muted, fontSize = 12.sp) }
        if (result.active == null) MutedText("활동 시간과 체감을 남겨요. 소비 칼로리는 ‘정보 없음’으로 보관해요.")
    }
    UiCard {
        SectionTitle("오늘 체감 · 선택")
        WorkoutChips("운동 중 난도", listOf("가벼움", "보통", "힘듦"), "w.cardio.effort", "", ui)
        WorkoutChips("지금 피로", listOf("낮음", "보통", "높음"), "w.cardio.fatigue", "", ui)
        Input("메모 · 선택", ui.get("w.cardio.memo"), { ui.set("w.cardio.memo", it) }, multiline = true)
    }
    WorkoutExpand("소비량 계산법과 출처", "w.cardio.sourcesOpen", ui) {
        BodyText("걷기: 산소소비량 = 0.1 × 속도 + 1.8 × 속도 × 경사 + 3.5. 속도는 m/분, 경사 5%는 0.05예요. 총 kcal = 산소소비량 × 체중kg ÷ 200 × 시간(분).")
        BodyText("MET: 총 kcal = MET × 3.5 × 체중kg ÷ 200 × 시간(분). 쉬어도 소비되는 열량(1 MET)을 뺀 값을 활동 칼로리로 보여줘요. 표시값은 10 kcal 단위로 반올림해요.")
        MutedText("2024 Adult Compendium은 19–59세 일반 성인의 활동 기준이에요. 같은 조건이어도 나이·체력·기구·운동 방식에 따라 실제 소비량은 달라질 수 있어요.")
        MutedText("난도와 피로는 다음 운동을 조정할 때 참고해요. 이 값이나 걸음 수만으로 소비량을 정확하게 알 수는 없어요.")
        WorkoutLink("보행식과 추정 오차 연구", "https://pmc.ncbi.nlm.nih.gov/articles/PMC7896743/", ui)
        WorkoutLink("2024 활동 MET · 자전거", "https://pacompendium.com/bicycling/", ui)
        WorkoutLink("2024 활동 MET · 걷기", "https://pacompendium.com/walking/", ui)
    }
    WorkoutNote("같은 운동은 한 가지 방법으로만 기록해주세요. 운동 소비량을 기록해도 식사 목표는 그대로예요.")
}

@Composable
private fun CardioInput(label: String, key: String, fallback: String, unit: String, ui: PreviewSession) {
    Input(label, ui.get("w.cardio.$key", fallback), { ui.set("w.cardio.$key", it) }, suffix = unit, numeric = true)
}

internal fun saveWorkoutCardio(ui: PreviewSession) {
    val result = cardioResult(ui)
    if (result.error != null) { ui.notify(result.error); return }
    ui.set("w.cardioSaved", "${whole(result.minutes)}분 · ${result.active?.let { "활동 약 ${kcal(it)} kcal" } ?: "열량 정보 없음"}")
    ui.set("w.cardio.saved.active", result.active?.toString() ?: "")
    ui.set("w.cardio.saved.gross", result.gross?.toString() ?: "")
    ui.set("w.cardio.saved.source", result.source)
    ui.set("w.cardio.saved.policy", "cardio-mvp-2026-09-25.1")
    ui.save("W08")
}
