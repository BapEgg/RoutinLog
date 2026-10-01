package com.bapegg.routinlog.ui.screens

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bapegg.routinlog.data.*
import com.bapegg.routinlog.domain.WorkoutNumbers
import com.bapegg.routinlog.ui.*
import com.bapegg.routinlog.ui.theme.*
import kotlinx.coroutines.delay
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/** User-authored routines and confirmed workout records. No sample catalog enters these screens. */
@Composable
internal fun LiveWorkoutScreens(id: String, ui: PreviewSession, model: WorkoutViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    state.error?.let { UiCard { Text(it, color = MaterialTheme.colorScheme.error); UiButton("서버에서 다시 확인", model::refresh, primary = false, enabled = !state.busy && !state.loading) } }
    if (!state.loaded) UiCard {
        SectionTitle(if (state.loading) "운동 기록을 불러오고 있어요" else "내 운동 기록을 확인해주세요")
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = DeepBlue) else UiButton("운동 기록 불러오기", model::refresh)
    } else when (id) {
        "W01" -> LiveWorkoutWeek(ui, model, state)
        "W04" -> LiveRoutineEditor(ui, model, state)
        "W05" -> LiveExerciseLibrary(ui, model, state)
        "W06" -> LiveExerciseDetails(ui, model, state)
        "W07" -> LiveExerciseForm(ui, model, state)
        "W08" -> LiveWorkoutSession(ui, model, state)
        "W09" -> LiveSetEditor(ui, model, state)
        "W10" -> LiveRestTimer(ui, model, state)
        "W11" -> LiveReplacement(ui, model, state)
        "W13" -> LiveWorkoutMemo(ui, model, state)
        "W14" -> LiveDateOverride(ui, model, state)
        "W16" -> LiveWorkoutFinish(ui, model, state)
        else -> UiCard { BodyText("내 루틴과 실제 수행부터 기록할 수 있어요."); UiButton("운동 일정으로", { ui.go("W01") }) }
    }
    if (state.busy || (state.loading && state.loaded)) Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
        UiCard { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            CircularProgressIndicator(Modifier.size(26.dp), color = DeepBlue, strokeWidth = 3.dp)
            Text(if (state.busy) "기록을 저장하고 있어요" else "운동 기록 확인 중…")
        } }
    }
}

private data class RoutineOpen(val id: String?)

@Composable
private fun LiveWorkoutWeek(ui: PreviewSession, model: WorkoutViewModel, state: WorkoutUiState) {
    var dateOpen by remember { mutableStateOf(false) }
    var date by remember(state.date) { mutableStateOf(state.date) }
    var opening by remember { mutableStateOf<RoutineOpen?>(null) }
    var discarding by remember { mutableStateOf(false) }
    var planOpen by remember { mutableStateOf(false) }
    var planSlots by remember { mutableStateOf(emptyList<WorkoutPlanSlot>()) }
    var planVersion by remember { mutableStateOf<Long?>(null) }
    var pickingDay by remember { mutableStateOf<Int?>(null) }
    fun openRoutine(id: String?) {
        if (state.routineDraft != null) {
            if (id == state.routineDraft.id) ui.go("W04") else opening = RoutineOpen(id)
        } else { model.beginRoutine(id); ui.go("W04") }
    }
    UiCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { MutedText("선택한 날짜"); Text(state.date, style = MaterialTheme.typography.titleMedium) }
            TextButton(onClick = { dateOpen = !dateOpen }) { Text(if (dateOpen) "접기" else "날짜 변경") }
        }
        if (dateOpen) {
            WorkoutField("운동 날짜", date, { date = it }, hint = "연도-월-일")
            UiButton("이 날짜 보기", { model.loadDate(date); dateOpen = false }, primary = false)
            UiButton("오늘로", { model.loadDate(ui.today().toString()); dateOpen = false }, primary = false)
        }
    }
    val future = runCatching { LocalDate.parse(state.date) > ui.today() }.getOrDefault(true)
    UiButton(when { state.session?.status == "COMPLETED" -> "운동 기록 보기"; state.session != null -> "운동 이어서 기록"; state.day?.planned != null -> "이날 운동 시작"; else -> "자유 운동 기록 시작" },
        { model.startSession { ui.go("W08") } }, enabled = !future)
    if (future) MutedText("앞으로의 일정은 바꿀 수 있어요. 수행 기록은 해당 날짜부터 남겨요.")
    UiButton("이 날짜의 루틴 변경", { ui.go("W14") }, primary = false)
    SectionTitle("이번 주 일정")
    UiCard {
        state.days.forEach { day ->
            val localDate = LocalDate.parse(day.date)
            val session = day.session
            val performed = session?.entries.orEmpty().sumOf { entry -> entry.sets.count { it.status == "DONE" } }
            val planned = if (session != null) session.planned else day.planned
            UiRow("${weekLabel(localDate.dayOfWeek.value)} · ${localDate.monthValue}/${localDate.dayOfMonth}",
                planned?.routineName ?: if (session != null) "자유 운동" else if (day.`override` != null) "휴식으로 변경" else "정해둔 루틴 없음",
                value = when { session?.status == "COMPLETED" -> "마무리 · ${performed}세트"; session != null -> "진행 중 · ${performed}세트"; else -> "기록 전" },
                selected = day.date == state.date, onClick = { model.loadDate(day.date) })
        }
    }
    SectionTitle("기본 요일별 루틴")
    UiButton(if (planOpen) "요일 설정 접기" else "요일별 루틴 설정", {
        if (!planOpen) {
            planSlots = (1..7).map { day -> state.plan?.slots?.firstOrNull { it.dayOfWeek == day } ?: WorkoutPlanSlot(day) }
            planVersion = state.plan?.version
        }
        planOpen = !planOpen
    }, primary = false)
    if (planOpen) UiCard {
        planSlots.forEach { slot -> UiRow("${weekLabel(slot.dayOfWeek)}요일", state.routines.firstOrNull { it.id == slot.routineId }?.name ?: "휴식 · 루틴 없음", onClick = { pickingDay = slot.dayOfWeek }) }
        UiButton("요일별 루틴 저장", { model.savePlan(WorkoutPlanWrite(planSlots, planVersion)) { planOpen = false } })
        MutedText("루틴을 수정했다면 여기도 다시 저장해주세요. 이미 시작한 운동은 바뀌지 않아요.")
    }
    state.routineDraft?.let { draft -> UiCard {
        Badge("작성 중인 루틴")
        Text(draft.name.ifBlank { "이름 없는 루틴" }, fontWeight = FontWeight.SemiBold)
        UiButton("작성하던 루틴 이어서", { ui.go("W04") })
        TextButton(onClick = { discarding = true }) { Text("작성 내용 버리기") }
    } }
    SectionTitle("저장한 루틴")
    state.routines.forEach { routine -> UiCard { UiRow(routine.name, "운동 ${routine.entries.size}개", icon = "Dumbbell", onClick = { openRoutine(routine.id) }) } }
    if (state.exercises.isEmpty()) UiCard {
        SectionTitle("내가 하는 운동부터 등록해요")
        MutedText("운동 이름과 기록 방식을 정하면 루틴에 넣을 수 있어요.")
        UiButton("첫 운동 등록", { editExercise(ui, null, "W01") })
    } else UiButton("새 루틴 만들기", { openRoutine(null) })
    UiButton("내 운동 목록", { ui.set("liveWorkout.pickFor", "manage"); ui.go("W05") }, primary = false)
    UiButton("서버에서 새로 불러오기", model::refresh, primary = false)
    opening?.let { next -> WorkoutConfirm("작성하던 루틴을 바꿀까요?", "저장하지 않은 변경을 버리고 다른 루틴을 열어요.", "다른 루틴 열기", { opening = null }) {
        model.discardRoutine(); model.beginRoutine(next.id); opening = null; ui.go("W04")
    } }
    if (discarding) WorkoutConfirm("루틴 초안을 버릴까요?", "저장된 기본 루틴은 그대로 남아요.", "버리기", { discarding = false }) { model.discardRoutine(); discarding = false }
    pickingDay?.let { day -> WorkoutSheet("${weekLabel(day)}요일의 기본 루틴", { pickingDay = null }) {
        Choice("휴식 · 루틴 없음", selected = planSlots.firstOrNull { it.dayOfWeek == day }?.routineId == null) { planSlots = planSlots.map { if (it.dayOfWeek == day) it.copy(routineId = null) else it }; pickingDay = null }
        state.routines.forEach { routine -> Choice(routine.name, "운동 ${routine.entries.size}개", planSlots.firstOrNull { it.dayOfWeek == day }?.routineId == routine.id) {
            planSlots = planSlots.map { if (it.dayOfWeek == day) it.copy(routineId = routine.id) else it }; pickingDay = null
        } }
        if (state.routines.isEmpty()) MutedText("먼저 내 루틴을 하나 저장해주세요.")
    } }
}

