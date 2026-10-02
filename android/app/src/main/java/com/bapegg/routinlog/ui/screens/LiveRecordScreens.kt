package com.bapegg.routinlog.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.bapegg.routinlog.data.RecordCalendar
import com.bapegg.routinlog.data.RecordDay
import com.bapegg.routinlog.ui.*
import com.bapegg.routinlog.ui.theme.*
import kotlinx.coroutines.awaitCancellation
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable internal fun LiveRecordScreens(id: String, ui: PreviewSession, model: RecordCalendarViewModel,
    meals: MealViewModel? = null, workouts: WorkoutViewModel? = null) {
    val state by model.state.collectAsStateWithLifecycle()
    val today = ui.today()
    val selected = runCatching { LocalDate.parse(ui.get("home.date", today.toString())) }.getOrDefault(today).coerceIn(LocalDate.of(1900,1,1), today)
    val month = if (id == "H03") runCatching { YearMonth.parse(ui.get("home.calendarMonth", YearMonth.from(selected).toString())) }
        .getOrDefault(YearMonth.from(selected)).coerceIn(YearMonth.of(1900,1), YearMonth.from(today)) else YearMonth.from(selected)
    val lifecycle = LocalLifecycleOwner.current
    LaunchedEffect(id, month, state.owner, lifecycle) {
        if (state.owner != null) lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            model.select(month.atDay(1).toString(), refresh = true)
            awaitCancellation()
        }
    }
    val calendar = state.calendar?.takeIf { it.month == month.atDay(1).toString() }
    val mealState = meals?.state?.collectAsStateWithLifecycle()?.value
    val workoutState = workouts?.state?.collectAsStateWithLifecycle()?.value
    val working = mealState?.busy == true || workoutState?.busy == true
    var confirmDate by remember { mutableStateOf<String?>(null) }
    fun openMeals(date: String) {
        if (working) return
        if (mealState?.draft != null && mealState.date != date) confirmDate = date
        else { if (mealState?.date != date) meals?.loadDate(date); ui.go("F01") }
    }
    if (id == "H03") {
        CalendarGrid(month, selected, today, calendar, onMonth = { next ->
            ui.set("home.calendarMonth", next.toString()); ui.set("home.date", minOf(next.atDay(1), today).toString())
        }, onSelect = { ui.set("home.date", it.toString()) })
    } else {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                SectionTitle(if (selected == today) "오늘의 기록" else "이날의 기록")
                BodyText(selected.format(DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN)))
            }
            TextButton(onClick = { ui.set("home.calendarMonth", YearMonth.from(selected).toString()); ui.go("H03") }) { Text("기록 달력") }
        }
        if (selected != today) UiButton("오늘로 돌아가기", { ui.set("home.date", today.toString()) }, primary = false)
    }
    when {
        state.loading -> UiCard { BodyText("이 날짜의 기록을 모으고 있어요"); LinearProgressIndicator(Modifier.fillMaxWidth(), color = DeepBlue) }
        state.error != null -> UiCard { BodyText(state.error!!); UiButton("기록 다시 불러오기", model::refresh) }
        calendar != null -> {
            val day = calendar.days.firstOrNull { it.date == selected.toString() }
            if (day != null) {
                if (id == "H03") SectionTitle(selected.format(DateTimeFormatter.ofPattern("M월 d일의 기록", Locale.KOREAN)))
                DayCards(day, ui, working, onMeals = { openMeals(day.date) }, onWorkout = {
                    if (!working) { if (workoutState?.date != day.date) workouts?.loadDate(day.date); ui.go("W01") }
                })
                if (id == "H03") UiButton("이 날짜 홈으로", { ui.go("H01") }, primary = false)
            }
            if (id != "H03") LocalAccount.current.state.profile?.let { profile -> UiCard {
                UiRow("내 목표 · 단위", profile.dailyCalories?.let { "시작 열량 $it kcal / 일" } ?: "영양 목표 없이 기록 중",
                    icon = "Settings", onClick = { ui.go("S02") })
            } }
            if (id != "H03") UiCard {
                SectionTitle("기록이 쌓인 한 주")
                BodyText("지난주 식사와 운동, 몸의 변화를 함께 살펴보세요.")
                UiButton("주간 리포트 보기", { ui.go("R01") })
            }
            UiButton("기록 새로고침", model::refresh, primary = false)
        }
    }
    confirmDate?.let { date -> AlertDialog(onDismissRequest = { confirmDate = null },
        title = { Text("작성 중인 식사가 있어요") },
        text = { Text("$date 기록을 열면 아직 저장하지 않은 식사 변경은 사라져요.") },
        confirmButton = { TextButton(onClick = { meals?.loadDate(date); confirmDate = null; ui.go("F01") }) { Text("이 날짜로 이동") } },
        dismissButton = { TextButton(onClick = { confirmDate = null; ui.go("F03") }) { Text("작성 중 식사 이어서") } }) }
}

