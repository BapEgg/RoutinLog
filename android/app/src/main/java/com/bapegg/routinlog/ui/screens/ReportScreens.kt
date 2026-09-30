package com.bapegg.routinlog.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bapegg.routinlog.ui.*
import com.bapegg.routinlog.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ReportScreens(id: String, ui: PreviewSession) {
    when (id) {
        "R01" -> ReportOverview(ui)
        "R02" -> NutritionComparison(ui)
        "R03" -> WorkoutComparison(ui)
        "R04" -> BodyActivity(ui)
        "R05" -> NextProposal(ui)
        "R06" -> EditProposal(ui)
        "R07" -> ConfirmProposal(ui)
        "R08" -> ProposalEvidence(ui)
        "R09" -> ReportHistory(ui)
        "R10" -> IncompleteReport(ui)
        "R11" -> AppliedProposal(ui)
        else -> SettingsScreens(id, ui)
    }
}

@Composable
fun ReportFooter(id: String, ui: PreviewSession) {
    when (id) {
        "R01" -> UiButton("다음 주에는 무엇을 바꿀까요?", { ui.go("R05") })
        "R02" -> UiButton("다음 주 초안 보기", { ui.go("R05") })
        "R03" -> UiButton("운동 제안 확인", { ui.go("R05") })
        "R04" -> UiButton("날짜별 신체 기록", { ui.go("H05") }, primary = false)
        "R05" -> {
            UiButton("이 제안을 초안에 반영", { ui.set("r.decision", "제안한 초안"); ui.go("R07") })
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                UiButton("직접 수정", { ui.go("R06") }, primary = false, modifier = Modifier.weight(1f))
                UiButton("이번에는 보류", {
                    ui.set("r.decision", "보류"); ui.set("r.applied", "false"); ui.save("R09")
                }, primary = false, modifier = Modifier.weight(1f))
            }
        }
        "R06" -> UiButton("수정안을 초안에 반영", {
            if (ui.get("r.draftName", "닭가슴살 파스타").isBlank()) ui.notify("대체 식사 이름을 입력해주세요.")
            else { ui.set("r.decision", "직접 수정"); ui.go("R07") }
        })
        "R07" -> {
            UiButton("확인한 계획 적용", {
                ui.set("r.applied", "true")
                ui.set("r.appliedName", draftMealName(ui))
                ui.set("r.appliedDate", ui.get("r.applyDate", "2026-09-28"))
                ui.save("R11")
            })
            UiButton("더 수정하기", { ui.go("R06") }, primary = false)
        }
        "R10" -> UiButton("오늘 기록하러 가기", { ui.go("H01") })
        "R11" -> UiButton("오늘 기록으로 돌아가기", { ui.go("H01") })
        else -> SettingsFooter(id, ui)
    }
}

private fun draftMealName(ui: PreviewSession): String =
    if (ui.get("r.decision") == "직접 수정") ui.get("r.draftName", "닭가슴살 파스타") else "빠른 점심 세트"

@Composable
private fun ReportOverview(ui: PreviewSession) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.align(Alignment.CenterVertically).padding(end = 8.dp)) { MutedText("9월 14일 — 20일") }
        OutlinedButton(onClick = { ui.go("R09") }, shape = RoundedCornerShape(11.dp),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = Ink),
            modifier = Modifier.heightIn(min = 50.dp)) {
            Text("이전 리포트", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }
    }
    ReportHeading("조금 달라진 계획,\n꾸준히 이어진 기록.")
    ReportNote {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ReportLabel("이번 리포트의 기록 범위"); UiIcon("Check", tint = Muted)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("식사 21/21", "운동 4/4", "체중 6회", "걸음 7일").forEach {
                Text(it, Modifier.weight(1f).background(Color(0xFFF5F8FC), RoundedCornerShape(6.dp)).padding(horizontal = 4.dp, vertical = 6.dp),
                    color = Muted, fontSize = 11.sp, textAlign = TextAlign.Center)
            }
        }
    }
    UiCard {
        SectionTitle("영양 섭취", "상세", { ui.go("R02") })
        HeroNumber("17,640", "kcal")
        MutedText("주간 목표보다 +840 kcal")
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                ReportStat("단백질", "1,050", "g"); MutedText("목표 1,190 g")
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                ReportStat("변경한 식사", "4", "끼"); MutedText("대체 3끼 · 외식 1끼")
            }
        }
        ProgressLine(1050f / 1190f)
        MutedText("식이섬유는 2끼 정보가 없어 부분 합계예요.")
    }
    UiCard {
        SectionTitle("운동 수행", "상세", { ui.go("R03") })
        KeyValue("계획한 운동일", "4 / 4 일")
        ProgressLine(1f)
        KeyValue("완료한 본 세트", "44 / 48")
        MutedText("기구 대기로 종목을 1회 바꿨고, 스쿼트 반복수를 조정했어요.")
    }
    UiCard {
        SectionTitle("몸과 활동의 변화", "상세", { ui.go("R04") })
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1f)) { ReportStat("평균 체중", "83.3", "kg"); MutedText("전주 −0.3 kg · 6회 측정") }
            Column(Modifier.weight(1f)) { ReportStat("평균 허리", "80.1", "cm"); MutedText("전주 −0.3 cm · 4회 측정") }
        }
        ReportLabel("최근 4주 체중 추세")
        ReportWeightChart()
        MutedText("식사 차이만으로 몸의 변화 원인을 확정하지 않아요.")
    }
}