@Composable
private fun LiveRoutineEditor(ui: PreviewSession, model: WorkoutViewModel, state: WorkoutUiState) {
    val draft = state.routineDraft
    if (draft == null) { WorkoutEmpty("수정할 루틴을 먼저 선택해주세요.", "저장 루틴으로", { ui.go("W01") }); return }
    var deleting by remember { mutableStateOf(false) }
    var discarding by remember { mutableStateOf(false) }
    var removeEntry by remember { mutableStateOf<String?>(null) }
    val unit = weightUnit(draft.units)
    WorkoutField("루틴 이름", draft.name, model::setRoutineName)
    MutedText("계획을 모르는 값은 비워두세요. 실제 수행할 때 기록할 수 있어요.")
    draft.entries.forEachIndexed { index, entry -> key(entry.id) { UiCard {
        ExerciseIdentity(entry.exercise)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { model.moveRoutineEntry(entry.id, -1) }, enabled = index > 0) { Text("위로") }
            TextButton(onClick = { model.moveRoutineEntry(entry.id, 1) }, enabled = index < draft.entries.lastIndex) { Text("아래로") }
            TextButton(onClick = { removeEntry = entry.id }) { Text("운동 빼기", color = MaterialTheme.colorScheme.error) }
        }
        entry.sets.forEachIndexed { setIndex, set -> key(set.id) {
            DividerLine()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${setIndex + 1}세트", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Checkbox(checked = set.warmup, onCheckedChange = { model.updateRoutineSet(entry.id, set.id, set.weight, set.reps, set.seconds, it) }, colors = CheckboxDefaults.colors(checkedColor = Celery, checkmarkColor = Ink))
                Text("준비 세트", style = MaterialTheme.typography.bodySmall)
            }
            when (entry.exercise.recordType) {
                "WEIGHT_REPS" -> {
                    WorkoutField("${setIndex + 1}세트 계획 중량", set.weight, { model.updateRoutineSet(entry.id, set.id, it, set.reps, set.seconds, set.warmup) }, unit, numeric = true)
                    WorkoutField("${setIndex + 1}세트 계획 횟수", set.reps, { model.updateRoutineSet(entry.id, set.id, set.weight, it, set.seconds, set.warmup) }, "회", numeric = true)
                }
                "REPS" -> WorkoutField("${setIndex + 1}세트 계획 횟수", set.reps, { model.updateRoutineSet(entry.id, set.id, set.weight, it, set.seconds, set.warmup) }, "회", numeric = true)
                "DURATION" -> WorkoutField("${setIndex + 1}세트 계획 시간", set.seconds, { model.updateRoutineSet(entry.id, set.id, set.weight, set.reps, it, set.warmup) }, "초", numeric = true)
            }
            if (entry.sets.size > 1) TextButton(onClick = { model.removeRoutineSet(entry.id, set.id) }) { Text("이 계획 세트 빼기") }
        } }
        UiButton("계획 세트 추가", { model.addRoutineSet(entry.id) }, primary = false, enabled = entry.sets.size < 30)
        WorkoutField("세트 사이 휴식", entry.restSeconds, { model.setRoutineRest(entry.id, it) }, "초", numeric = true)
        WorkoutField("이 운동의 메모 · 선택", entry.note, { model.setRoutineEntryNote(entry.id, it) }, multiline = true)
    } } }
    UiButton("루틴에 운동 추가", { ui.set("liveWorkout.pickFor", "routine"); ui.go("W05") }, primary = false, enabled = draft.entries.size < 40)
    WorkoutField("루틴 메모 · 선택", draft.note, model::setRoutineNote, multiline = true)
    UiButton("기본 루틴 저장", { model.saveRoutine { ui.go("W01") } }, enabled = draft.entries.isNotEmpty())
    UiButton("작성 취소", { discarding = true }, primary = false)
    val original = state.routines.firstOrNull { it.id == draft.id }
    if (original != null) TextButton(onClick = { deleting = true }) { Text("저장된 루틴 삭제", color = MaterialTheme.colorScheme.error) }
    removeEntry?.let { entryId -> WorkoutConfirm("루틴에서 이 운동을 뺄까요?", "이 루틴 초안에서만 제외해요.", "빼기", { removeEntry = null }) { model.removeRoutineEntry(entryId); removeEntry = null } }
    if (discarding) WorkoutConfirm("작성 내용을 버릴까요?", "저장하지 않은 루틴 변경이 사라져요.", "버리고 돌아가기", { discarding = false }) { model.discardRoutine(); ui.go("W01") }
    if (deleting && original != null) WorkoutConfirm("저장된 루틴을 삭제할까요?", "기본 요일에 연결되어 있다면 먼저 해제해주세요. 지난 수행 기록은 남아요.", "삭제", { deleting = false }) {
        deleting = false; model.deleteRoutine(original) { model.discardRoutine(); ui.go("W01") }
    }
}