@Composable private fun CalendarGrid(month: YearMonth, selectedDate: LocalDate, today: LocalDate, calendar: RecordCalendar?,
    onMonth: (YearMonth) -> Unit, onSelect: (LocalDate) -> Unit) {
    UiCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onMonth(month.minusMonths(1)) }, enabled = month > YearMonth.of(1900,1), modifier = Modifier.semantics { contentDescription = "이전 달" }) { UiIcon("ChevronLeft") }
            Text("${month.year}년 ${month.monthValue}월", Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
            IconButton(onClick = { onMonth(month.plusMonths(1)) }, enabled = month < YearMonth.from(today), modifier = Modifier.semantics { contentDescription = "다음 달" }) { UiIcon("ChevronRight") }
        }
        Row { listOf("월","화","수","목","금","토","일").forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, color = Muted, fontSize = 12.sp) } }
        val offset = month.atDay(1).dayOfWeek.value - 1
        val days = calendar?.days?.associateBy { it.date }.orEmpty()
        repeat((offset + month.lengthOfMonth() + 6) / 7) { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(7) { weekday ->
                    val number = week * 7 + weekday - offset + 1
                    if (number !in 1..month.lengthOfMonth()) Spacer(Modifier.weight(1f).height(48.dp)) else {
                        val date = month.atDay(number)
                        val row = days[date.toString()]
                        val count = row?.categories ?: 0
                        val selected = date == selectedDate
                        val available = date <= today
                        val fill = when { selected -> Charcoal; count >= 3 -> Celery; count > 0 -> Color(0xFFE6EFD9); else -> Color(0xFFF1F3F6) }
                        val status = when { !available -> "미래 날짜"; row == null -> "조회 전"; count == 0 -> "남긴 기록 없음"; else -> "${count}종류 기록" }
                        Column(Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(10.dp)).background(fill)
                            .clickable(enabled = available, role = Role.Button) { onSelect(date) }
                            .semantics { contentDescription = "$date · $status"; this.selected = selected },
                            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(number.toString(), color = if (selected) Color.White else if (available) Ink else Muted)
                            if (count > 0) Text("$count", fontSize = 10.sp, color = if (selected) Celery else Ink)
                        }
                    }
                }
            }
        }
        MutedText("숫자와 색 농도는 남긴 기록의 종류예요. 목표 달성률이 아니에요.")
        if (calendar != null) KeyValue("이달 기록을 남긴 날", "${calendar.days.count { it.categories > 0 }}일")
    }
}

@Composable private fun DayCards(day: RecordDay, ui: PreviewSession, working: Boolean, onMeals: () -> Unit, onWorkout: () -> Unit) {
    val imperial = LocalAccount.current.state.profile?.units == "IMPERIAL"
    UiCard(dark = true) {
        Badge(when(day.workoutStatus) { "IN_PROGRESS" -> "운동 진행 중"; "COMPLETED" -> "운동 기록 마침"; else -> "운동 기록 전" })
        SectionTitle(day.workoutName ?: "나의 운동 루틴")
        if (day.workoutStatus != null) {
            HeroNumber(day.doneSets.toString(), "세트 수행")
            MutedText("생략 ${day.skippedSets} · 미기록 ${day.pendingSets}세트 · 준비 세트 포함")
        } else BodyText("그날의 계획과 실제 수행을 함께 남겨요.")
        UiButton(if (day.workoutStatus == "IN_PROGRESS") "운동 기록 이어가기" else "운동 기록 보기", onWorkout, enabled = !working)
    }
    UiCard {
        SectionTitle("식사 기록")
        if (day.mealsEaten + day.mealsSkipped == 0) BodyText("아직 확인한 식사가 없어요.")
        else {
            KeyValue("먹었다고 기록", "${day.mealsEaten}끼")
            KeyValue("먹지 않았다고 기록", "${day.mealsSkipped}끼")
        }
        MutedText("미기록 식사는 먹지 않은 것으로 계산하지 않아요.")
        UiButton("이날 식단 보기", onMeals, primary = false, enabled = !working)
    }
    UiCard {
        SectionTitle("체중 · 허리둘레", "변화 보기", { ui.go("H05") })
        KeyValue("체중", measurement(day.weightKg, if (imperial) BigDecimal("2.2046226218") else BigDecimal.ONE, if (imperial) "lb" else "kg"))
        KeyValue("허리둘레", measurement(day.waistCm, if (imperial) BigDecimal.ONE.divide(BigDecimal("2.54"), 12, RoundingMode.HALF_UP) else BigDecimal.ONE, if (imperial) "in" else "cm"))
        UiButton("측정 기록하기", { prepareBody(ui, LocalDate.parse(day.date)); ui.go("H04") }, primary = false)
    }
    UiCard {
        UiRow("걸음 기록", day.steps?.let { "%,d걸음 · 저장된 수집 기록".format(it) } ?: "아직 수집한 기록이 없어요", icon = "Footprints", onClick = { ui.go("H06") })
        MutedText("휴대폰을 들고 있던 시간과 수집 상태에 따라 실제 활동과 다를 수 있어요.")
    }
    UiCard { UiRow("컨디션 기록", if (day.conditionRecorded) "이날 남긴 컨디션을 확인해요" else "수면·피로·근육통을 남겨요", icon = "HeartPulse", onClick = { ui.go("H07") }) }
}

private fun measurement(value: BigDecimal?, factor: BigDecimal, unit: String) = value?.multiply(factor)
    ?.setScale(1, RoundingMode.HALF_UP)?.stripTrailingZeros()?.toPlainString()?.let { "$it $unit" } ?: "미기록"
