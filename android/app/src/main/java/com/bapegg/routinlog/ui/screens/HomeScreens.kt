package com.bapegg.routinlog.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bapegg.routinlog.domain.BodyMeasurementInput
import com.bapegg.routinlog.ui.*
import com.bapegg.routinlog.ui.theme.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.floor

/** Shared design; authenticated recording uses server-backed AccountActions. */
@Composable
fun HomeScreens(id: String, ui: PreviewSession) {
    when (id) {
        "A01" -> Welcome(ui)
        "A02" -> Consent(ui)
        "A03" -> Goal(ui)
        "A04" -> Basics(ui)
        "A05" -> Activity(ui)
        "A06" -> Schedule(ui)
        "A07" -> StartingTargets(ui)
        "A08" -> StepConnection(ui)
        "H01" -> if(ui.accountMode)LiveToday(ui)else Today(ui)
        "H02" -> if(ui.accountMode)LiveToday(ui)else FirstHome(ui)
        "H03" -> RecordCalendar(ui)
        "H04" -> BodyEntry(ui)
        "H05" -> if(ui.accountMode)LiveBodyHistory(ui)else BodyHistory(ui)
        "H06" -> Steps(ui)
        "H07" -> Condition(ui)
    }
}

@Composable
fun HomeFooter(id: String, ui: PreviewSession) {
    val account=LocalAccount.current
    when (id) {
        "A02" -> UiButton("동의하고 계속", { ui.go("A03") }, enabled = ui.flag("onb.terms") && ui.flag("onb.privacy") && (!ui.accountMode||ui.flag("onb.health")))
        "A03" -> UiButton("다음", { ui.go("A04") })
        "A04" -> UiButton("다음", {
            val error = basicsError(ui)
            if (error == null) ui.go("A05") else ui.notify(error)
        })
        "A05" -> UiButton("다음", { ui.go("A06") })
        "A06" -> UiButton("내 시작 목표 확인", { ui.go("A07") })
        "A07" -> {
            UiButton(if (ui.get("onb.targetMode", "자동으로 정하기") == "직접 설정") "이 목표로 기록하기" else "이 시작 목표로 기록하기", {
                if (ui.get("onb.targetMode", "자동으로 정하기") == "직접 설정") {
                    val keys = listOf("kcal", "carbs", "protein", "fat")
                    if (keys.any { key -> ui.get("onb.manual.$key").toDoubleOrNull()?.let { !it.isFinite() || it < 0 } ?: true } || (ui.get("onb.manual.kcal").toDoubleOrNull() ?: 0.0) <= 0) {
                        ui.notify("열량은 0보다 크게, 영양소는 0 이상의 숫자로 입력해주세요.")
                    } else {
                        keys.forEach { ui.set("target.$it", ui.get("onb.manual.$it")) }
                        ui.set("target.fiber", "")
                        ui.set("target.method", "manual")
                        finishTargets(ui)
                    }
                } else {
                    val target = estimateTargets(ui)
                    if (target == null) ui.notify("기본 정보를 확인하거나 목표 없이 먼저 기록해주세요.")
                    else {
                        ui.set("target.kcal", target.kcal.toString()); ui.set("target.carbs", target.carbs.toString())
                        ui.set("target.protein", target.protein.toString()); ui.set("target.fat", target.fat.toString())
                        ui.set("target.fiber", target.fiber.toString()); ui.set("target.method", "prototype-start-0.1")
                        finishTargets(ui)
                    }
                }
            })
            UiButton("기본 정보 다시 보기", { ui.go("A04") }, primary = false)
        }
        "A08" -> {
            if(ui.accountMode){
                UiButton("시작 설정 저장하고 기록하기",account.saveProfile)
                MutedText("걸음수 연결은 추후 제공돼요.")
            }else{
                UiButton("걸음수 연동하기", { ui.notify("걸음 연동은 아직 연결되지 않았어요. 지금은 샘플 화면으로 둘러볼 수 있어요.") })
                UiButton("나중에 할게요", { ui.go("H02") }, primary = false)
            }
        }
        "H04" -> UiButton("측정값 저장", { if(ui.accountMode)account.saveBody()else saveBody(ui) },enabled=!ui.accountMode||ui.get("body.loadedDate")==bodyDate(ui).toString())
        "H07" -> UiButton("컨디션 저장", { saveCondition(ui) })
    }
}

@Composable private fun Welcome(ui: PreviewSession) {
    val account=LocalAccount.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Charcoal), contentAlignment = Alignment.Center) {
            Text("r", color = Celery, fontWeight = FontWeight.Bold, fontSize = 25.sp)
        }
        Text("루틴로그", fontWeight = FontWeight.Bold, color = Ink)
    }
    Text("매일의 기록이\n나의 루틴이 되도록.", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = Ink)
    MutedText("한 번 설정하고, 달라진 것만.\n식사와 운동, 몸의 변화를 함께 기록해요.")
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color(0xFFE0E6ED)).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        UiCard(modifier = Modifier.weight(1f)) {
            MutedText("오늘의 운동")
            Text("시티드 레그컬", fontWeight = FontWeight.SemiBold)
            HeroNumber("35", "kg")
            MutedText("12회 × 3세트")
        }
        Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(Color(0xFFE2EDD6)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            UiIcon("Check", tint = Color(0xFF406325))
            Text("차곡차곡.", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("지난주보다\n나를 더 잘 알게 돼요.", style = MaterialTheme.typography.bodyMedium)
        }
    }
    UiButton("G   Google로 계속",account.login, primary = false)
    if(account.state.userId!=null)UiButton("내 기록으로 돌아가기",account.resume,primary=false)
    UiButton("로그인 없이 둘러보기", { ui.startPreview() }, primary = false)
    Text("내 기록을 오래 보관하려면 로그인이 필요해요.", Modifier.fillMaxWidth(), color = Muted, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
}

@Composable private fun Consent(ui: PreviewSession) {
    Text("내 기록을\n안전하게 보관해요.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    MutedText(if(ui.accountMode)"계정별로 기록을 보관해요. 선택 연동은 나중에 결정할 수 있어요."else"기록은 계정별로 보관하며, 계정 설정에서 내보내거나 삭제할 수 있어요.")
    UiCard {
        ConsentRow("서비스 이용약관 동의 (필수)", ui.flag("onb.terms")) { ui.toggle("onb.terms") }
        DividerLine()
        ConsentRow("개인정보 수집·이용 동의 (필수)", ui.flag("onb.privacy")) { ui.toggle("onb.privacy") }
        if(ui.accountMode){DividerLine();ConsentRow("건강정보 수집·이용 동의 (필수)",ui.flag("onb.health")){ui.toggle("onb.health")}}
        Expandable("수집하는 정보 보기", ui, "onb.consentDetails") {
            MutedText("계정 정보와 내가 남긴 신체·식사·운동 기록을 보관해요. 걸음수는 따로 허용한 경우에 가져와요.")
            if(ui.accountMode){
                BodyText("이용 목적 · 개인 루틴과 몸의 변화를 기록하고 다시 확인하기")
                MutedText("계정 식별값, 나이·성별·키·체중, 선택한 허리둘레·목표, 생활 활동과 운동 일정, 영양 목표를 보관해요.")
                MutedText("신체 측정·식사 기록과 메모는 건강정보로 별도 동의를 받아 보관해요. 계정 삭제 시 함께 삭제하며, 동의하지 않아도 샘플은 둘러볼 수 있어요.")
                MutedText("개발 테스트용 동의 내용 · 버전 $CONSENT_VERSION. 정식 약관과 개인정보 처리방침은 출시 전에 확정해요.")
            }else MutedText("지금은 화면 체험 단계이며 실제 약관 동의나 계정 생성은 처리되지 않아요.")
        }
    }
    SoftNote("걸음수 연결과 알림은 나중에 선택할 수 있어요.")
}

@Composable private fun ConsentRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = selected, onCheckedChange = { onClick() }, colors = CheckboxDefaults.colors(checkedColor = Celery, checkmarkColor = Charcoal))
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text("*", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
    }
}