@Composable
private fun LiveExerciseLibrary(ui: PreviewSession, model: WorkoutViewModel, state: WorkoutUiState) {
    var query by remember { mutableStateOf("") }
    val purpose = ui.get("liveWorkout.pickFor", "manage")
    Badge(when (purpose) { "routine" -> "기본 루틴에 넣을 운동"; "session" -> "이날 수행할 운동 추가"; "replace" -> "대신 수행할 운동 선택"; else -> "내가 등록한 운동" })
    WorkoutField("내 운동 검색", query, { query = it }, hint = "운동 이름 또는 기구")
    UiButton("내 운동 직접 등록", { editExercise(ui, null, "W05") }, primary = false)
    val results = state.exercises.filter { query.isBlank() || it.name.contains(query, true) || it.equipment.contains(query, true) }
    if (results.isEmpty()) UiCard { BodyText("찾는 운동이 없으면 이름과 기록 방식을 직접 등록해주세요.") }
    results.forEach { exercise -> UiCard {
        ExerciseIdentity(exercise.snapshot())
        UiButton(when (purpose) { "routine" -> "루틴에 추가"; "session" -> "오늘 운동에 추가"; "replace" -> "이 운동과 비교"; else -> "운동 정보 보기" }, {
            when (purpose) {
                "routine" -> { model.addRoutineExercise(exercise.id); ui.go("W04") }
                "session" -> model.addSessionExercise(exercise.id) { ui.go("W08") }
                "replace" -> { ui.set("liveWorkout.replacementId", exercise.id); ui.go("W11") }
                else -> { ui.set("liveWorkout.exerciseId", exercise.id); ui.go("W06") }
            }
        }, primary = purpose != "manage")
        if (purpose != "manage") TextButton(onClick = { editExercise(ui, exercise.id, "W05") }) { Text("등록 정보 수정") }
    } }
}

@Composable
private fun LiveExerciseForm(ui: PreviewSession, model: WorkoutViewModel, state: WorkoutUiState) {
    val selected = ui.get("liveWorkout.exerciseId")
    val original = state.exercises.firstOrNull { it.id == selected }
    if (selected.isNotBlank() && original == null) { WorkoutEmpty("이 운동 정보를 찾을 수 없어요.", "내 운동 목록", { ui.go("W05") }); return }
    val id = remember(selected) { selected.ifBlank { UUID.randomUUID().toString() } }
    val version = remember(id) { original?.version }
    var name by remember(id) { mutableStateOf(original?.name.orEmpty()) }
    var equipment by remember(id) { mutableStateOf(original?.equipment ?: "미지정") }
    var target by remember(id) { mutableStateOf(original?.target.orEmpty()) }
    var type by remember(id) { mutableStateOf(original?.recordType ?: "WEIGHT_REPS") }
    var convention by remember(id) { mutableStateOf(original?.loadConvention ?: "UNSPECIFIED") }
    var error by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf(false) }
    WorkoutField("운동 이름", name, { name = it }, hint = "예: 내가 쓰는 머신의 운동 이름")
    WorkoutField("사용하는 기구", equipment, { equipment = it })
    Chips(listOf("머신", "바벨", "덤벨", "밴드", "맨몸", "기타"), equipment) { equipment = it }
    WorkoutField("주로 운동하는 부위 · 선택", target, { target = it })
    SectionTitle("기록 방식")
    Chips(recordLabels.values.toList(), recordLabels[type].orEmpty()) { label -> type = recordLabels.entries.first { it.value == label }.key }
    if (type == "WEIGHT_REPS") {
        SectionTitle("무게를 적는 기준")
        Chips(loadLabels.values.toList(), loadLabels[convention].orEmpty()) { label -> convention = loadLabels.entries.first { it.value == label }.key }
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    UiButton(if (original == null) "내 운동 저장" else "운동 정보 변경 저장", {
        error = when {
            !workoutText(name.trim(), 80, required = true) -> "운동 이름을 1~80자로 입력해주세요."
            !workoutText(equipment.trim(), 80, required = true) -> "기구 이름을 1~80자로 입력해주세요."
            !workoutText(target.trim(), 80) -> "운동 부위는 80자 이내로 입력해주세요."
            else -> null
        }
        if (error == null) model.saveExercise(id, ExerciseWrite(name.trim(), equipment.trim(), target.trim(), type, convention, version)) { ui.go(ui.get("liveWorkout.exerciseReturn", "W05")) }
    })
    if (original != null) {
        MutedText("이름과 기록 방식을 바꿔도 지난 운동 기록과 이미 저장한 일정은 그대로 남아요.")
        TextButton(onClick = { deleting = true }) { Text("내 운동 삭제", color = MaterialTheme.colorScheme.error) }
        if (deleting) WorkoutConfirm("이 운동을 삭제할까요?", "루틴에서 사용 중이면 먼저 루틴에서 빼주세요. 지난 운동 기록은 남아요.", "삭제", { deleting = false }) {
            deleting = false; model.deleteExercise(original) { ui.go("W05") }
        }
    }
}