private data class WeeklyNutrition(val label: String, val target: Int, val actual: Int, val partial: Boolean = false)
private val weeklyNutrition = listOf(
    WeeklyNutrition("열량", 16800, 17640), WeeklyNutrition("탄수화물", 1960, 2040),
    WeeklyNutrition("단백질", 1190, 1050), WeeklyNutrition("지방", 469, 570),
    WeeklyNutrition("식이섬유", 210, 132, true),
)

@Composable
private fun NutritionComparison(ui: PreviewSession) {
    val daily = ui.get("r.nutritionRange", "주간 총량") == "일평균"
    Chips(listOf("주간 총량", "일평균"), if (daily) "일평균" else "주간 총량", { ui.set("r.nutritionRange", it) })
    UiCard {
        ReportTableRow(listOf("항목", "목표", "실제", "차이"), heading = true)
        weeklyNutrition.forEach { row ->
            DividerLine()
            fun amount(n: Int): String = if (daily) String.format(Locale.KOREA, "%,.1f", n / 7.0) else String.format(Locale.KOREA, "%,d", n)
            val diff = row.actual - row.target
            ReportTableRow(listOf(row.label, amount(row.target), amount(row.actual) + if (row.partial) "*" else "",
                if (row.partial) "부분 합계" else (if (diff > 0) "+" else "−") + amount(kotlin.math.abs(diff))))
        }
        MutedText(if (daily) "열량 kcal · 영양소 g / 기록한 7일의 평균" else "열량 kcal · 영양소 g / 7일 기록")
    }
    ReportNote { BodyText("식이섬유는 2끼 정보가 없어 부족량을 확정하지 않아요.") }
    SectionTitle("달라진 식사")
    UiCard {
        ReportTimeline("화요일 점심 · 재료 없음", "기본 점심 → 닭가슴살 파스타\n해당 끼니 지방 +5 g")
        DividerLine()
        ReportTimeline("목요일 저녁 · 외식", "실제 먹은 음식과 양으로 기록\n바꿔 먹은 식사만 합산했어요.")
        DividerLine()
        ReportTimeline("금요일 점심 · 시간 부족", "기본 식사 → 간단한 식사")
    }
    ReportNote { BodyText("이번 주의 차이를 다음 주에 한꺼번에 채울 필요는 없어요. 내 목표와 몇 주간의 흐름을 함께 살펴요.") }
}