@Composable private fun Goal(ui: PreviewSession) {
    MutedText("지금 가장 중요한 목적 하나를 골라주세요.")
    listOf("근육 유지" to "지금의 근력과 운동 습관을 이어가요", "근육 증가" to "근력운동과 몸의 변화를 함께 살펴요", "체중 감량" to "체중·허리 변화와 근력 유지를 살펴요").forEach { (label, detail) ->
        Choice(label, detail, ui.get("onb.goal", "근육 증가") == label) { ui.set("onb.goal", label) }
    }
    SoftNote("지금의 목표로 시작하고, 기록을 보며 조정할 수 있어요.")
}

@Composable private fun Basics(ui: PreviewSession) {
    val imperial = ui.get("onb.units", "metric") == "imperial"
    Chips(listOf("kg · cm", "lb · ft/in"), if (imperial) "lb · ft/in" else "kg · cm") { label ->
        if (label == "lb · ft/in") {
            ui.set("onb.weightLb", decimal(number(ui, "onb.weightKg", "83.2") * 2.2046226218))
            ui.set("onb.waistIn", converted(ui.get("onb.waistCm"), 1 / 2.54))
            val inches = roundedInches(number(ui, "onb.heightCm", "178"))
            ui.set("onb.heightFt", floor(inches / 12).toInt().toString()); ui.set("onb.heightIn", decimal(inches % 12))
            ui.set("onb.targetWeightLb", converted(ui.get("onb.targetWeightKg"), 2.2046226218))
        }
        ui.set("onb.units", if (label == "lb · ft/in") "imperial" else "metric")
    }
    Input("나이 *", ui.get("onb.age", "32"), { ui.set("onb.age", it) }, suffix = "세", numeric = true)
    SectionTitle("성별 · 열량 계산용")
    Chips(listOf("남성", "여성", "응답하지 않음"), ui.get("onb.sex", "남성")) { ui.set("onb.sex", it) }
    if (imperial) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) { Input("키 · ft *", ui.get("onb.heightFt", "5"), { ui.set("onb.heightFt", it); updateHeight(ui) }, numeric = true) }
            Column(Modifier.weight(1f)) { Input("in", ui.get("onb.heightIn", "10.1"), { ui.set("onb.heightIn", it); updateHeight(ui) }, numeric = true) }
        }
        Input("현재 체중 · lb *", ui.get("onb.weightLb", "183.4"), {
            ui.set("onb.weightLb", it); ui.set("onb.weightKg", it.toDoubleOrNull()?.let { v -> decimal(v / 2.2046226218) } ?: "")
        }, numeric = true)
        Input("허리둘레 · in (선택)", ui.get("onb.waistIn"), {
            ui.set("onb.waistIn", it); ui.set("onb.waistCm", converted(it, 2.54))
        }, numeric = true)
    } else {
        Input("키 · cm *", ui.get("onb.heightCm", "178"), { ui.set("onb.heightCm", it) }, numeric = true)
        Input("현재 체중 · kg *", ui.get("onb.weightKg", "83.2"), { ui.set("onb.weightKg", it) }, numeric = true)
        Input("허리둘레 · cm (선택)", ui.get("onb.waistCm"), { ui.set("onb.waistCm", it) }, numeric = true)
    }
    MutedText("지금 재기 어렵다면 나중에 기록해도 돼요.")
    Expandable("목표 체중 추가 (선택)", ui, "onb.showTargetWeight") {
        Input("목표 체중", if (imperial) ui.get("onb.targetWeightLb") else ui.get("onb.targetWeightKg"), {
            if (imperial) { ui.set("onb.targetWeightLb", it); ui.set("onb.targetWeightKg", converted(it, 1 / 2.2046226218)) }
            else { ui.set("onb.targetWeightKg", it); ui.set("onb.targetWeightLb", converted(it, 2.2046226218)) }
        }, suffix = if (imperial) "lb" else "kg", numeric = true)
        MutedText("기한을 정하지 않아도 돼요.")
    }
    MutedText("나이·성별·키·체중은 시작 열량 계산에 사용해요.")
}

@Composable private fun Activity(ui: PreviewSession) {
    Text("운동 시간 외의\n생활을 알려주세요.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    listOf("앉아 있는 편" to "사무·학업 중심, 이동이 적어요", "이동이 잦은 편" to "서 있거나 걷는 시간이 있어요", "몸을 많이 쓰는 편" to "업무 중 지속적으로 몸을 움직여요").forEach { (label, detail) ->
        Choice(label, detail, ui.get("onb.activity", "앉아 있는 편") == label) { ui.set("onb.activity", label) }
    }
    SoftNote("처음에는 대략적인 기준으로 시작해요. 활동과 섭취 기록이 쌓이면 추세를 함께 살펴봐요.")
}

@Composable private fun Schedule(ui: PreviewSession) {
    SectionTitle("최근 2주 동안 한 운동")
    UiCard {
        BodyText("주로 한 운동")
        Chips(listOf("근력 중심", "유산소 중심", "근력 + 유산소", "잘 모르겠어요"), ui.get("onb.exerciseType", "근력 + 유산소")) { ui.set("onb.exerciseType", it) }
        BodyText("일주일에 며칠 했나요?")
        Chips(listOf("0일 · 아직 안 해요", "1일", "2일", "3일", "4일", "5일", "6일", "7일", "잘 모르겠어요"), ui.get("onb.exerciseDays", "3일")) { ui.set("onb.exerciseDays", it) }
        if (ui.get("onb.exerciseDays", "3일") != "0일 · 아직 안 해요") {
            BodyText("한 번에 보통")
            Chips(listOf("30분 미만", "30–60분", "60분 이상", "잘 모르겠어요"), ui.get("onb.exerciseDuration", "30–60분")) { ui.set("onb.exerciseDuration", it) }
            BodyText("체감 강도")
            Chips(listOf("가벼움 · 대화가 편함", "보통 · 숨이 조금 참", "높음 · 대화가 어려움", "잘 모르겠어요"), ui.get("onb.exerciseIntensity", "보통 · 숨이 조금 참")) { ui.set("onb.exerciseIntensity", it) }
        }
    }
    SectionTitle("앞으로 운동할 수 있는 날")
    UiCard {
        Chips(listOf("고정 요일", "매주 달라요"), ui.get("onb.schedule", "고정 요일")) { ui.set("onb.schedule", it) }
        if (ui.get("onb.schedule", "고정 요일") == "고정 요일") {
            val selected = ui.get("onb.availableDays", "월|화|목|금").split('|').filter { it.isNotBlank() }
            DayChoices(selected) { day -> ui.set("onb.availableDays", (if (day in selected) selected - day else selected + day).joinToString("|")) }
        } else {
            Chips((1..7).map { "주 ${it}일" }, ui.get("onb.availableCount", "주 4일")) { ui.set("onb.availableCount", it) }
            MutedText("날짜는 매주 일정에 맞게 정할 수 있어요.")
        }
        BodyText("한 번에 운동할 수 있는 시간")
        Chips(listOf("30분 미만", "30–60분", "60분 이상"), ui.get("onb.availableDuration", "30–60분")) { ui.set("onb.availableDuration", it) }
        BodyText("운동 경험")
        Chips(listOf("입문", "기초 동작 경험", "꾸준히 운동 중", "나중에 설정"), ui.get("onb.experience", "꾸준히 운동 중")) { ui.set("onb.experience", it) }
    }
    SoftNote("아직 시작하지 않았다면 0일을 골라주세요. 앞으로의 일정은 따로 정할 수 있어요.")
}