@Composable
private fun LiveExerciseDetails(ui: PreviewSession, model: WorkoutViewModel, state: WorkoutUiState) {
    val exercise = state.exercises.firstOrNull { it.id == ui.get("liveWorkout.exerciseId") }
    if (exercise == null) { WorkoutEmpty("이 운동 정보를 찾을 수 없어요.", "내 운동 목록", { ui.go("W05") }); return }
    LaunchedEffect(exercise.id, state.date) { model.loadHistory(exercise.id) }
    UiCard { Badge("직접 등록한 운동"); ExerciseIdentity(exercise.snapshot()) }
    UiButton("운동 정보 수정", { editExercise(ui, exercise.id, "W05") })
    WorkoutHistory(state, exercise.snapshot(), state.units)
}

@Composable
private fun LiveWorkoutSession(ui: PreviewSession, model: WorkoutViewModel, state: WorkoutUiState) {
    val session = state.session
    if (session == null) {
        UiCard {
            SectionTitle(state.day?.planned?.routineName ?: "자유 운동")
            MutedText("시작하면 이날의 계획과 실제 수행을 나란히 기록해요.")
            UiButton("운동 기록 시작", { model.startSession { ui.go("W08") } }, enabled = LocalDate.parse(state.date) <= ui.today())
        }; return
    }
    var deleting by remember { mutableStateOf(false) }
    var removeEntry by remember { mutableStateOf<WorkoutEntryDto?>(null) }
    Badge(if (session.status == "COMPLETED") "마무리한 운동" else "기록 중")
    UiCard {
        SectionTitle(session.planned?.routineName ?: "자유 운동")
        MutedText(session.date)
        SessionCounts(session)
    }
    if (session.status == "COMPLETED") UiButton("다시 이어서 기록", { model.reopenSession { ui.go("W08") } }, primary = false)
    SectionTitle("실제 수행한 운동")
    if (session.entries.isEmpty()) UiCard { BodyText("오늘 수행할 운동을 내 목록에서 추가해주세요.") }
    session.entries.forEach { entry -> UiCard {
        ExerciseIdentity(entry.exercise)
        val planned = session.planned?.entries?.firstOrNull { it.id == entry.plannedEntryId }
        if (planned != null && planned.exercise.id != entry.exercise.id) {
            MutedText("원래 계획 · ${planned.exercise.name}")
            entry.replacementReason?.let { MutedText("바꾼 이유 · $it") }
        }
        val done = entry.sets.count { it.status == "DONE" }
        val skipped = entry.sets.count { it.status == "SKIPPED" }
        val pending = entry.sets.count { it.status == "PENDING" }
        KeyValue("세트 기록", "수행 $done · 생략 $skipped · 미기록 $pending")
        UiButton(if (session.status == "COMPLETED") "세트 기록 확인·수정" else "이 운동 기록", { selectWorkoutEntry(ui, entry); ui.go("W09") })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (session.status != "COMPLETED") TextButton(onClick = {
                ui.set("liveWorkout.entryId", entry.id); ui.set("liveWorkout.pickFor", "replace"); ui.set("liveWorkout.replacementId", ""); ui.go("W05")
            }) { Text("운동 교체") }
            TextButton(onClick = { removeEntry = entry }) { Text("기록에서 빼기", color = MaterialTheme.colorScheme.error) }
        }
        entry.note?.takeIf { it.isNotBlank() }?.let { MutedText(it) }
    } }
    if (session.status != "COMPLETED") UiButton("오늘 운동 추가", { ui.set("liveWorkout.pickFor", "session"); ui.go("W05") }, primary = false, enabled = session.entries.size < 40)
    session.planned?.let { planned -> UiCard {
        SectionTitle("시작할 때의 원래 계획")
        planned.entries.forEach { entry -> KeyValue(entry.exercise.name, "${entry.sets.size}세트 · ${entry.exercise.target.ifBlank { "부위 미입력" }}") }
        MutedText("운동을 바꾸거나 일부만 수행해도 원래 계획은 남아요.")
    } }
    UiButton(if (session.status == "COMPLETED") "마무리 기록 확인" else "오늘 운동 마무리", { ui.go("W16") })
    TextButton(onClick = { deleting = true }) { Text("이날 운동 기록 삭제", color = MaterialTheme.colorScheme.error) }
    if (deleting) WorkoutConfirm("이날 운동 기록을 삭제할까요?", "세트와 수행 메모를 삭제해요. 저장한 루틴과 기본 일정은 유지돼요.", "기록 삭제", { deleting = false }) {
        deleting = false; model.deleteSession { ui.go("W01") }
    }
    removeEntry?.let { entry -> WorkoutConfirm("이 운동 기록을 뺄까요?", "${entry.exercise.name}의 실제 세트 기록과 메모를 삭제해요. 원래 계획은 남아요.", "빼기", { removeEntry = null }) {
        removeEntry = null; model.removeSessionEntry(entry.id) { }
    } }
}