@Composable
private fun WorkoutComparison(ui: PreviewSession) {
    UiCard {
        KeyValue("계획 / 실제 운동일", "4일 / 4일")
        KeyValue("본 세트", "계획 48 / 수행 44")
        ProgressLine(44f / 48f)
        KeyValue("종목 변경", "1회 · 기구 대기")
    }
    listOf("월 · 상체 A", "화 · 하체 A", "목 · 상체 B", "금 · 하체 B").forEachIndexed { index, title ->
        val expanded = ui.flag("r.workoutDay.$index", index == 1)
        UiCard {
            UiRow(title, value = if (expanded) "접기" else "자세히", onClick = { ui.toggle("r.workoutDay.$index", index == 1) })
            if (expanded) {
                when (index) {
                    1 -> {
                        ReportTableRow(listOf("세트", "스쿼트 계획", "실제"), heading = true)
                        listOf("100 kg × 8", "100 kg × 8", "80 kg × 10").forEachIndexed { i, actual ->
                            DividerLine(); ReportTableRow(listOf("${i + 1}", "100 kg × 10", actual))
                        }
                        MutedText("마지막 세트 난도: 버거움 · 중량 조정")
                    }
                    3 -> { ReportLabel("스미스 스쿼트 → 레그프레스"); MutedText("기구 대기 · 기존 대체 기록 참고") }
                    else -> BodyText("계획한 운동을 했어요. 마지막 세트까지 기록했어요.")
                }
            }
        }
    }
    SectionTitle("관련 부위 기록")
    UiCard {
        KeyValue("대퇴사두 중심 운동", "계획 9 / 실제 7세트")
        ProgressLine(7f / 9f)
        KeyValue("햄스트링 중심 운동", "계획 6 / 실제 6세트")
        ProgressLine(1f)
        MutedText("보조로 관여한 운동은 별도로 표시해요. 세트 수가 실제 근육 자극량과 같지는 않아요.")
    }
    ReportNote { BodyText("다른 날의 운동과 피로도 함께 봐요. 못 한 세트를 다음 주에 몰아서 채울 필요는 없어요.") }
}

@Composable
private fun BodyActivity(ui: PreviewSession) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        UiCard(modifier = Modifier.weight(1f)) { ReportStat("평균 체중", "83.3", "kg"); MutedText("전주 −0.3 · 6회") }
        UiCard(modifier = Modifier.weight(1f)) { ReportStat("평균 허리", "80.1", "cm"); MutedText("전주 −0.3 · 4회") }
    }
    UiCard {
        SectionTitle("체중 · 최근 4주 평균")
        ReportWeightChart()
    }
    UiCard {
        ReportLabel("일평균 걸음")
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("6,850 →", color = Muted, fontSize = 20.sp)
            Text("7,120", fontWeight = FontWeight.Bold, fontSize = 29.sp)
            MutedText("걸음")
        }
        BarChart(listOf(6300f, 8200f, 6800f, 7600f, 9200f, 5900f, 5840f), listOf("월", "화", "수", "목", "금", "토", "일"))
        KeyValue("확인한 기간", "7 / 7일 수집")
        KeyValue("높은 피로 기록", "2일 / 컨디션 기록 5일")
    }
    ReportNote {
        ReportLabel("기록에서 보이는 점")
        BodyText("평균 체중과 허리는 전주보다 낮았어요. 다만 식사 변경이나 운동 한 가지가 원인이라고 확정할 수는 없어요.")
    }
}

@Composable
private fun NextProposal(ui: PreviewSession) {
    Badge("우선 변경 1건")
    ReportHeading("바쁜 날의 점심을\n하나 더 준비해요.")
    BodyText("지난주 점심 2끼를 시간과 재료 사정으로 바꿨어요. 현재 목표 안에서 바로 고를 수 있는 대체 식사를 제안해요.")
    UiCard {
        Badge("기본 점심 유지 + 대체 옵션")
        SectionTitle("빠른 점심 세트")
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ReportStat("기본 점심", "860", "kcal", Modifier.weight(1f))
            ReportStat("대체 식사", "850", "kcal", Modifier.weight(1f))
        }
        MutedText("단백질 68 g · 확인한 식품으로 구성")
    }
    ReportNote {
        ReportLabel("운동은 지금 구성을 유지해요.")
        BodyText("한 번의 반복수 감소만으로 강도를 높이지 않고, 다음 수행과 난도를 더 확인해요.")
    }
    UiButton("연결된 기록과 과학적 근거", { ui.go("R08") }, primary = false)
    UiButton("다른 운동 프로그램 보기", { ui.go("W02") }, primary = false)
}