@Composable private fun StartingTargets(ui: PreviewSession) {
    val mode = ui.get("onb.targetMode", "자동으로 정하기")
    Chips(listOf("자동으로 정하기", "직접 설정"), mode) {
        ui.set("onb.targetMode", it)
        if (it == "직접 설정" && ui.get("onb.manual.kcal").isEmpty()) {
            val targets = estimateTargets(ui)
            ui.set("onb.manual.kcal", targets?.kcal?.toString() ?: "")
            ui.set("onb.manual.carbs", targets?.carbs?.toString() ?: "")
            ui.set("onb.manual.protein", targets?.protein?.toString() ?: "")
            ui.set("onb.manual.fat", targets?.fat?.toString() ?: "")
        }
    }
    if (mode == "직접 설정") {
        MutedText("이미 정해둔 목표가 있다면 입력해주세요. 매일의 식사와 함께 비교할 수 있어요.")
        UiCard {
            listOf("kcal" to "하루 열량", "carbs" to "탄수화물", "protein" to "단백질", "fat" to "지방").forEach { (key, label) ->
                Input(label, ui.get("onb.manual.$key"), { ui.set("onb.manual.$key", it) }, suffix = if (key == "kcal") "kcal" else "g", numeric = true)
            }
            val macroKcal = number(ui, "onb.manual.carbs") * 4 + number(ui, "onb.manual.protein") * 4 + number(ui, "onb.manual.fat") * 9
            KeyValue("입력한 탄단지의 열량", "${macroKcal.toInt()} kcal")
            MutedText("열량과 탄단지 합계가 다르면 입력한 목표를 한 번 더 확인해주세요.")
        }
        return
    }
    val target = estimateTargets(ui)
    if (target == null) {
        UiCard {
            SectionTitle("먼저 기본 정보를 확인해요.")
            BodyText("자동 시작값은 필요한 신체 정보가 있는 성인 기준이에요. 성별 계수가 없거나 일반 시작값을 만들기 어려운 경우에는 직접 설정하거나 목표 없이 기록할 수 있어요.")
            UiButton("기본 정보 보완", { ui.go("A04") })
            UiButton("목표 없이 기록부터 시작", {
                listOf("kcal", "carbs", "protein", "fat", "fiber").forEach { ui.set("target.$it", "") }
                ui.set("target.method", "none"); finishTargets(ui)
            }, primary = false)
        }
        return
    }
    MutedText("얼마나 먹어야 할지 몰라도 괜찮아요.\n입력한 정보를 바탕으로 시작할 기준을 준비했어요.")
    UiCard(dark = true) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("하루 시작 목표", color = Color.White.copy(alpha = .8f), style = MaterialTheme.typography.bodyMedium)
            Badge("✓ 자동 제안")
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("약 ${formatInt(target.kcal)}", color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Bold)
            Text("kcal", color = Color.White.copy(alpha = .75f), modifier = Modifier.padding(bottom = 7.dp))
        }
        Text("이 열량을 탄수화물 · 단백질 · 지방으로 나눠요.", color = Color.White.copy(alpha = .75f), style = MaterialTheme.typography.bodySmall)
        val macroTotal = (target.carbs * 4 + target.protein * 4 + target.fat * 9).toFloat()
        Row(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp))) {
            Box(Modifier.weight((target.carbs * 4f / macroTotal).coerceAtLeast(.01f)).fillMaxHeight().background(Celery))
            Box(Modifier.weight((target.protein * 4f / macroTotal).coerceAtLeast(.01f)).fillMaxHeight().background(Color(0xFFDCE3EB)))
            Box(Modifier.weight((target.fat * 9f / macroTotal).coerceAtLeast(.01f)).fillMaxHeight().background(Color(0xFF8494AC)))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MacroStat("탄수화물", target.carbs, target.carbs * 4f / macroTotal, Modifier.weight(1f))
            MacroStat("단백질", target.protein, target.protein * 4f / macroTotal, Modifier.weight(1f))
            MacroStat("지방", target.fat, target.fat * 9f / macroTotal, Modifier.weight(1f))
        }
        HorizontalDivider(color = Color.White.copy(alpha = .15f))
        Text("하루 목표 구성 · 실제 섭취와 별개예요", color = Color.White.copy(alpha = .65f), style = MaterialTheme.typography.bodySmall)
    }
    MutedText(when (ui.get("onb.goal", "근육 증가")) { "체중 감량" -> "체중 감량을 위해 유지 열량보다 조금 낮게 시작해요."; "근육 유지" -> "현재 체중과 운동 수행을 유지하는 기준으로 시작해요."; else -> "근육 증가를 위해 유지 열량보다 조금 높게 시작해요." })
    KeyValue("식이섬유 · 하루 기준", "${target.fiber} g")
    SoftNote("매일 정확히 맞추지 않아도 괜찮아요. 몇 주간의 식사와 몸의 변화를 보며 조정해요.")
    Expandable("어떤 정보를 반영했나요?", ui, "onb.inputsOpen") {
        KeyValue("목적", ui.get("onb.goal", "근육 증가"))
        KeyValue("신체", "${ui.get("onb.age", "32")}세 · ${ui.get("onb.heightCm", "178")} cm · ${ui.get("onb.weightKg", "83.2")} kg")
        KeyValue("생활 활동", ui.get("onb.activity", "앉아 있는 편"))
        KeyValue("최근 실제 운동", "${ui.get("onb.exerciseDays", "3일")} · ${ui.get("onb.exerciseType", "근력 + 유산소")}")
        KeyValue("1회 시간 · 강도", "${ui.get("onb.exerciseDuration", "30–60분")} · ${ui.get("onb.exerciseIntensity", "보통 · 숨이 조금 참").substringBefore(" · ")}")
        MutedText("최근 운동과 생활 활동으로 계산했어요. 앞으로의 운동 일정은 포함하지 않았고, 허리둘레는 몸의 변화를 비교할 때 사용해요.")
    }
    val uri = LocalUriHandler.current
    Expandable("목표를 어떻게 계산했나요?", ui, "onb.formulaOpen") {
        BodyText("1. 휴식 에너지 · Mifflin–St Jeor")
        MutedText("10 × 체중kg + 6.25 × 키cm − 5 × 나이 + 성별 계수\n남성 +5 · 여성 −161")
        KeyValue("휴식 에너지 추정", "${formatInt(target.ree)} kcal")
        BodyText("2. 생활 활동과 목표 반영")
        KeyValue("생활·최근 운동을 반영한 배수", target.pal.toString())
        KeyValue("체중 유지 열량 · 추정", "약 ${formatInt(target.maintenance)} kcal")
        KeyValue("목적별 조정", "${ui.get("onb.goal", "근육 증가")} · × ${target.factor}")
        MutedText("활동 배수 1.3–1.9를 적용하고 유지 ×1.0 · 증가 ×1.05 · 감량 ×0.9로 시작해요. 50 kcal 단위로 반올림해요.")
        BodyText("3. 탄수화물·단백질·지방 나누기")
        KeyValue("단백질 시작 기준", "${target.proteinPerKg} g / 체중kg")
        KeyValue("지방 시작 기준", "총 목표 열량의 25%")
        MutedText("나머지는 탄수화물로 배분해요. 탄수화물·단백질 4 kcal/g, 지방 9 kcal/g이며 반올림으로 작은 차이가 생길 수 있어요.")
        SoftNote("휴식 에너지 공식과 앱의 시작 정책은 구분해요. 활동 구간·목적별 조정·영양 배분은 검토 중인 시안 기준이며 개인에게 검증된 처방값은 아니에요. 걸음과 운동 열량을 다시 더하지 않아요.")
        UiButton("Mifflin–St Jeor 원문 · 1990 ↗", { runCatching { uri.openUri("https://pubmed.ncbi.nlm.nih.gov/2305711/") }.onFailure { ui.notify("링크를 열 수 있는 브라우저가 없어요.") } }, primary = false)
    }
}