@Composable
private fun LiveSetEditor(ui: PreviewSession, model: WorkoutViewModel, state: WorkoutUiState) {
    val session = state.session
    val entry = session?.entries?.firstOrNull { it.id == ui.get("liveWorkout.entryId") }
    if (session == null || entry == null) { WorkoutEmpty("기록할 운동을 먼저 선택해주세요.", "운동 구성으로", { ui.go("W08") }); return }
    val set = entry.sets.firstOrNull { it.id == ui.get("liveWorkout.setId") } ?: entry.sets.firstOrNull { it.status == "PENDING" } ?: entry.sets.firstOrNull()
    if (set == null) { WorkoutEmpty("세트를 추가해 기록해주세요.", "세트 추가", { model.addSessionSet(entry.id) { } }); return }
    val planned = session.planned?.entries?.firstOrNull { it.id == entry.plannedEntryId }
    val plannedSet = planned?.sets?.firstOrNull { it.id == set.planSetId }
    val sameExercise = planned?.exercise?.id == entry.exercise.id
    LaunchedEffect(session.id, entry.id, set.id, state.busy, state.loading) { model.prepareSetDraft(entry.id, set.id) }
    val draft = state.setDrafts[workoutSetDraftKey(session.id, entry.id, set.id)]
    if (draft == null) { UiCard { MutedText("세트 입력을 준비하고 있어요…") }; return }
    val units = draft.units
    val weight = draft.weight
    val reps = draft.reps
    val seconds = draft.seconds
    val changedElsewhere = draft.source != set
    var deleting by remember { mutableStateOf(false) }
    var resetting by remember { mutableStateOf(false) }
    var skippingSaved by remember { mutableStateOf(false) }
    LaunchedEffect(entry.id, entry.exercise.id, state.date) { model.loadHistory(entry.exercise.id) }
    LaunchedEffect(set.id) { ui.set("liveWorkout.setId", set.id) }
    UiCard {
        ExerciseIdentity(entry.exercise)
        if (session.status == "COMPLETED") Badge("마무리된 운동 · 기존 기록 수정")
        if (planned != null) {
            if (sameExercise) KeyValue("이 세트의 계획", plannedSet?.let { plannedSetText(it, entry.exercise.recordType, units) } ?: "추가한 세트")
            else { MutedText("원래 계획 · ${planned.exercise.name}"); MutedText("운동을 바꿨어요. 수행할 중량과 횟수를 새로 적어주세요.") }
        }
        Badge(if (plannedSet?.warmup == true) "준비 세트" else "본 세트")
    }
    SectionTitle("세트 선택")
    entry.sets.forEachIndexed { index, row -> Choice("${index + 1}세트", "${setStatus(row.status)}${if (row.status == "DONE") " · ${actualSetText(row, entry.exercise.recordType, units)}" else ""}", row.id == set.id) { ui.set("liveWorkout.setId", row.id) } }
    UiCard {
        SectionTitle("실제로 수행한 값")
        if (changedElsewhere) {
            BodyText("저장된 세트가 변경됐어요. 지금 입력한 값을 유지할지 선택해주세요.")
            UiButton("내 입력 유지", { model.resolveSetDraft(entry.id, set.id, false) }, primary = false)
            UiButton("저장된 값 다시 불러오기", { model.resolveSetDraft(entry.id, set.id, true) }, primary = false)
        }
        if (entry.exercise.recordType == "WEIGHT_REPS") WorkoutField("실제 중량", weight, { model.updateSetDraft(entry.id, set.id, it, reps, seconds) }, weightUnit(units), numeric = true)
        if (entry.exercise.recordType in setOf("WEIGHT_REPS", "REPS")) WorkoutField("실제 반복수", reps, { model.updateSetDraft(entry.id, set.id, weight, it, seconds) }, "회", numeric = true)
        if (entry.exercise.recordType == "DURATION") WorkoutField("실제 수행 시간", seconds, { model.updateSetDraft(entry.id, set.id, weight, reps, it) }, "초", numeric = true)
        UiButton(if (set.status == "DONE") "이 세트 기록 수정" else "이 세트 완료", {
            model.recordSet(entry.id, set.id, weight, reps, seconds, "DONE", units) {
                if (entry.restSeconds > 0 && session.status != "COMPLETED") ui.go("W10") else ui.go("W08")
            }
        }, enabled = !changedElsewhere)
        if (set.status != "SKIPPED") UiButton("이 세트 생략", {
            if (set.status == "DONE") skippingSaved = true
            else model.recordSet(entry.id, set.id, "", "", "", "SKIPPED", units) { ui.go("W08") }
        }, primary = false, enabled = !changedElsewhere)
        if (set.status != "PENDING") TextButton(onClick = { resetting = true }) { Text("미기록으로 되돌리기") }
    }
    UiButton("불편함·수행 메모", { ui.go("W13") }, primary = false)
    WorkoutHistory(state, entry.exercise, units) { previous ->
        model.updateSetDraft(entry.id, set.id, displayWeight(previous.weightKg, units), previous.reps?.toString().orEmpty(), previous.durationSeconds?.toString().orEmpty())
    }
    UiButton("세트 추가", {
        model.addSessionSet(entry.id) {
            model.state.value.session?.entries?.firstOrNull { it.id == entry.id }?.sets?.lastOrNull()?.let { ui.set("liveWorkout.setId", it.id) }
        }
    }, primary = false, enabled = entry.sets.size < 30 && session.status != "COMPLETED")
    if (entry.sets.size > 1) TextButton(onClick = { deleting = true }) { Text("선택한 세트 삭제", color = MaterialTheme.colorScheme.error) }
    if (deleting) WorkoutConfirm("이 세트 기록을 삭제할까요?", "기록한 실제 수행값이 삭제돼요. 원래 계획은 남아요.", "삭제", { deleting = false }) {
        deleting = false; model.removeSessionSet(entry.id, set.id) { ui.set("liveWorkout.setId", "") }
    }
    if (resetting) WorkoutConfirm("미기록으로 되돌릴까요?", "이 세트에 저장된 실제 수행값을 지워요.", "되돌리기", { resetting = false }) {
        resetting = false; model.recordSet(entry.id, set.id, "", "", "", "PENDING", units) { }
    }
    if (skippingSaved) WorkoutConfirm("수행 기록을 생략으로 바꿀까요?", "이 세트에 저장된 실제 수행값을 지우고 생략으로 남겨요.", "생략으로 변경", { skippingSaved = false }) {
        skippingSaved = false; model.recordSet(entry.id, set.id, "", "", "", "SKIPPED", units) { ui.go("W08") }
    }
}