@Composable
private fun EditProposal(ui: PreviewSession) {
    UiCard { MutedText("원래 제안"); ReportLabel("빠른 점심 세트를 대체 식사로 추가") }
    Input("나의 대체 식사 이름", ui.get("r.draftName", "닭가슴살 파스타"), { ui.set("r.draftName", it) })
    UiButton("구성 음식 · 양 수정", { beginReportMealEdit(ui) }, primary = false)
    UiCard {
        KeyValue("예상 열량", ui.get("r.draftKcal", "850 kcal"))
        KeyValue("탄수화물", ui.get("r.draftCarbs", "정보 없음"))
        KeyValue("단백질", ui.get("r.draftProtein", "68 g"))
        KeyValue("지방", ui.get("r.draftFat", "20 g"))
        KeyValue("식이섬유", ui.get("r.draftFiber", "정보 없음"))
        MutedText(if(ui.flag("r.draftNutritionEdited")) "수정한 음식과 실제 먹는 양으로 다시 합산했어요." else "제안에 사용한 식사의 영양정보예요. 구성 변경은 식사 편집에서 확인해요.")
    }
    val scope = ui.get("r.scope", "다음 주만")
    Choice("다음 주만", selected = scope == "다음 주만", onClick = { ui.set("r.scope", "다음 주만") })
    Choice("기본 저장 식사에도 추가", selected = scope == "기본 저장 식사에도 추가", onClick = { ui.set("r.scope", "기본 저장 식사에도 추가") })
    SectionTitle("바꾼 이유 (선택)")
    Chips(listOf("선호하는 음식", "준비하기 쉬움", "시간", "비용", "생략"), ui.get("r.reason", "생략"), { ui.set("r.reason", it) })
}

@Composable
private fun ConfirmProposal(ui: PreviewSession) {
    ReportNote {
        Badge("이번에 달라지는 것")
        ReportLabel("${draftMealName(ui)} 추가")
        MutedText("점심 대체 옵션으로 저장 · ${ui.get("r.decision", "제안한 초안")}")
    }
    UiCard {
        KeyValue("목적", ui.get("onb.goal", "근육 증가"))
        KeyValue("일일 영양 목표", "현재 목표 유지")
        KeyValue("운동 루틴", "기존 구성 유지")
    }
    ReportDateField("적용 시작일", "r.applyDate", "2026-09-28", ui)
    UiCard {
        val date = runCatching { LocalDate.parse(ui.get("r.applyDate", "2026-09-28")) }.getOrDefault(LocalDate.of(2026, 9, 28))
        SectionTitle("${date.monthValue}.${date.dayOfMonth} — ${date.plusDays(6).monthValue}.${date.plusDays(6).dayOfMonth}")
        val plans = listOf("월" to "상체 A", "화" to "하체 A", "수" to "휴식 / 자유 활동", "목" to "상체 B", "금" to "하체 B", "토" to "가벼운 걷기", "일" to "휴식")
        repeat(7) { offset ->
            val day = date.plusDays(offset.toLong())
            val (label, plan) = plans[day.dayOfWeek.ordinal]
            KeyValue("$label · ${day.monthValue}.${day.dayOfMonth}", plan)
        }
        DividerLine()
        BodyText("아침·저녁 유지\n점심: 기본 또는 새 대체 식사 선택")
    }
    ReportNote { BodyText("이미 먹고 운동한 기록은 바뀌지 않아요. 적용 후에도 직접 수정할 수 있어요.") }
}

@Composable
private fun ProposalEvidence(ui: PreviewSession) {
    val uri = LocalUriHandler.current
    fun open(url: String) { runCatching { uri.openUri(url) }.onFailure { ui.notify("링크를 열 수 없어요. 인터넷 연결을 확인해주세요.") } }
    UiCard {
        Badge("01 · 내 기록")
        ReportLabel("점심 2끼 변경 · 시간/재료 문제")
        BodyText("단백질 목표 대비 −140 g/주. 기록된 영양값이 완전한 항목을 기준으로 비교했어요.")
    }
    UiCard {
        Badge("02 · 이 방법을 제안한 이유")
        BodyText("바쁜 날 바로 고를 식사가 있으면 계획을 이어가기 수월할 수 있어요. 실제로 도움이 됐는지는 다음 기록에서 함께 살펴요.")
    }
    SectionTitle("03 · 참고 자료")
    UiCard {
        ReportLabel("총단백질 섭취와 저항운동")
        MutedText("Morton 등, 2018 · 49개 연구 종합")
        BodyText("근력운동과 단백질 섭취의 관계를 살핀 연구예요.")
        UiButton("연구 원문 정보 ↗", { open("https://pubmed.ncbi.nlm.nih.gov/28698222/") }, primary = false)
        DividerLine()
        ReportLabel("계획 이탈 후 습관 재개")
        MutedText("NIDDK · 건강한 습관을 이어가는 방법")
        BodyText("개별 메뉴 효과를 검증한 시험은 아니에요.")
        UiButton("공식 안내 ↗", { open("https://www.niddk.nih.gov/health-information/diet-nutrition/changing-habits-better-health") }, primary = false)
    }
    ReportNote { BodyText("연구가 이 850 kcal 메뉴의 효과나 내게 맞는 양을 보장하지는 않아요. 내 목표와 저장한 식사, 가능한 준비 시간을 고려한 초안이에요.") }
    UiCard {
        ReportLabel("운동 원칙 참고 자료")
        MutedText("ACSM 2026 · 성인 저항운동 지침")
        BodyText("목표에 맞는 점진적 운동 원칙을 참고해요. 프로그램 전체나 개인의 정확한 회복 시간을 보장하지 않아요.")
        UiButton("연구 원문 정보 ↗", { open("https://pubmed.ncbi.nlm.nih.gov/41843416/") }, primary = false)
    }
}