@Composable private fun MacroStat(label: String, grams: Int, fraction: Float, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = Color.White.copy(alpha = .8f), style = MaterialTheme.typography.bodySmall)
        Text("${grams}g", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("열량의 ${roundInt(fraction * 100.0)}%", color = Color.White.copy(alpha = .65f), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable private fun StepConnection(ui: PreviewSession) {
    Text("생활 속 움직임도\n루틴의 일부예요.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    MutedText("휴대폰에 모인 걸음을 가져와\n내 루틴과 함께 살펴봐요.")
    UiCard {
        SectionTitle("주간 걸음의 변화")
        BarChart(listOf(4800f, 6800f, 5400f, 7800f, 6200f, 9000f, 5600f), weekDays)
        MutedText("연동하면 내 걸음 기록이 이곳에 표시돼요.")
    }
    UiCard {
        KeyValue("가져오는 정보", "일별 걸음 수")
        KeyValue("함께 보는 기록", "식단 · 운동 · 몸의 변화")
        KeyValue("연동 선택", "나중에 변경 가능")
    }
    SoftNote("권한을 허용하지 않아도 기록할 수 있어요.\nGPS를 켜고 계속 위치를 추적하지 않아요.")
}

@Composable private fun Today(ui: PreviewSession) {
    DateWeek(ui)
    SectionTitle("오늘의 루틴", "1 / 3 기록") { ui.go("H03") }
    UiCard(dark = true) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Badge("진행 중")
                Text("하체 A", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("시티드 레그컬 · 2세트 남음", color = Color(0xFFCDD5DF), style = MaterialTheme.typography.bodyMedium)
            }
            Box(Modifier.size(72.dp).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = .07f)).border(1.dp, Color.White.copy(alpha = .17f), RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
                UiIcon("Dumbbell", modifier = Modifier.size(40.dp), tint = Color(0xFFDCE4EE))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(5) { index -> Box(Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(when { index < 2 -> Celery; index == 2 -> Color(0xFFCED6E0); else -> Color(0xFF5D6877) })) }
        }
        UiButton("운동 이어하기", { ui.go("W08") })
    }
    val today = selectedHomeDate(ui)
    val recordedWeight = ui.get("body.${today}.weight")
    val imperial = ui.get("onb.units", "metric") == "imperial"
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        UiCard(modifier = Modifier.weight(1f).clickable { prepareBody(ui, today); ui.go("H04") }) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) { UiIcon("Scale", Modifier.size(18.dp)); MutedText("체중 · 허리") }
            Text(if (imperial) "${converted(recordedWeight.ifBlank { "83.2" }, 2.2046226218)} lb" else "${recordedWeight.ifBlank { "83.2" }} kg", fontSize = 25.sp, fontWeight = FontWeight.Bold)
            MutedText(if (recordedWeight.isNotBlank()) "측정 기록 완료" else "직전 기록 · 측정 전")
        }
        UiCard(modifier = Modifier.weight(1f).clickable { ui.go("H06") }) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) { UiIcon("Footprints", Modifier.size(18.dp)); MutedText("오늘 걸음") }
            Text("6,240", fontSize = 25.sp, fontWeight = FontWeight.Bold)
            MutedText("걸음 · 샘플 기록")
        }
    }
    SectionTitle("오늘 식사", "하루 보기") { ui.go("F01") }
    ui.get("food.slots", "아침|점심|저녁").split('|').filter { it.isNotBlank() }.forEach { meal ->
        val done = ui.flag("food.done.$meal", meal == "아침")
        UiCard { UiRow(meal, if (done) "기록 완료" else "${ui.get("food.meal.$meal", if (meal == "아침") "기본 아침" else "점심 기본 세트")} · 확인 전", icon = if (done) "CircleCheck" else "Utensils", selected = done, onClick = { ui.set("food.activeMeal", meal); ui.go("F03") }) }
    }
    UiCard { UiRow("지난주 리포트", "다음 주 제안 1건", icon = "ChartNoAxesCombined", onClick = { ui.go("R01") }) }
    UiCard { UiRow(if (today == ui.today()) "오늘의 컨디션" else "이날의 컨디션", if (ui.flag("condition.$today.saved")) savedConditionSummary(ui, today) else "피로 · 수면 · 근육통 · 짧은 메모", icon = "HeartPulse", onClick = { ui.go("H07") }) }
}

@Composable private fun FirstHome(ui: PreviewSession) {
    DateWeek(ui)
    Badge("${ui.get("onb.goal", "근육 증가")}를 위한 첫 기록이에요.")
    Text("매일 바뀌는 것만\n남길 수 있도록.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    SectionTitle("처음 한 번만")
    UiCard {
        SectionTitle("01　기본 식단 저장")
        MutedText("내 식사 횟수에 맞춰 자주 먹는 음식을 저장해요.")
        UiButton("식단 만들기", { ui.go("F02") })
    }
    UiCard {
        SectionTitle("02　일주일 운동 정하기")
        MutedText("내 루틴을 만들거나 프로그램을 골라요.")
        UiButton("운동 루틴 만들기", { ui.go("W01") })
    }
    UiRow("오늘 체중 · 허리 먼저 기록", icon = "Scale", onClick = { prepareBody(ui, ui.today()); ui.go("H04") })
    UiRow("걸음수 연동 확인", icon = "Footprints", onClick = { ui.go("S03") })
}

@Composable private fun DateWeek(ui: PreviewSession) {
    val date = selectedHomeDate(ui)
    val monday = date.minusDays((date.dayOfWeek.value - 1).toLong())
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(7) { index ->
            val day = monday.plusDays(index.toLong())
            val active = day == date
            Column(Modifier.weight(1f).heightIn(min = 72.dp).clip(RoundedCornerShape(12.dp)).background(if (active) Charcoal else Color.White).border(1.dp, if (active) Charcoal else Border, RoundedCornerShape(12.dp)).clickable { ui.set("home.date", day.toString()) }.semantics { contentDescription = "${day.monthValue}월 ${day.dayOfMonth}일 ${weekDays[index]}요일"; selected = active }.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(weekDays[index], color = if (active) Color.White.copy(alpha = .7f) else Muted, fontSize = 11.sp)
                Text(day.dayOfMonth.toString(), color = if (active) Color.White else Ink, fontWeight = FontWeight.Bold)
                Text(if (day == ui.today()) "•" else if (index < 2) "✓" else " ", color = if (active) Celery else Color(0xFF507B31), fontSize = 11.sp)
            }
        }
    }
}