@Composable
private fun LiveRestTimer(ui: PreviewSession, model: WorkoutViewModel, state: WorkoutUiState) {
    val deadline = state.restDeadlineElapsedMs
    var remaining by remember(deadline) { mutableLongStateOf(restRemaining(deadline)) }
    LaunchedEffect(deadline) {
        remaining = restRemaining(deadline)
        while (remaining > 0) { delay(250); remaining = restRemaining(deadline) }
    }
    val entry = state.session?.entries?.firstOrNull { it.id == ui.get("liveWorkout.entryId") }
    val completed = entry?.sets?.firstOrNull { it.id == ui.get("liveWorkout.setId") && it.status == "DONE" }
    fun next() {
        model.finishRest()
        val nextEntry = entry?.takeIf { it.sets.any { set -> set.status == "PENDING" } }
            ?: state.session?.entries?.firstOrNull { it.sets.any { set -> set.status == "PENDING" } }
        if (nextEntry == null) ui.go("W08") else { selectWorkoutEntry(ui, nextEntry); ui.go("W09") }
    }
    Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(232.dp)) {
            drawCircle(Color(0xFFDDE5D8), style = Stroke(7.dp.toPx()))
            val total = (entry?.restSeconds ?: 0).coerceAtLeast(1)
            drawArc(Celery, -90f, (remaining.toFloat() / total).coerceIn(0f, 1f) * 360f, false, style = Stroke(7.dp.toPx(), cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MutedText(if (deadline == null) "진행 중인 휴식 없음" else if (remaining > 0) "남은 휴식" else "휴식 시간이 지났어요")
            Text("${remaining / 60}:${(remaining % 60).toString().padStart(2, '0')}", fontSize = 56.sp, fontWeight = FontWeight.SemiBold)
        }
    }
    if (entry != null && completed != null) UiCard {
        Badge("방금 저장한 세트")
        Text(entry.exercise.name, fontWeight = FontWeight.SemiBold)
        BodyText(actualSetText(completed, entry.exercise.recordType, state.units))
        UiButton("이 기록 수정", { ui.go("W09") }, primary = false)
    }
    UiButton("휴식 30초 더", { model.extendRest(30) }, primary = false, enabled = deadline != null)
    UiButton(if (remaining > 0) "휴식 끝내고 다음 세트" else "다음 세트로", ::next)
    UiButton("운동 구성으로", { ui.go("W08") }, primary = false)
    MutedText("컨디션에 맞춰 휴식을 조절하세요. 타이머가 끝나도 세트가 자동으로 완료되지는 않아요.")
}

@Composable
private fun LiveReplacement(ui: PreviewSession, model: WorkoutViewModel, state: WorkoutUiState) {
    val entry = state.session?.entries?.firstOrNull { it.id == ui.get("liveWorkout.entryId") }
    val replacement = state.exercises.firstOrNull { it.id == ui.get("liveWorkout.replacementId") }
    if (entry == null || replacement == null) { WorkoutEmpty("교체할 운동을 먼저 선택해주세요.", "내 운동에서 선택", { ui.set("liveWorkout.pickFor", "replace"); ui.go("W05") }); return }
    var reason by remember(entry.id, replacement.id) { mutableStateOf("") }
    var confirming by remember { mutableStateOf(false) }
    UiCard {
        SectionTitle("현재 운동"); ExerciseIdentity(entry.exercise)
        DividerLine()
        SectionTitle("대신 수행할 운동"); ExerciseIdentity(replacement.snapshot())
    }
    MutedText("부위는 내가 등록한 표기예요. 서로 다른 운동의 무게를 그대로 옮기지 말고 새로 정해주세요.")
    Chips(listOf("기구 대기", "수행이 버거움", "시간 부족", "불편함", "다른 이유"), reason) { reason = it }
    WorkoutField("바꾸는 이유", reason, { reason = it }, multiline = true)
    val performed = entry.sets.count { it.status == "DONE" }
    if (performed > 0) UiCard { BodyText("이미 수행한 $performed 세트는 남겨요. 남은 미기록 세트는 생략하고 대체 운동을 따로 추가해요.") }
    else MutedText("이날의 수행 종목만 바뀌어요. 원래 계획과 저장 루틴은 유지돼요.")
    UiButton("이 운동으로 교체", { confirming = true }, enabled = reason.isNotBlank() && reason.length <= 1000 && replacement.id != entry.exercise.id)
    UiButton("다른 운동 선택", { ui.set("liveWorkout.pickFor", "replace"); ui.go("W05") }, primary = false)
    if (confirming) WorkoutConfirm("이날 운동을 바꿀까요?", "${entry.exercise.name} 대신 ${replacement.name}을 기록해요. 원래 계획은 남아요.", "교체", { confirming = false }) {
        confirming = false; model.replaceEntry(entry.id, replacement.id, reason) { ui.go("W08") }
    }
}