@Composable
private fun ReportHistory(ui: PreviewSession) {
    val filter = ui.get("r.historyFilter", "전체")
    Chips(listOf("전체", "적용", "직접 수정", "보류"), filter, { ui.set("r.historyFilter", it) })
    val status = if (ui.flag("r.applied")) "적용" else ui.get("r.decision", "검토 중")
    var shown = 0
    if (filter == "전체" || filter == status || (filter == "직접 수정" && ui.get("r.decision") == "직접 수정")) {
        shown++
        UiCard {
            SectionTitle("12주차 · 점심 대체 식사")
            Badge(status)
            BodyText("바꾼 이유: ${ui.get("r.reason", "시간 · 재료")}")
            MutedText(if (ui.flag("r.applied")) "${ui.get("r.appliedName", "빠른 점심 세트")} · 체험 계획에 반영" else "현재 제안 원안")
            UiButton("리포트 열기", { ui.go("R01") }, primary = false)
        }
    }
    if (filter in listOf("전체", "적용")) {
        shown++
        UiCard {
            SectionTitle("11주차 · 기구 대체 후보"); Badge("적용")
            BodyText("스쿼트 대기 시 레그프레스 선택")
            MutedText("실제 사용 1회 · 계속 관찰")
            UiButton("당시 기록 보기", { ui.go("R03") }, primary = false)
        }
    }
    if (filter in listOf("전체", "직접 수정")) {
        shown++
        UiCard { SectionTitle("10주차 · 일정 재배치"); Badge("직접 수정"); BodyText("금요일 → 토요일로 변경"); MutedText("사유: 업무 일정") }
    }
    if (filter == "전체") {
        shown++
        UiCard { SectionTitle("9주차 · 현재 계획 유지"); Badge("유지"); BodyText("추가 변경 없이 기록을 이어갔어요.") }
    }
    if (shown == 0) UiCard { BodyText("아직 ${filter}한 제안이 없어요."); MutedText("내가 선택한 방향을 이곳에서 확인할 수 있어요.") }
}

@Composable
private fun IncompleteReport(ui: PreviewSession) {
    ReportSymbol("ChartNoAxesCombined")
    ReportHeading("이번 주는\n기록부터 모아볼게요.")
    UiCard {
        KeyValue("식사", "4 / 21끼 · 영양값 일부 없음")
        KeyValue("체중 · 허리", "각 1회")
        KeyValue("걸음", "연동 안 됨")
        KeyValue("운동", "1일 기록")
    }
    BodyText("확인된 운동과 식사는 볼 수 있어요. 한 주 전체 섭취량이나 신체 추세를 판단하기에는 자료가 적어요.")
    ReportNote { BodyText("지금 목표로 기록을 이어가도 괜찮아요. 기록하지 않은 날은 0으로 계산하지 않아요.") }
    UiButton("아직 기록하지 않은 날 보기", { ui.go("H03") }, primary = false)
}

@Composable
private fun AppliedProposal(ui: PreviewSession) {
    val applied = ui.flag("r.applied")
    ReportSymbol(if (applied) "Check" else "ClipboardList")
    ReportHeading(if (applied) "선택한 방향으로\n다시 기록해요." else "다음 주 계획을\n먼저 확인해요.")
    UiCard {
        ReportLabel(ui.get("r.appliedName", draftMealName(ui)))
        BodyText("점심 대체 식사로 추가")
        MutedText("적용 시작 ${ui.get("r.appliedDate", ui.get("r.applyDate", "2026-09-28"))}")
        DividerLine()
        KeyValue("영양 목표", "유지")
        KeyValue("운동 루틴", "유지")
    }
    BodyText("계획을 적용한 뒤 실제로 어떻게 먹고 운동했는지 다음 리포트에서 함께 확인해요.")
    UiButton("내가 고른 제안 이력", { ui.go("R09") }, primary = false)
    if (!applied) UiButton("적용 전 확인하기", { ui.go("R07") }, primary = false)
}