@Composable private fun RecordCalendar(ui: PreviewSession) {
    val today = ui.today()
    val month = runCatching { YearMonth.parse(ui.get("home.calendarMonth", YearMonth.from(selectedHomeDate(ui)).toString())) }.getOrDefault(YearMonth.from(today))
    val account=LocalAccount.current
    LaunchedEffect(month,ui.accountMode) {
        if(ui.accountMode && month.atDay(1)<=today && month.atEndOfMonth()>=earliestRecordDate)
            account.loadRange(month.atDay(1).coerceAtLeast(earliestRecordDate).toString(),month.atEndOfMonth().coerceAtMost(today).toString())
    }
    UiCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { ui.set("home.calendarMonth", month.minusMonths(1).toString()) }, modifier = Modifier.semantics { contentDescription = "이전 달" }) { UiIcon("ChevronLeft") }
            Text("${month.year}년 ${month.monthValue}월", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            IconButton(onClick = { ui.set("home.calendarMonth", month.plusMonths(1).toString()) }, modifier = Modifier.semantics { contentDescription = "다음 달" }) { UiIcon("ChevronRight") }
        }
        Row { weekDays.forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, color = Muted, fontSize = 12.sp) } }
        val offset = month.atDay(1).dayOfWeek.value - 1
        val weeks = (offset + month.lengthOfMonth() + 6) / 7
        repeat(weeks) { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(7) { weekday ->
                    val day = week * 7 + weekday - offset + 1
                    if (day !in 1..month.lengthOfMonth()) Spacer(Modifier.weight(1f).height(48.dp))
                    else {
                        val date = month.atDay(day)
                        val selected = date == selectedHomeDate(ui)
                        val hasRecord = ui.get("body.$date.weight").isNotBlank() || ui.get("body.$date.waist").isNotBlank()
                        val fill = when { selected -> Charcoal; hasRecord -> Celery; !ui.accountMode && date < today && day % 3 == 0 -> Color(0xFFE3EDD9); else -> Color(0xFFF3F5F7) }
                        Box(Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(10.dp)).background(fill).clickable(enabled=!ui.accountMode||date in earliestRecordDate..today) { ui.set("home.date", date.toString()) }.semantics { contentDescription = "${date.year}년 ${date.monthValue}월 ${date.dayOfMonth}일"; this.selected = selected }, contentAlignment = Alignment.Center) {
                            Text(day.toString(), color = if (selected) Color.White else Ink, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }
        }
        MutedText(if(ui.accountMode)"■ 측정 기록 있음　□ 미기록"else"■ 기록 완료　▧ 일부 기록　□ 미기록　┄ 휴식")
    }
    val selected = selectedHomeDate(ui)
    UiCard {
        SectionTitle("${selected.monthValue}월 ${selected.dayOfMonth}일")
        if(!ui.accountMode){KeyValue("식사", "2 / ${ui.get("food.slots", "아침|점심|저녁").split('|').size}끼 기록");KeyValue("운동", "하체 A · 일부 수행")}
        KeyValue("신체", when { ui.get("body.$selected.weight").isNotBlank() && ui.get("body.$selected.waist").isNotBlank() -> "체중 · 허리 기록 완료"; ui.get("body.$selected.waist").isNotBlank() -> "허리 기록 · 체중 미기록"; ui.get("body.$selected.weight").isNotBlank() -> "체중 기록 · 허리 미기록"; else->"아직 기록 전" })
        UiButton("이 날짜 기록 보기", { ui.go("H01") })
    }
    SoftNote("계획과 달라도 괜찮아요. 먹고 운동한 만큼 기록하면 돼요.")
}