@Composable
private fun LiveWorkoutMemo(ui: PreviewSession, model: WorkoutViewModel, state: WorkoutUiState) {
    val entry = state.session?.entries?.firstOrNull { it.id == ui.get("liveWorkout.entryId") }
    if (entry == null) { WorkoutEmpty("메모할 운동을 선택해주세요.", "운동 구성으로", { ui.go("W08") }); return }
    var note by remember(entry.id, state.session?.version) { mutableStateOf(entry.note.orEmpty()) }
    UiCard { ExerciseIdentity(entry.exercise) }
    WorkoutField("이 운동의 수행 메모", note, { note = it }, multiline = true, hint = "예: 기구 설정, 오늘의 난도, 느낀 불편함")
    MutedText("1,000자까지 남길 수 있어요. 불편함을 적어두면 다음 수행 때 다시 확인할 수 있어요.")
    UiButton("수행 메모 저장", { model.saveEntryNote(entry.id, note) { ui.go("W09") } }, enabled = note.length <= 1000)
}

@Composable
private fun LiveDateOverride(ui: PreviewSession, model: WorkoutViewModel, state: WorkoutUiState) {
    var date by remember(state.date) { mutableStateOf(state.date) }
    val current = state.day?.`override`
    var routineId by remember(state.date, current?.version) { mutableStateOf(if (current != null) current.routineId else state.day?.planned?.routineId) }
    var note by remember(state.date, current?.version) { mutableStateOf(current?.note.orEmpty()) }
    var picking by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    UiCard {
        SectionTitle("이 날짜의 일정만 바꿔요")
        WorkoutField("변경할 날짜", date, { date = it }, hint = "연도-월-일")
        UiButton("이 날짜 확인", { model.loadDate(date) }, primary = false)
        KeyValue("선택한 날짜", state.date)
        UiRow("이날의 루틴", state.routines.firstOrNull { it.id == routineId }?.name ?: if (routineId == null) "휴식" else "현재 목록에 없는 루틴", onClick = { picking = true })
    }
    WorkoutField("일정 변경 메모 · 선택", note, { note = it }, multiline = true)
    if (state.session != null) MutedText("이미 시작한 운동의 원계획과 수행 기록은 바뀌지 않아요.")
    UiButton("이 날짜에 적용", { model.saveOverride(state.date, WorkoutOverrideWrite(routineId, note.takeIf { it.isNotBlank() }, current?.version)) { ui.go("W01") } },
        enabled = date == state.date && note.length <= 1000 && (routineId == null || state.routines.any { it.id == routineId }))
    if (date != state.date) MutedText("날짜를 바꿨다면 먼저 ‘이 날짜 확인’을 눌러주세요.")
    MutedText("다른 날짜나 기본 요일 일정은 바뀌지 않아요.")
    if (current != null) UiButton("날짜 변경 취소하고 기본 일정으로", { deleting = true }, primary = false)
    if (deleting && current != null) WorkoutConfirm("기본 일정으로 되돌릴까요?", "이 날짜에 따로 정한 루틴이나 휴식 설정을 지워요. 실제 수행 기록은 남아요.", "되돌리기", { deleting = false }) {
        deleting = false; model.deleteOverride(state.date, current.version) { ui.go("W01") }
    }
    if (picking) WorkoutSheet("이날 수행할 루틴", { picking = false }) {
        Choice("휴식", selected = routineId == null) { routineId = null; picking = false }
        state.routines.forEach { routine -> Choice(routine.name, "운동 ${routine.entries.size}개", routine.id == routineId) { routineId = routine.id; picking = false } }
    }
}