@Composable
internal fun ReportHeading(text: String) { Text(text, style = MaterialTheme.typography.headlineMedium, color = Ink) }

@Composable
internal fun ReportLabel(text: String) { Text(text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = Ink) }

@Composable
private fun ReportStat(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        MutedText(label)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(value, fontWeight = FontWeight.SemiBold, fontSize = 23.sp, color = Ink)
            Text(unit, Modifier.padding(bottom = 2.dp), fontSize = 12.sp, color = Muted)
        }
    }
}

@Composable
internal fun ReportNote(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(Color(0xFFE2E9F0), RoundedCornerShape(16.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
}

@Composable
internal fun ReportSymbol(name: String, warning: Boolean = false) {
    Box(Modifier.size(64.dp).background(if (warning) Color(0xFFFCEDEA) else Color(0xFFE3EBF5), RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
        val tint = if (warning) MaterialTheme.colorScheme.error else DeepBlue
        when (name) {
            "Trash2" -> Canvas(Modifier.size(29.dp)) {
                fun point(x: Float, y: Float) = Offset(size.width * x / 24, size.height * y / 24)
                fun line(x: Float, y: Float, x2: Float, y2: Float) = drawLine(tint, point(x, y), point(x2, y2), 1.8.dp.toPx(), StrokeCap.Round)
                line(4f, 7f, 20f, 7f); line(9f, 7f, 9f, 4f); line(9f, 4f, 15f, 4f); line(15f, 4f, 15f, 7f)
                line(6f, 7f, 7f, 21f); line(7f, 21f, 17f, 21f); line(17f, 21f, 18f, 7f)
                line(10f, 11f, 10f, 17f); line(14f, 11f, 14f, 17f)
            }
            "WifiOff" -> {
                UiIcon("Wifi", Modifier.size(28.dp), tint)
                Canvas(Modifier.size(29.dp)) { drawLine(tint, Offset(2.dp.toPx(), 2.dp.toPx()), Offset(size.width - 2.dp.toPx(), size.height - 2.dp.toPx()), 2.dp.toPx(), StrokeCap.Round) }
            }
            "ClipboardList" -> UiIcon("BookOpen", Modifier.size(28.dp), tint)
            else -> UiIcon(name, Modifier.size(28.dp), tint)
        }
    }
}

@Composable
private fun ReportTimeline(title: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.padding(top = 7.dp).size(8.dp).background(Celery, RoundedCornerShape(4.dp)))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { ReportLabel(title); MutedText(text) }
    }
}

@Composable
private fun ReportTableRow(cells: List<String>, heading: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        cells.forEachIndexed { i, text ->
            Text(text, Modifier.weight(if (i == 0 && cells.size == 3) .48f else 1f).padding(vertical = 7.dp),
                color = if (heading) Muted else Ink, fontWeight = if (heading || i == 0) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 13.sp, lineHeight = 20.sp, textAlign = if (i == 0) TextAlign.Start else TextAlign.End)
        }
    }
}

@Composable
private fun ReportWeightChart() {
    LineChart(listOf(83.9f, 83.7f, 83.6f, 83.3f), Modifier.fillMaxWidth().height(120.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { listOf("1주", "2주", "3주", "4주").forEach { MutedText(it) } }
}

@Composable
internal fun ReportDateField(label: String, key: String, default: String, ui: PreviewSession) {
    val context = LocalContext.current
    val date = runCatching { LocalDate.parse(ui.get(key, default)) }.getOrDefault(LocalDate.parse(default))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ReportLabel(label)
        UiButton(date.format(DateTimeFormatter.ofPattern("yyyy년 M월 d일")), {
            DatePickerDialog(context, { _, year, month, day -> ui.set(key, LocalDate.of(year, month + 1, day).toString()) }, date.year, date.monthValue - 1, date.dayOfMonth).show()
        }, primary = false)
    }
}