@Composable private fun BodyEntry(ui: PreviewSession) {
    val date = bodyDate(ui)
    val account=LocalAccount.current
    LaunchedEffect(date,ui.accountMode){if(ui.accountMode){ui.set("body.loadedDate","");account.loadDate(date.toString())}}
    val imperial = if(ui.accountMode)account.state.profile?.units=="IMPERIAL"else ui.get("onb.units", "metric") == "imperial"
    UiCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                MutedText("측정일")
                Text("${if (date == ui.today()) "오늘 · " else ""}${date.monthValue}월 ${date.dayOfMonth}일", fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = { ui.toggle("body.dateOpen") }) { Text(if (ui.flag("body.dateOpen")) "접기" else "날짜 변경") }
        }
        if (ui.flag("body.dateOpen")) {
            Chips(listOf("오늘", "어제", "직접 선택"), ui.get("body.dateChoice", if (date == ui.today()) "오늘" else "직접 선택")) {
                ui.set("body.dateChoice", it)
                when (it) { "오늘" -> prepareBody(ui, ui.today()); "어제" -> prepareBody(ui, ui.today().minusDays(1)) }
            }
            if (ui.get("body.dateChoice") == "직접 선택") {
                Input("측정한 날짜", ui.get("body.dateText", date.toString()), { ui.set("body.dateText", it) })
                MutedText("예: ${ui.today()} · 연도-월-일 순서")
                UiButton("이 날짜로 변경", {
                    val chosen = runCatching { LocalDate.parse(ui.get("body.dateText")) }.getOrNull()
                    if (chosen == null || chosen !in earliestRecordDate..ui.today()) ui.notify("1900년부터 오늘까지의 날짜를 연도-월-일로 입력해주세요.") else prepareBody(ui, chosen)
                }, primary = false)
            }
        }
    }
    Input(if (imperial) "체중 · lb" else "체중 · kg", if (imperial) ui.get("body.draftWeightLb", converted(ui.get("body.draftWeight"), 2.2046226218)) else ui.get("body.draftWeight"), {
        if (imperial) { ui.set("body.draftWeightLb", it); ui.set("body.draftWeight", converted(it, 1 / 2.2046226218)) }
        else { ui.set("body.draftWeight", it); ui.set("body.draftWeightLb", converted(it, 2.2046226218)) }
        ui.set("body.error", "")
    }, numeric = true)
    if(!ui.accountMode)MutedText("직전 기록 ${if (imperial) "183.4 lb" else "83.2 kg"} · ${date.minusDays(1).monthValue}월 ${date.minusDays(1).dayOfMonth}일")
    else account.state.records.firstOrNull{it.date<date.toString()&&it.weightKg!=null}?.let{MutedText("이전 기록 ${liveNumber(it.weightKg!!*if(imperial)2.2046226218 else 1.0)} ${if(imperial)"lb"else"kg"} · ${it.date}")}
    Input(if (imperial) "허리둘레 · in" else "허리둘레 · cm", if (imperial) ui.get("body.draftWaistIn", converted(ui.get("body.draftWaist"), 1 / 2.54)) else ui.get("body.draftWaist"), {
        if (imperial) { ui.set("body.draftWaistIn", it); ui.set("body.draftWaist", converted(it, 2.54)) }
        else { ui.set("body.draftWaist", it); ui.set("body.draftWaistIn", converted(it, 1 / 2.54)) }
        ui.set("body.error", "")
    }, numeric = true)
    if(!ui.accountMode)MutedText("직전 기록 ${if (imperial) "31.5 in" else "80.0 cm"} · ${date.minusDays(1).monthValue}월 ${date.minusDays(1).dayOfMonth}일")
    else account.state.records.firstOrNull{it.date<date.toString()&&it.waistCm!=null}?.let{MutedText("이전 기록 ${liveNumber(it.waistCm!!/if(imperial)2.54 else 1.0)} ${if(imperial)"in"else"cm"} · ${it.date}")}
    Input("측정 메모 · 선택", ui.get("body.draftMemo"), { ui.set("body.draftMemo", it) }, multiline = true)
    MutedText("예: 기상 후, 식사 전")
    if (ui.get("body.error").isNotBlank()) Text(ui.get("body.error"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
    SoftNote("측정한 항목만 남겨주세요.\n둘 중 하나만 입력해도 저장할 수 있어요.")
    UiRow("이전 기록 · 추세 보기", icon = "ChartNoAxesCombined", onClick = { ui.go("H05") })
    if(ui.accountMode&&ui.get("body.draftVersion").isNotBlank()){
        UiButton("이 날짜의 측정 기록 삭제",{ui.set("body.deleteConfirm","true")},primary=false)
        if(ui.flag("body.deleteConfirm"))AlertDialog(onDismissRequest={ui.set("body.deleteConfirm","false")},
            title={Text("측정 기록을 삭제할까요?")},text={Text("${date}의 체중·허리둘레와 메모를 삭제해요.")},
            confirmButton={TextButton(onClick={ui.set("body.deleteConfirm","false");account.deleteBody()}){Text("삭제",color=MaterialTheme.colorScheme.error)}},
            dismissButton={TextButton(onClick={ui.set("body.deleteConfirm","false")}){Text("취소")}})
    }
}

@Composable private fun BodyHistory(ui: PreviewSession) {
    val waist = ui.get("body.metric", "체중") == "허리둘레"
    val imperial = ui.get("onb.units", "metric") == "imperial"
    val unit = if (waist) if (imperial) "in" else "cm" else if (imperial) "lb" else "kg"
    val factor = if (imperial) if (waist) 1 / 2.54 else 2.2046226218 else 1.0
    Chips(listOf("체중", "허리둘레"), if (waist) "허리둘레" else "체중") { ui.set("body.metric", it) }
    Chips(listOf("주간", "월간", "연간"), ui.get("body.period", "주간")) { ui.set("body.period", it) }
    val period = ui.get("body.period", "주간")
    val sample = if (waist) when (period) { "월간" -> listOf(81.3f, 81.0f, 80.8f, 80.2f, 80f); "연간" -> listOf(84f, 83f, 82.4f, 81.2f, 80.8f, 80f); else -> listOf(80.4f, 80.2f, 80.3f, 80.1f, 80f, 80f) }
    else when (period) { "월간" -> listOf(84f, 83.8f, 83.5f, 83.6f, 83.2f); "연간" -> listOf(87f, 85.5f, 84.8f, 84.4f, 83.8f, 83.2f); else -> listOf(83.7f, 83.6f, 83.4f, 83.5f, 83.3f, 83.2f) }
    UiCard {
        MutedText("${if (period == "주간") "지난주" else if (period == "월간") "이번 달" else "올해"} 평균 · ${if (waist) "허리둘레" else "체중"}")
        HeroNumber(decimal((if (waist) 80.2 else 83.3) * factor), unit)
        MutedText("${decimal(sample.first() * factor)} → ${decimal(sample.last() * factor)} $unit")
        LineChart(sample.map { (it * factor).toFloat() }, Modifier.fillMaxWidth().height(160.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            (if (period == "주간") listOf("월", "수", "금", "일") else if (period == "월간") listOf("1주", "2주", "3주", "4주") else listOf("1월", "4월", "8월", "12월")).forEach { MutedText(it) }
        }
        MutedText("기록된 값 기준 · 샘플 추세")
    }
    val recorded = ui.values.keys.filter { it.startsWith("body.") && it.endsWith(if (waist) ".waist" else ".weight") }.mapNotNull { key ->
        val day = runCatching { LocalDate.parse(key.removePrefix("body.").substringBefore('.')) }.getOrNull()
        if (day == null || ui.get(key).isBlank()) null else day to ui.get(key)
    }.sortedByDescending { it.first }
    UiCard {
        KeyValue("날짜", "${if (waist) "허리둘레" else "체중"} $unit")
        val rows = recorded.ifEmpty { listOf(ui.today().minusDays(1) to if (waist) "80.0" else "83.2", ui.today().minusDays(2) to if (waist) "80.2" else "83.3") }
        rows.take(14).forEach { (date, value) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(date.format(DateTimeFormatter.ofPattern("MM.dd")), Modifier.weight(1f), color = Muted)
                Text(converted(value, factor), Modifier.weight(1f), fontWeight = FontWeight.Bold)
                TextButton(onClick = {
                    prepareBody(ui, date)
                    if (recorded.isEmpty()) {
                        ui.set(if (waist) "body.draftWaist" else "body.draftWeight", value)
                        ui.set(if (waist) "body.draftWaistIn" else "body.draftWeightLb", converted(value, if (waist) 1 / 2.54 else 2.2046226218))
                    }
                    ui.go("H04")
                }) { Text("수정") }
            }
        }
    }
    SoftNote("하루의 숫자보다 여러 주의 흐름을 함께 봐요. 체중·허리 기록만으로 근육과 지방의 변화를 알 수는 없어요.")
}

@Composable private fun Steps(ui: PreviewSession) {
    UiCard {
        val date = selectedHomeDate(ui)
        MutedText("${date.monthValue}월 ${date.dayOfMonth}일 · ${if (date == ui.today()) "오늘" else "선택한 날"}")
        HeroNumber("6,240", "걸음")
        MutedText("샘플 걸음 기록 · 건강 데이터 연동 전")
        UiButton("새 기록 확인", { ui.notify("건강 데이터 연결 전이라 새 걸음을 가져올 수 없어요. 현재 수치는 샘플이에요.") }, primary = false)
    }
    UiCard {
        SectionTitle("이번 주")
        BarChart(listOf(6800f, 7200f, 6240f, 0f, 0f, 0f, 0f), weekDays)
        MutedText("오늘까지 일평균 6,747걸음 · 3일 확인")
    }
    SoftNote("휴대폰을 두고 움직인 시간은 빠질 수 있어요. 걸음만으로 정확한 소비 열량을 알 수는 없어요.")
    UiRow("연동 · 권한 관리", icon = "Settings2", onClick = { ui.go("S03") })
}

@Composable private fun Condition(ui: PreviewSession) {
    val date = selectedHomeDate(ui)
    LaunchedEffect(date) {
        if (ui.get("condition.draftDate") != date.toString()) {
            conditionFields.forEach { ui.set("condition.$it", ui.get("condition.$date.$it")) }
            ui.set("condition.draftDate", date.toString())
        }
    }
    if (date != ui.today()) MutedText("${date.year}년 ${date.monthValue}월 ${date.dayOfMonth}일의 컨디션")
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xFFE4EDD9)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text("오늘의 상태", color = Color(0xFF486A30), style = MaterialTheme.typography.labelMedium)
        Text(if (ui.get("condition.fatigue").isBlank() && ui.get("condition.sleep").isBlank()) "기억하고 싶은 항목만 남겨주세요." else conditionSummary(ui), fontWeight = FontWeight.Bold)
        MutedText("모든 항목을 채우지 않아도 돼요.")
    }
    ConditionChoice("피로", "지금 느끼는 정도", "BatteryFull", listOf("낮음", "보통", "높음"), "condition.fatigue", ui)
    UiCard {
        ConditionHeading("수면", "지난밤 기준", "Moon")
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Silver).padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedIconButton(onClick = { ui.set("condition.sleep", decimal(((ui.get("condition.sleep").toDoubleOrNull() ?: 7.0) - .5).coerceAtLeast(0.0))) }, modifier = Modifier.size(48.dp).semantics { contentDescription = "수면 시간 30분 줄이기" }) { UiIcon("Minus") }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("지난밤 수면 시간", style = MaterialTheme.typography.labelSmall, color = Muted)
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(ui.get("condition.sleep").ifBlank { "—" }, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    Text("시간", modifier = Modifier.padding(bottom = 5.dp), color = Muted)
                }
            }
            OutlinedIconButton(onClick = { ui.set("condition.sleep", decimal(((ui.get("condition.sleep").toDoubleOrNull() ?: 6.0) + .5).coerceAtMost(24.0))) }, modifier = Modifier.size(48.dp).semantics { contentDescription = "수면 시간 30분 늘리기" }) { UiIcon("Plus") }
        }
        Chips(listOf("6시간", "7시간", "8시간"), ui.get("condition.sleep").let { if (it.endsWith(".0")) "${it.substringBefore('.')}시간" else "${it}시간" }) { ui.set("condition.sleep", it.removeSuffix("시간") + ".0") }
        if (ui.get("condition.sleep").isNotBlank()) TextButton(onClick = { ui.set("condition.sleep", "") }) { Text("수면 기록 지우기") }
    }
    ConditionChoice("근육통", "느껴지는 정도", "Activity", listOf("없음", "조금", "많음"), "condition.soreness", ui)
    if (ui.get("condition.soreness") in listOf("조금", "많음")) {
        Input("어느 부위가 뻐근했나요? · 선택", ui.get("condition.sorenessArea"), { ui.set("condition.sorenessArea", it) })
    }
    ConditionChoice("스트레스", "오늘 하루", "Wind", listOf("낮음", "보통", "높음"), "condition.stress", ui)
    UiCard {
        ConditionHeading("짧은 메모", "선택", "PencilLine")
        Input("기억하고 싶은 변화", ui.get("condition.memo"), { ui.set("condition.memo", it) }, multiline = true)
    }
    UiRow("운동 중 불편함 기록하기", icon = "MessageCircle", onClick = { ui.go("W13") })
    MutedText("내가 느낀 상태를 남겨요. 회복 정도를 정확히 측정하는 기록은 아니에요.")
}