@Composable
private fun LiveWorkoutFinish(ui: PreviewSession, model: WorkoutViewModel, state: WorkoutUiState) {
    val session = state.session
    if (session == null) { WorkoutEmpty("마무리할 운동 기록이 없어요.", "운동 일정으로", { ui.go("W01") }); return }
    var note by remember(session.id, session.version) { mutableStateOf(session.note.orEmpty()) }
    Text("오늘 한 만큼\n남겨둘게요.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    UiCard { SectionTitle(session.planned?.routineName ?: "자유 운동"); MutedText(session.date); SessionCounts(session) }
    session.entries.filter { !it.replacementReason.isNullOrBlank() }.forEach { entry -> UiCard { Text(entry.exercise.name, fontWeight = FontWeight.SemiBold); MutedText("교체 이유 · ${entry.replacementReason}") } }
    WorkoutField("오늘 운동 메모 · 선택", note, { note = it }, multiline = true)
    MutedText("마무리해도 수행하지 않은 세트는 미기록으로 남아요. 모두 수행한 것으로 바꾸지 않아요.")
    UiButton(if (session.status == "COMPLETED") "마무리 기록 저장" else "이날 운동 마무리", {
        model.finishSession(note) { ui.go("W01") }
    }, enabled = note.length <= 1000)
    UiButton("운동 기록으로 돌아가기", { ui.go("W08") }, primary = false)
}

@Composable
private fun WorkoutHistory(state: WorkoutUiState, exercise: ExerciseSnapshot, units: String, apply: ((ActualSet) -> Unit)? = null) {
    SectionTitle("이 운동의 이전 기록")
    if (state.historyLoading) { MutedText("이전 기록 확인 중…"); return }
    val rows = state.history.filter { it.entry.exercise.id == exercise.id }
    if (rows.isEmpty()) { MutedText("이 날짜보다 앞서 완료한 세트 기록이 없어요."); return }
    rows.forEach { row -> UiCard {
        Text(row.date, fontWeight = FontWeight.SemiBold)
        ExerciseIdentity(row.entry.exercise)
        val done = row.entry.sets.filter { it.status == "DONE" }
        done.forEachIndexed { index, set -> KeyValue("${index + 1}세트", actualSetText(set, row.entry.exercise.recordType, units)) }
        val sameBasis = row.entry.exercise.recordType == exercise.recordType &&
            row.entry.exercise.loadConvention == exercise.loadConvention && row.entry.exercise.equipment == exercise.equipment
        if (apply != null && done.isNotEmpty() && sameBasis) UiButton("이날 마지막 수행값 불러오기", { apply(done.last()) }, primary = false)
        else if (apply != null && !sameBasis) MutedText("현재 운동과 기구 또는 기록 기준이 달라 값을 바로 불러올 수 없어요.")
    } }
}

@Composable
private fun SessionCounts(session: WorkoutSessionDto) {
    val sets = session.entries.flatMap { it.sets }
    KeyValue("기록한 종목", "${session.entries.size}개")
    KeyValue("수행한 세트", "${sets.count { it.status == "DONE" }}세트")
    KeyValue("생략한 세트", "${sets.count { it.status == "SKIPPED" }}세트")
    KeyValue("아직 미기록", "${sets.count { it.status == "PENDING" }}세트")
}

@Composable
private fun ExerciseIdentity(exercise: ExerciseSnapshot) {
    Text(exercise.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    MutedText(listOf(exercise.equipment, exercise.target.ifBlank { "부위 미입력" }, recordLabels[exercise.recordType].orEmpty()).filter { it.isNotBlank() }.joinToString(" · "))
    if (exercise.recordType == "WEIGHT_REPS") MutedText("중량 표기 · ${loadLabels[exercise.loadConvention] ?: "기준 미지정"}")
}

@Composable
private fun WorkoutField(label: String, value: String, change: (String) -> Unit, suffix: String = "", numeric: Boolean = false, multiline: Boolean = false, hint: String? = null) {
    OutlinedTextField(value = value, onValueChange = change, label = { Text(label) },
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = label }, singleLine = !multiline, minLines = if (multiline) 3 else 1,
        suffix = if (suffix.isBlank()) null else ({ Text(suffix) }), supportingText = if (hint == null) null else ({ Text(hint) }),
        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Color.White, focusedContainerColor = Color.White, unfocusedBorderColor = Border, focusedBorderColor = DeepBlue))
}

@Composable
private fun WorkoutEmpty(message: String, action: String, click: () -> Unit) { UiCard { BodyText(message); UiButton(action, click) } }

@Composable
private fun WorkoutConfirm(title: String, message: String, action: String, dismiss: () -> Unit, confirm: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = { Text(message) },
        confirmButton = { TextButton(onClick = confirm) { Text(action, color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = dismiss) { Text("취소") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkoutSheet(title: String, dismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(onDismissRequest = dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Silver) {
        Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()).imePadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge); TextButton(onClick = dismiss) { Text("닫기") } }
            content(); Spacer(Modifier.height(16.dp))
        }
    }
}

private val recordLabels = linkedMapOf("WEIGHT_REPS" to "중량 × 반복", "REPS" to "반복수만", "DURATION" to "시간")
private val loadLabels = linkedMapOf("TOTAL" to "전체 합계", "PER_HAND" to "덤벨 한쪽", "MACHINE" to "기구 표시 중량", "EXTERNAL" to "추가한 중량", "BODYWEIGHT" to "맨몸 기준", "UNSPECIFIED" to "기준 미지정")
private fun weekLabel(day: Int) = listOf("월", "화", "수", "목", "금", "토", "일").getOrElse(day - 1) { "" }
private fun weightUnit(units: String) = if (units == "IMPERIAL") "lb" else "kg"
private fun displayWeight(kg: BigDecimal?, units: String): String = kg?.let { WorkoutNumbers.kgToDisplay(it, units).stripTrailingZeros().toPlainString() }.orEmpty()
private fun plannedSetText(set: PlannedSet, type: String, units: String): String = when (type) {
    "WEIGHT_REPS" -> "${displayWeight(set.weightKg, units).ifBlank { "미정" }} ${weightUnit(units)} × ${set.reps?.toString() ?: "미정"}회"
    "REPS" -> "${set.reps?.toString() ?: "미정"}회"
    else -> "${set.durationSeconds?.toString() ?: "미정"}초"
}
private fun actualSetText(set: ActualSet, type: String, units: String): String = when (type) {
    "WEIGHT_REPS" -> "${displayWeight(set.weightKg, units).ifBlank { "—" }} ${weightUnit(units)} × ${set.reps?.toString() ?: "—"}회"
    "REPS" -> "${set.reps?.toString() ?: "—"}회"
    else -> "${set.durationSeconds?.toString() ?: "—"}초"
}
private fun setStatus(status: String) = when (status) { "DONE" -> "수행 완료"; "SKIPPED" -> "생략"; else -> "미기록" }
private fun workoutText(value: String, max: Int, required: Boolean = false) = (!required || value.isNotBlank()) && value.length <= max && '\u0000' !in value
private fun ExerciseDto.snapshot() = ExerciseSnapshot(id, name, equipment, target, recordType, loadConvention)
private fun editExercise(ui: PreviewSession, id: String?, returnRoute: String) { ui.set("liveWorkout.exerciseId", id.orEmpty()); ui.set("liveWorkout.exerciseReturn", returnRoute); ui.go("W07") }
private fun selectWorkoutEntry(ui: PreviewSession, entry: WorkoutEntryDto) { ui.set("liveWorkout.entryId", entry.id); ui.set("liveWorkout.setId", (entry.sets.firstOrNull { it.status == "PENDING" } ?: entry.sets.firstOrNull())?.id.orEmpty()) }
private fun restRemaining(deadline: Long?) = deadline?.let { ((it - SystemClock.elapsedRealtime() + 999) / 1000).coerceAtLeast(0) } ?: 0L