@Composable private fun ConditionChoice(title: String, helper: String, icon: String, options: List<String>, key: String, ui: PreviewSession) {
    UiCard {
        ConditionHeading(title, helper, icon)
        Chips(options, ui.get(key)) {
            val choice = if (ui.get(key) == it) "" else it
            ui.set(key, choice)
            if (key == "condition.soreness" && choice !in listOf("조금", "많음")) ui.set("condition.sorenessArea", "")
        }
    }
}

@Composable private fun ConditionHeading(title: String, helper: String, icon: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        UiIcon(icon, Modifier.size(22.dp))
        Text(title, Modifier.weight(1f), fontWeight = FontWeight.Bold)
        Text(helper, style = MaterialTheme.typography.labelSmall, color = Muted)
    }
}

@Composable private fun Expandable(title: String, ui: PreviewSession, key: String, content: @Composable ColumnScope.() -> Unit) {
    UiCard {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { ui.toggle(key) }, verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            UiIcon(if (ui.flag(key)) "ChevronUp" else "ChevronDown", Modifier.size(20.dp))
        }
        if (ui.flag(key)) content()
    }
}

@Composable private fun SoftNote(text: String) {
    Text(text, Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFE3E9F0)).padding(16.dp), color = Muted, style = MaterialTheme.typography.bodyMedium)
}

@Composable private fun DayChoices(selected: List<String>, onClick: (String) -> Unit) {
    // Two rows preserve a 48dp touch target even with 320dp screens and large fonts.
    weekDays.chunked(4).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { day ->
                FilterChip(selected = day in selected, onClick = { onClick(day) }, label = { Text(day) }, modifier = Modifier.heightIn(min = 48.dp), colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Celery, selectedLabelColor = Charcoal))
            }
        }
    }
}

private val weekDays = listOf("월", "화", "수", "목", "금", "토", "일")
private val earliestRecordDate: LocalDate = LocalDate.of(1900, 1, 1)
private val conditionFields = listOf("fatigue", "sleep", "soreness", "sorenessArea", "stress", "memo")
private fun decimal(value: Double) = String.format(Locale.US, "%.1f", value)
private fun converted(value: String, factor: Double) = value.toDoubleOrNull()?.takeIf { it.isFinite() }?.let { decimal(it * factor) } ?: value
private fun roundedInches(cm: Double) = roundInt(cm / 2.54 * 10) / 10.0
private fun formatInt(value: Int) = String.format(Locale.US, "%,d", value)
private fun roundInt(value: Double) = floor(value + .5).toInt()
private fun number(ui: PreviewSession, key: String, fallback: String = "0") = ui.get(key, fallback).toDoubleOrNull() ?: 0.0
private fun selectedHomeDate(ui: PreviewSession) = runCatching { LocalDate.parse(ui.get("home.date", ui.today().toString())) }.getOrDefault(ui.today())
private fun bodyDate(ui: PreviewSession) = runCatching { LocalDate.parse(ui.get("body.date", ui.today().toString())) }.getOrDefault(ui.today()).coerceAtLeast(earliestRecordDate)
private fun finishTargets(ui: PreviewSession) {
    val returnRoute = ui.get("onboardingReturnRoute")
    if (returnRoute.isNotBlank()) { ui.set("onboardingReturnRoute", ""); if(ui.accountMode)ui.go(returnRoute)else ui.save(returnRoute) }
    else ui.go("A08")
}
private fun updateHeight(ui: PreviewSession) {
    val feet = ui.get("onb.heightFt", "5").toDoubleOrNull()
    val inches = ui.get("onb.heightIn", "10.1").toDoubleOrNull()
    ui.set("onb.heightCm", if (feet != null && inches != null && feet.isFinite() && inches.isFinite() && feet >= 0 && feet % 1.0 == 0.0 && inches >= 0 && inches < 12) decimal((feet * 12 + inches) * 2.54) else "")
}
private fun basicsError(ui: PreviewSession): String? {
    val age = ui.get("onb.age", "32").toIntOrNull()
    val height = number(ui, "onb.heightCm", "178")
    val weight = number(ui, "onb.weightKg", "83.2")
    val waist = ui.get("onb.waistCm").toDoubleOrNull()
    // Technical input bounds, not medical recommendations.
    return when {
        age == null || age !in (if(ui.accountMode)19..120 else 1..120) -> if(ui.accountMode)"현재 가입은 만 19~120세 범위에서 지원해요."else"나이를 1~120세 사이의 숫자로 입력해주세요."
        !height.isFinite() || height <= 0 || height > 300 -> "키를 0보다 크고 300cm 이하로 입력해주세요."
        !weight.isFinite() || weight <= 0 || weight > 1000 -> "체중을 0보다 크고 1,000kg 이하로 입력해주세요."
        ui.get("onb.waistCm").isNotBlank() && (waist == null || !waist.isFinite() || waist <= 0 || waist > 500) -> "허리둘레를 확인하거나 빈칸으로 남겨주세요."
        ui.get("onb.targetWeightKg").isNotBlank() && (ui.get("onb.targetWeightKg").toDoubleOrNull()?.let { !it.isFinite() || it <= 0 || it > 1000 } ?: true) -> "목표 체중을 확인하거나 빈칸으로 남겨주세요."
        else -> null
    }
}
internal fun prepareBody(ui: PreviewSession, date: LocalDate) {
    ui.set("body.date", date.toString()); ui.set("body.dateText", date.toString())
    ui.set("body.dateChoice", when (date) { ui.today() -> "오늘"; ui.today().minusDays(1) -> "어제"; else -> "직접 선택" })
    ui.set("body.draftWeight", ui.get("body.$date.weight")); ui.set("body.draftWaist", ui.get("body.$date.waist"))
    ui.set("body.draftWeightLb", converted(ui.get("body.$date.weight"), 2.2046226218))
    ui.set("body.draftWaistIn", converted(ui.get("body.$date.waist"), 1 / 2.54))
    ui.set("body.draftMemo", ui.get("body.$date.memo")); ui.set("body.error", "")
    ui.set("body.draftVersion",ui.get("body.$date.version"))
}
private fun saveBody(ui: PreviewSession) {
    val date = bodyDate(ui)
    if (date !in earliestRecordDate..ui.today()) { ui.notify("1900년부터 오늘까지의 측정값을 기록해주세요."); return }
    val result = BodyMeasurementInput.validate(date, ui.get("body.draftWeight"), ui.get("body.draftWaist"))
    val measurement = result.measurement
    if (measurement == null) { ui.set("body.error", listOfNotNull(result.formError, result.weightError, result.waistError).joinToString("\n")); return }
    ui.set("body.$date.weight", measurement.weightKg?.toPlainString() ?: "")
    ui.set("body.$date.waist", measurement.waistCm?.toPlainString() ?: "")
    ui.set("body.$date.memo", ui.get("body.draftMemo")); ui.set("home.date", date.toString())
    ui.save("H01")
}
private fun saveCondition(ui: PreviewSession) {
    val date = selectedHomeDate(ui)
    if (date !in earliestRecordDate..ui.today()) { ui.notify("1900년부터 오늘까지의 컨디션을 기록해주세요."); return }
    conditionFields.forEach { ui.set("condition.$date.$it", ui.get("condition.$it")) }
    ui.set("condition.$date.saved", conditionFields.any { ui.get("condition.$it").isNotBlank() }.toString())
    ui.set("condition.date", date.toString())
    ui.save("H01")
}
private fun savedConditionSummary(ui: PreviewSession, date: LocalDate) = listOfNotNull(
    ui.get("condition.$date.fatigue").takeIf { it.isNotBlank() }?.let { "피로 $it" },
    ui.get("condition.$date.sleep").takeIf { it.isNotBlank() }?.let { "수면 ${it}시간" },
    ui.get("condition.$date.soreness").takeIf { it.isNotBlank() }?.let { "근육통 $it" }
).joinToString(" · ").ifBlank { "오늘의 상태를 남겼어요." }
private fun conditionSummary(ui: PreviewSession): String = listOfNotNull(
    ui.get("condition.fatigue").takeIf { it.isNotBlank() }?.let { "피로 $it" },
    ui.get("condition.sleep").takeIf { it.isNotBlank() }?.let { "수면 ${it}시간" },
    ui.get("condition.soreness").takeIf { it.isNotBlank() }?.let { "근육통 $it" }
).joinToString(" · ").ifBlank { if (ui.get("condition.memo").isNotBlank()) "오늘의 메모를 남겼어요." else "기억하고 싶은 항목만 남겨주세요." }

private data class StartTarget(val kcal: Int, val carbs: Int, val protein: Int, val fat: Int, val fiber: Int, val ree: Int, val maintenance: Int, val pal: Double, val factor: Double, val proteinPerKg: Double)

/** Port of design-reference/nutrition-start.js. PAL and goal factors remain unvalidated prototype policy. */
private fun estimateTargets(ui: PreviewSession): StartTarget? {
    val age = number(ui, "onb.age", "32")
    val weight = number(ui, "onb.weightKg", "83.2")
    val height = number(ui, "onb.heightCm", "178")
    val sex = ui.get("onb.sex", "남성")
    if (!age.isFinite() || !weight.isFinite() || !height.isFinite() || age < 18 || age > 120 || weight <= 0 || weight > 1000 || height <= 0 || height > 300 || sex !in listOf("남성", "여성")) return null
    val ree = 10 * weight + 6.25 * height - 5 * age + if (sex == "남성") 5 else -161
    val days = ui.get("onb.exerciseDays", "3일").takeWhile { it.isDigit() }.toIntOrNull() ?: 0
    val duration = when (ui.get("onb.exerciseDuration", "30–60분")) { "30분 미만" -> 20; "30–60분" -> 45; "60분 이상" -> 60; else -> 0 }
    val intensity = when (ui.get("onb.exerciseIntensity", "보통 · 숨이 조금 참")) { "가벼움 · 대화가 편함" -> .8; "높음 · 대화가 어려움" -> 1.2; else -> 1.0 }
    val score = days * duration * intensity
    val tier = if (score >= 240) 2 else if (score >= 90) 1 else 0
    val pal = when (ui.get("onb.activity", "앉아 있는 편")) { "이동이 잦은 편" -> listOf(1.45, 1.6, 1.75); "몸을 많이 쓰는 편" -> listOf(1.6, 1.75, 1.9); else -> listOf(1.3, 1.45, 1.6) }[tier]
    val goal = ui.get("onb.goal", "근육 증가")
    val factor = when (goal) { "근육 증가" -> 1.05; "체중 감량" -> .9; else -> 1.0 }
    val maintenance = ree * pal
    val kcal = roundInt(maintenance * factor / 50) * 50
    val training = days > 0 && ui.get("onb.exerciseType", "근력 + 유산소") != "잘 모르겠어요"
    val strength = training && ui.get("onb.exerciseType", "근력 + 유산소") in listOf("근력 중심", "근력 + 유산소") || goal in listOf("근육 유지", "근육 증가")
    val proteinRate = if (strength) 1.6 else if (training) 1.4 else .8
    val protein = roundInt(weight * proteinRate / 5) * 5
    val fat = roundInt(kcal * .25 / 9)
    val carbs = roundInt((kcal - protein * 4 - fat * 9) / 4.0)
    val fiber = if (sex == "남성") if (age <= 50) 38 else 30 else if (age <= 50) 25 else 21
    if (ree <= 0 || kcal <= 0 || carbs < 0 || protein * 4 > kcal * .35) return null
    return StartTarget(kcal, carbs, protein, fat, fiber, roundInt(ree), roundInt(maintenance / 50) * 50, pal, factor, proteinRate)
}
