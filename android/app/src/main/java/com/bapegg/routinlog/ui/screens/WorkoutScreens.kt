package com.bapegg.routinlog.ui.screens

import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bapegg.routinlog.ui.*
import com.bapegg.routinlog.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.max

private data class Exercise(val name: String, val equipment: String, val target: String, val group: String, val weight: String, val reps: String = "12")
private val exerciseCatalog = listOf(
    Exercise("시티드 레그컬", "머신", "햄스트링", "하체", "35"),
    Exercise("레그프레스", "머신", "대퇴사두·둔근", "하체", "120"),
    Exercise("덤벨 런지", "덤벨", "좌우 각각 기록", "하체", "16"),
    Exercise("벤치프레스", "바벨", "가슴", "상체", "40", "10"),
    Exercise("스미스 머신 스쿼트", "머신", "대퇴사두·둔근", "하체", "100", "10"),
    Exercise("루마니안 데드리프트", "바벨", "햄스트링·둔근", "하체", "60", "10"),
    Exercise("덤벨 숄더 프레스", "덤벨", "어깨", "상체", "12", "10"),
    Exercise("카프레이즈", "머신", "종아리", "하체", "40", "15"),
    Exercise("플랭크", "맨몸", "몸통", "맨몸", "0", "30"),
)
private val workoutDays = listOf("월", "화", "수", "목", "금", "토", "일")
private fun activeName(ui: PreviewSession) = ui.get("w.activeExercise", "시티드 레그컬")
private fun exercise(ui: PreviewSession) = exerciseCatalog.firstOrNull { it.name == activeName(ui) }
private fun selectedProgram(ui: PreviewSession) = workoutPrograms.firstOrNull { it.id == ui.get("w.program", "P03") } ?: workoutPrograms[2]
private fun routineExercises(ui: PreviewSession) = ui.get("w.routineExercises", "스미스 머신 스쿼트|루마니안 데드리프트|시티드 레그컬").split('|').filter(String::isNotBlank)
private fun currentSet(ui: PreviewSession) = ui.get("w.currentSet.${activeName(ui)}", if (activeName(ui) == "시티드 레그컬") "2" else "1").toIntOrNull() ?: 1
private fun integer(value: String) = value.toDoubleOrNull()?.takeIf { it.isFinite() && it % 1.0 == 0.0 && it in 0.0..100000.0 }?.toInt()
private fun totalSets(ui: PreviewSession) = integer(ui.get("w.plan.sets.${activeName(ui)}", "3"))?.coerceIn(1, 12) ?: 3
private fun weight(ui: PreviewSession) = ui.get("w.weight.${activeName(ui)}", ui.get("w.plan.weight.${activeName(ui)}", exercise(ui)?.weight ?: "0"))
private fun reps(ui: PreviewSession) = ui.get("w.reps.${activeName(ui)}", ui.get("w.plan.reps.${activeName(ui)}", exercise(ui)?.reps ?: "12"))
private fun setExercise(ui: PreviewSession, name: String, route: String = "W09", plan: Boolean = false) {
    ui.set("w.activeExercise", name)
    ui.set("w.setContext", if (plan) "plan" else "record")
    ui.set("w.editSet", "")
    ui.go(route)
}
private fun todayExercises(ui: PreviewSession) = listOf(ui.get("w.replacement", "레그프레스"), "덤벨 런지", "시티드 레그컬", "카프레이즈", "플랭크")
private fun completedExercises(ui: PreviewSession) = todayExercises(ui).filterIndexed { index, name -> index < 2 || ui.flag("w.done.$name") }

@Composable
fun WorkoutScreens(id: String, ui: PreviewSession) {
    when (id) {
        "W01" -> WorkoutWeek(ui)
        "W02" -> ProgramCatalog(ui)
        "W03" -> ProgramDetail(ui)
        "W04" -> RoutineEditor(ui)
        "W05" -> ExerciseSearch(ui)
        "W06" -> ExerciseGuide(ui)
        "W07" -> CustomExercise(ui)
        "W08" -> WorkoutToday(ui)
        "W09" -> SetRecorder(ui)
        "W10" -> RestTimer(ui)
        "W11" -> SubstitutePicker(ui)
        "W12" -> SubstituteConfirm(ui)
        "W13" -> ExerciseMemo(ui)
        "W14" -> MoveWorkout(ui)
        "W15" -> WorkoutCardioScreen(ui)
        "W16" -> FinishWorkout(ui)
        "W17" -> Warmup(ui)
    }
}

@Composable
fun WorkoutFooter(id: String, ui: PreviewSession) {
    when (id) {
        "W01" -> UiButton(if (ui.flag("w.finished")) "오늘 운동 기록 보기" else "오늘 하체 A 시작", { ui.go("W08") })
        "W02" -> UiButton("내 루틴 직접 만들기", { ui.set("w.draftProgram", ""); ui.go("W04") }, primary = false)
        "W03" -> UiButton("내 일정으로 초안 만들기", {
            val program = selectedProgram(ui)
            ui.set("w.draftProgram", program.id)
            ui.set("w.routineName", program.sessions.first().name)
            ui.set("w.routineExercises", ui.get("w.draftExercises.${program.id}.0", program.sessions.first().exercises.joinToString("|")))
            ui.set("w.programSession", "0")
            ui.go("W04")
        })
        "W04" -> UiButton("기본 루틴 저장", {
            if (ui.get("w.routineName", "하체 A").isBlank() || routineExercises(ui).isEmpty()) ui.notify("루틴 이름과 운동을 하나 이상 넣어주세요.")
            else {
                val draft = workoutPrograms.firstOrNull { it.id == ui.get("w.draftProgram") }
                if (draft != null) {
                    ui.set("w.draftExercises.${draft.id}.${ui.get("w.programSession", "0")}", routineExercises(ui).joinToString("|"))
                    workoutDays.forEach { ui.set("w.planDay.$it", "휴식") }
                    draft.sessions.forEachIndexed { i, day ->
                        val target = ui.get("w.programDay.${draft.id}.$i", programDefaultDays(draft)[i])
                        val existing = ui.get("w.planDay.$target", "휴식")
                        ui.set("w.planDay.$target", if (existing == "휴식") day.name else "$existing + ${day.name}")
                    }
                }
                ui.set("w.activeProgram", ui.get("w.draftProgram")); ui.save("W01")
            }
        })
        "W05" -> UiButton("+ 내 운동 직접 등록", { ui.go("W07") }, primary = false)
        "W06" -> UiButton(if (ui.get("w.guideReturn") == "W09") "세트 기록으로 돌아가기" else "이 운동 추가", {
            if (ui.get("w.guideReturn") == "W09") ui.go("W09") else addExercise(ui, activeName(ui))
        })
        "W07" -> UiButton("내 운동 저장", {
            val name = ui.get("w.customName").trim()
            if (name.isBlank()) ui.notify("운동 이름을 입력해주세요.")
            else {
                ui.set("w.customExercises", (ui.get("w.customExercises").split('|').filter(String::isNotBlank) + name).distinct().joinToString("|"))
                ui.set("w.custom.$name.equipment", ui.get("w.customEquipment", "머신"))
                ui.set("w.custom.$name.target", ui.get("w.customTarget", "미확인"))
                ui.set("w.custom.$name.record", ui.get("w.customRecord", "중량 × 반복"))
                ui.set("w.custom.$name.convention", ui.get("w.customConvention", "기구 표시 중량"))
                ui.set("w.custom.$name.photo", ui.get("w.customPhoto"))
                addExercise(ui, name)
            }
        })
        "W08" -> UiButton("${activeName(ui)} 이어하기", { ui.set("w.setContext", "record"); ui.go("W09") })
        "W09" -> UiButton(if (ui.get("w.setContext") == "plan") "루틴에 반영" else if (ui.get("w.editSet").isNotBlank()) "기록 수정" else "${currentSet(ui)}세트 완료", { completeSet(ui) })
        "W10" -> UiButton("다음 세트 시작", { nextSet(ui) })
        "W11" -> UiButton("변경 전후 확인", { ui.go("W12") })
        "W12" -> UiButton("이 운동으로 교체", {
            val valid = ui.get("w.subWeight", "120").toDoubleOrNull()?.let { it >= 0 && it <= 1000 } == true &&
                ui.get("w.subReps", "12").toIntOrNull()?.let { it in 1..500 } == true &&
                ui.get("w.subSets", "3").toIntOrNull()?.let { it in 1..12 } == true
            if (!valid) ui.notify("중량·횟수·세트를 확인해주세요.") else {
                val replacement = ui.get("w.substitute", "레그프레스")
                ui.set("w.replacement", replacement)
                ui.set("w.plan.weight.$replacement", ui.get("w.subWeight", "120"))
                ui.set("w.plan.reps.$replacement", ui.get("w.subReps", "12"))
                ui.set("w.plan.sets.$replacement", ui.get("w.subSets", "3"))
                if (ui.get("w.subScope", "오늘만 교체") == "기본 루틴에도 반영") ui.set("w.routineExercises", routineExercises(ui).map { if (it == "스미스 머신 스쿼트") replacement else it }.joinToString("|"))
                ui.save("W08")
            }
        })
        "W13" -> UiButton("메모 저장", { ui.set("w.memoSaved.${activeName(ui)}", "true"); ui.save("W09") })
        "W14" -> UiButton("일정 변경 저장", { saveMove(ui) })
        "W15" -> UiButton("유산소 기록 저장", { saveWorkoutCardio(ui) })
        "W16" -> UiButton("오늘 운동 마치기", { ui.set("w.finished", "true"); ui.save("H01") })
        "W17" -> UiButton("워밍업 기록 저장", { ui.save("W08") })
    }
}

@Composable
private fun WorkoutWeek(ui: PreviewSession) {
    val program = workoutPrograms.firstOrNull { it.id == ui.get("w.activeProgram") }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { WorkoutTitle(if (program == null) "9.21 — 9.27" else "다음 주 · 9.28 — 10.4"); MutedText(if (program == null) "직접 설정" else "적용한 계획") }
    UiCard(dark = true) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            UiIcon("Dumbbell", Modifier.size(34.dp), Color(0xFFD2DBE7))
            Column { Text("매주 반복할 루틴", color = Color(0xFFC0CBD8), fontSize = 12.sp); Text(program?.name ?: "상체 · 하체", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
        }
        Text(program?.frequency ?: "월·화·목·금 · 근력 4일", color = Color(0xFFDBE2EA), fontSize = 14.sp)
        WorkoutButtons("내 루틴 편집", { ui.go("W04") }, "프로그램 찾기", { ui.go("W02") }, secondPrimary = true)
    }
    val defaults = listOf("상체 A", "하체 A", "하체 A · 일정 이동", "상체 B", "하체 B", "가벼운 걷기", "휴식")
    UiCard {
        workoutDays.forEachIndexed { i, day ->
            val title = if (program == null) ui.get("w.day.$day", defaults[i]) else ui.get("w.planDay.$day", "휴식")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(day, fontSize = 14.sp, modifier = Modifier.width(20.dp))
                Column(Modifier.weight(1f)) { WorkoutTitle(title); MutedText(when { program != null -> if (title == "휴식") "계획된 휴식" else "다음 주 계획"; i < 2 -> "수행 완료"; i == 2 -> if (ui.flag("w.finished")) "운동 마무리" else "진행 중"; i == 6 -> "계획된 휴식"; else -> "유산소 30분 포함" }) }
                OutlinedButton(onClick = { if (program != null) { ui.set("w.draftProgram", program.id); ui.go("W04") } else if (i == 2) ui.go("W08") else { ui.set("w.moveFrom", day); ui.go("W14") } }, contentPadding = PaddingValues(horizontal = 12.dp), shape = RoundedCornerShape(12.dp)) { Text(if (i == 2 && program == null) "이어하기" else "편집", fontSize = 12.sp) }
            }
            if (i < 6) DividerLine()
        }
    }
    WorkoutNote("일정이 달라졌다면 앞으로 할 운동만 옮겨보세요. 이미 남긴 기록은 유지돼요.")
    UiButton("워밍업 · 스트레칭 설정", { ui.go("W17") }, primary = false)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProgramCatalog(ui: PreviewSession) {
    MutedText("기본 프로그램 6개")
    Text("내 생활에 맞는\n운동부터 골라요.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    MutedText("구성은 단순하게, 기록은 나에게 맞게.")
    WorkoutChips("목적", listOf("유지", "근육 증가", "체중 감량"), "w.programGoal", "근육 증가", ui)
    WorkoutChips("운동 장소", listOf("전체", "헬스장", "집"), "w.programPlace", "전체", ui)
    UiRow("주 4일 가능", "꾸준히 운동 중", "조건 변경", onClick = { ui.go("A06") })
    val goalIndex = listOf("유지", "근육 증가", "체중 감량").indexOf(ui.get("w.programGoal", "근육 증가")).coerceAtLeast(0)
    val place = ui.get("w.programPlace", "전체")
    val filtered = workoutPrograms.filter { place == "전체" || if (place == "집") it.id == "P05" else it.id != "P05" }.sortedBy { it.priorities[goalIndex] }
    SectionTitle("내 목표와 일정에 맞는 구성 · ${filtered.size}개")
    filtered.forEach { p ->
        UiCard(modifier = Modifier.clickable { ui.set("w.program", p.id); ui.go("W03") }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) { UiIcon(p.icon, Modifier.size(28.dp), DeepBlue); Column(Modifier.weight(1f)) { WorkoutTitle(p.name); MutedText(p.subtitle) }; UiIcon("ChevronRight", Modifier.size(18.dp)) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Badge(p.frequency); Badge(p.place) }
            MutedText(p.context)
            Text(workoutEvidence.getValue(p.sources.first()).title, color = DeepBlue, fontSize = 12.sp)
        }
    }
    MutedText("루틴로그가 구성한 프로그램이에요. 참고한 연구와 적용 범위는 상세에서 확인할 수 있어요.")
}

@Composable
private fun ProgramDetail(ui: PreviewSession) {
    val p = selectedProgram(ui)
    UiIcon(p.icon, Modifier.size(48.dp).background(Color(0xFFE7EDF6), RoundedCornerShape(16.dp)).padding(12.dp), DeepBlue)
    MutedText("루틴로그 구성 · 기본 프로그램")
    Text(p.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    MutedText(p.subtitle)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Badge(p.frequency); Badge(p.minutes) }
    UiCard { WorkoutTitle("이런 분에게 맞아요"); MutedText(p.context); KeyValue("장소·장비", p.place); KeyValue("내 목적", ui.get("w.programGoal", "근육 증가")) }
    WorkoutNote(when (ui.get("w.programGoal", "근육 증가")) { "유지" -> "최근 수행을 이어가며 내 일정에 맞게 운동량을 조정해요."; "체중 감량" -> "근력 기록과 실제 활동 시간을 함께 살펴봐요. 빠진 운동을 한꺼번에 채우지 않아요."; else -> "반복수와 세트, 운동이 얼마나 여유로웠는지 보며 하나씩 조정해요." })
    SectionTitle("기본 주간 구성")
    p.sessions.forEachIndexed { i, session -> UiCard { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Badge("${i + 1}"); Column { WorkoutTitle(session.name); MutedText(session.exercises.joinToString(" · ")) } } } }
    UiCard { WorkoutTitle("좋은 점"); BodyText(p.advantage); DividerLine(); WorkoutTitle("먼저 확인할 점"); BodyText(p.constraint) }
    WorkoutExpand("만든 곳과 참고 연구", "w.evidence", ui) {
        WorkoutTitle("운동표 작성: 루틴로그")
        MutedText("아래 연구의 운동 원칙을 참고했어요. 이 운동표 자체의 효과를 검증한 임상시험은 아니에요.")
        p.sources.forEach { key -> val evidence = workoutEvidence.getValue(key); WorkoutLink(evidence.title, evidence.url, ui); MutedText(evidence.type); BodyText(evidence.summary) }
    }
    WorkoutExpand("다음 주에는 어떻게 조정하나요?", "w.progressionOpen", ui) {
        listOf("일정 때문에 빠졌다면 날짜·구성을 먼저 바꿔요.", "같은 운동의 반복수·난도·불편함을 함께 봐요.", "목표 반복수의 상한까지 여러 번 여유 있게 했다면 조금 무거운 중량을 제안해요.", "피로가 높으면 유지·세트 축소도 선택할 수 있어요.").forEachIndexed { i, text -> BodyText("${i + 1}. $text") }
        MutedText("회복 속도는 사람마다 달라요. 내 상태를 보고 제안을 적용·수정·보류할 수 있어요.")
    }
    WorkoutExpand("다른 코치의 프로그램 보기", "w.coachesOpen", ui) {
        MutedText("제작자의 소개 페이지로 이동해요. 아래 프로그램은 앱에서 바로 적용할 수 없어요.")
        WorkoutLink("Mehdi · StrongLifts 5×5", "https://stronglifts.com/stronglifts-5x5/workout-program/", ui)
        WorkoutLink("Jim Wendler · 5/3/1", "https://www.jimwendler.com/blogs/jimwendler-com/101065094-5-3-1-for-a-beginner", ui)
    }
}

@Composable
private fun RoutineEditor(ui: PreviewSession) {
    val draft = workoutPrograms.firstOrNull { it.id == ui.get("w.draftProgram") }
    if (draft != null) {
        SectionTitle(draft.name)
        Chips(draft.sessions.map { it.name }, ui.get("w.routineName", draft.sessions.first().name)) { name ->
            ui.set("w.draftExercises.${draft.id}.${ui.get("w.programSession", "0")}", routineExercises(ui).joinToString("|"))
            val nextIndex = draft.sessions.indexOfFirst { it.name == name }
            ui.set("w.programSession", nextIndex.toString())
            ui.set("w.routineName", name)
            ui.set("w.routineExercises", ui.get("w.draftExercises.${draft.id}.$nextIndex", draft.sessions[nextIndex].exercises.joinToString("|")))
        }
        val index = ui.get("w.programSession", "0").toIntOrNull()?.coerceIn(draft.sessions.indices) ?: 0
        WorkoutChips("이 운동을 할 요일", workoutDays, "w.programDay.${draft.id}.$index", programDefaultDays(draft)[index], ui)
    }
    Input("루틴 이름", ui.get("w.routineName", "하체 A"), { ui.set("w.routineName", it) })
    UiCard {
        routineExercises(ui).forEachIndexed { index, name ->
            val e = exerciseCatalog.firstOrNull { it.name == name }
            UiRow(name, "${ui.get("w.plan.sets.$name", "3")}세트 · ${ui.get("w.plan.weight.$name", e?.weight ?: "미설정")} kg × ${ui.get("w.plan.reps.$name", e?.reps ?: "12")}회", icon = "Dumbbell", onClick = { setExercise(ui, name, plan = true) })
            if (ui.flag("w.reorder")) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { val items = routineExercises(ui).toMutableList(); if (index > 0) { val item = items.removeAt(index); items.add(index - 1, item); ui.set("w.routineExercises", items.joinToString("|")) } }, enabled = index > 0) { Text("위로") }
                OutlinedButton(onClick = { val items = routineExercises(ui).toMutableList(); if (index < items.lastIndex) { val item = items.removeAt(index); items.add(index + 1, item); ui.set("w.routineExercises", items.joinToString("|")) } }, enabled = index < routineExercises(ui).lastIndex) { Text("아래로") }
                TextButton(onClick = { ui.set("w.routineExercises", routineExercises(ui).filterIndexed { i, _ -> i != index }.joinToString("|")) }) { Text("제외", color = MaterialTheme.colorScheme.error) }
            }
            if (index < routineExercises(ui).lastIndex) DividerLine()
        }
    }
    WorkoutButtons("+ 운동 추가", { ui.set("w.addContext", "routine"); ui.go("W05") }, if (ui.flag("w.reorder")) "순서 완료" else "순서 바꾸기", { ui.toggle("w.reorder") })
    WorkoutChips("주간 배치", listOf("화·금", "월·목", "수·토", "매주 직접 배치"), "w.routineDays", "화·금", ui)
    WorkoutChips("중량·반복수 조정 방식", listOf("내가 직접 조절", "반복 범위 내 진행", "프로그램 규칙 사용"), "w.progression", "내가 직접 조절", ui)
    WorkoutExpand("세트 표기 · 기구 기준", "w.conventionOpen", ui) { BodyText("준비 세트와 본 세트는 따로 기록해요. 덤벨은 한쪽/합계, 스미스는 바 포함/원판만을 운동별로 확인해요.") }
}

@Composable
private fun ExerciseSearch(ui: PreviewSession) {
    Input("운동 이름", ui.get("w.exerciseQuery"), { ui.set("w.exerciseQuery", it) })
    Chips(listOf("전체", "하체", "상체", "맨몸"), ui.get("w.exerciseFilter", "전체")) { ui.set("w.exerciseFilter", it) }
    val query = ui.get("w.exerciseQuery").trim()
    val filter = ui.get("w.exerciseFilter", "전체")
    val custom = ui.get("w.customExercises").split('|').filter(String::isNotBlank).map { Exercise(it, ui.get("w.custom.$it.equipment", "기타"), ui.get("w.custom.$it.target", "미확인"), "전체", "0") }
    val found = (exerciseCatalog + custom).filter { it.name.contains(query, ignoreCase = true) && (filter == "전체" || it.group == filter || it.equipment == filter) }
    UiCard {
        if (found.isEmpty()) { WorkoutTitle("찾는 운동이 없어요"); MutedText("다른 이름으로 검색하거나 내 운동으로 등록해보세요.") }
        found.forEachIndexed { index, e ->
            UiRow(e.name, "${e.equipment} · ${e.target}", icon = "Dumbbell", onClick = {
                if (ui.get("w.addContext") == "replace") { ui.set("w.substitute", e.name); ui.go("W12") }
                else { ui.set("w.guideReturn", "W05"); setExercise(ui, e.name, "W06") }
            })
            if (index < found.lastIndex) DividerLine()
        }
    }
    WorkoutNote("같은 기구라도 동작에 맞는 운동을 골라주세요. 찾는 운동이 없으면 직접 등록할 수 있어요.")
}

@Composable
private fun ExerciseGuide(ui: PreviewSession) {
    ExerciseHeading(ui)
    UiCard { UiIcon("CirclePlay", Modifier.size(32.dp)); WorkoutTitle("이 운동의 동작 영상은 아직 없어요."); MutedText("기구 안내와 아래 기본 원칙을 확인해주세요.") }
    UiCard { WorkoutTitle("기본 움직임 원칙"); BodyText("1. 기구에 맞게 좌석과 패드를 조절해요.\n2. 몸통을 안정적으로 두고 움직여요.\n3. 무게를 제어하며 시작 위치로 돌아와요."); MutedText("개인별 가동 범위와 기구 설명을 확인해요.") }
    UiCard { WorkoutTitle("내 최근 기록"); KeyValue("최근 수행", "${weight(ui)} kg × ${reps(ui)}회 · ${totalSets(ui)}세트"); KeyValue("중량 표기", ui.get("w.custom.${activeName(ui)}.convention", "기구 표시 중량")); UiButton("세트 기록으로 돌아가기", { ui.set("w.setContext", "record"); ui.go("W09") }, primary = false) }
}

@Composable
private fun CustomExercise(ui: PreviewSession) {
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) ui.set("w.customPhoto", uri.toString()) }
    Input("운동 이름", ui.get("w.customName"), { ui.set("w.customName", it) })
    WorkoutChips("기구", listOf("머신", "덤벨", "바벨", "밴드", "맨몸", "기타"), "w.customEquipment", "머신", ui)
    WorkoutChips("주로 운동하는 부위", listOf("미확인", "대퇴사두", "햄스트링", "둔근", "가슴", "등", "어깨", "팔"), "w.customTarget", "미확인", ui)
    Input("함께 운동되는 부위 · 선택", ui.get("w.customSecondary"), { ui.set("w.customSecondary", it) })
    WorkoutChips("기록 방식", listOf("중량 × 반복", "반복만", "시간", "거리 + 시간"), "w.customRecord", "중량 × 반복", ui)
    WorkoutChips("무게를 적는 기준", listOf("기구 표시 중량", "덤벨 한쪽", "양쪽 합계", "바 포함", "원판만"), "w.customConvention", "기구 표시 중량", ui)
    UiRow("대표 사진 · 선택", if (ui.get("w.customPhoto").isBlank()) "사진 없이도 운동을 등록할 수 있어요." else "사진을 선택했어요 · 이번 체험에만 보관", if (ui.get("w.customPhoto").isBlank()) "선택" else "변경", "Plus", onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
}

@Composable
private fun WorkoutToday(ui: PreviewSession) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Badge("22분 경과"); Badge(if (ui.flag("w.finished")) "운동 마무리" else "${completedExercises(ui).size} / 5종목") }
    UiRow("워밍업 · 스트레칭", "${warmupNames(ui).size}동작 · ${warmupNames(ui).indices.count { ui.flag("w.warm.$it", it < 2) }}개 완료", "보기", "Check", onClick = { ui.go("W17") })
    val names = todayExercises(ui)
    UiCard {
        names.forEachIndexed { i, name ->
            val e = exerciseCatalog.firstOrNull { it.name == name }
            val done = i < 2 || ui.flag("w.done.$name")
            UiRow(name, if (name == "플랭크") "3세트 · 30초" else "${ui.get("w.plan.sets.$name", "3")}세트 · ${ui.get("w.weight.$name", e?.weight ?: "0")} kg", if (done) "완료 ✓" else if (name == activeName(ui)) "진행 중" else "예정", "Dumbbell", selected = name == activeName(ui), onClick = { setExercise(ui, name) })
            if (i < names.lastIndex) DividerLine()
        }
    }
    WorkoutButtons("종목 교체", { ui.go("W11") }, "오늘 일정 변경", { ui.go("W14") })
    UiRow("이번 주 변경 1건", "스쿼트 → ${ui.get("w.replacement", "레그프레스")}", "확인", onClick = { ui.go("W12") })
    if (ui.get("w.cardioSaved").isNotBlank()) UiRow("오늘 유산소", ui.get("w.cardioSaved"), "수정", "Footprints", onClick = { ui.go("W15") })
    UiButton("+ 유산소 기록", { ui.go("W15") }, primary = false)
    UiButton("오늘 운동 마치기", { ui.go("W16") }, primary = false)
}

@Composable
private fun SetRecorder(ui: PreviewSession) {
    val plan = ui.get("w.setContext") == "plan"
    ExerciseHeading(ui, guide = true)
    UiCard {
        KeyValue("오늘 계획", "${totalSets(ui)}세트 · ${ui.get("w.plan.weight.${activeName(ui)}", exercise(ui)?.weight ?: "0")} kg × ${ui.get("w.plan.reps.${activeName(ui)}", exercise(ui)?.reps ?: "12")}회")
        KeyValue("최근 수행", if (activeName(ui) == "시티드 레그컬") "35 kg × 12, 12, 10회" else "${exercise(ui)?.weight ?: "0"} kg · 내 중량 기준 확인")
    }
    val recordType = ui.get("w.custom.${activeName(ui)}.record", if (activeName(ui) == "플랭크") "시간" else "중량 × 반복")
    if (recordType == "중량 × 반복") Stepper("중량 · 2.5 kg씩 조절", weight(ui), "kg", { ui.set("w.weight.${activeName(ui)}", it) }, step = 2.5)
    Stepper(if (recordType == "시간" || recordType == "거리 + 시간") "시간" else "횟수", reps(ui), if (recordType == "시간" || recordType == "거리 + 시간") "초" else "회", { ui.set("w.reps.${activeName(ui)}", it) })
    if (recordType == "거리 + 시간") Input("거리", ui.get("w.distance.${activeName(ui)}"), { ui.set("w.distance.${activeName(ui)}", it) }, "km", numeric = true)
    if (plan) {
        Stepper("본 세트", totalSets(ui).toString(), "세트", { ui.set("w.plan.sets.${activeName(ui)}", it) })
        Stepper("세트 사이 휴식", ui.get("w.plan.rest.${activeName(ui)}", "90"), "초", { ui.set("w.plan.rest.${activeName(ui)}", it) }, step = 15.0)
    } else {
        UiCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { MutedText("본 세트"); MutedText("계획"); MutedText("실제") }
            DividerLine()
            (1..totalSets(ui)).forEach { set ->
                val saved = ui.get("w.actual.${activeName(ui)}.$set", if (activeName(ui) == "시티드 레그컬" && set == 1) "35 × 12" else "")
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (set == currentSet(ui)) Celery.copy(alpha = .23f) else Color.Transparent).padding(vertical = 12.dp, horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${set}세트", fontSize = 13.sp)
                    Text(if (recordType == "시간") "${reps(ui)}초" else "${ui.get("w.plan.weight.${activeName(ui)}", exercise(ui)?.weight ?: "0")} × ${ui.get("w.plan.reps.${activeName(ui)}", exercise(ui)?.reps ?: "12")}", fontSize = 13.sp)
                    Text(if (saved.isNotBlank()) "$saved ✓" else if (set == currentSet(ui)) "입력 중" else "예정", fontSize = 13.sp, color = if (saved.isNotBlank()) Color(0xFF537338) else Ink)
                }
            }
        }
        WorkoutButtons("불편함 · 메모", { ui.go("W13") }, "세트 생략", {
            ui.set("w.actual.${activeName(ui)}.${currentSet(ui)}", "생략")
            if (currentSet(ui) >= totalSets(ui)) { ui.set("w.done.${activeName(ui)}", "true"); ui.go("W08") } else ui.set("w.currentSet.${activeName(ui)}", (currentSet(ui) + 1).toString())
        })
        if (ui.flag("w.memoSaved.${activeName(ui)}")) WorkoutNote("메모를 남겼어요 · ${ui.get("w.discomfort.${activeName(ui)}", "불편함 미입력")}")
    }
}

@Composable
private fun RestTimer(ui: PreviewSession) {
    val restKey = "w.restDeadline"
    LaunchedEffect(Unit) { if (ui.get(restKey).toLongOrNull() == null) ui.set(restKey, (SystemClock.elapsedRealtime() + 47_000L).toString()) }
    val end = ui.get(restKey).toLongOrNull() ?: (SystemClock.elapsedRealtime() + 47_000)
    var remaining by remember(end) { mutableIntStateOf(max(0, ((end - SystemClock.elapsedRealtime() + 999) / 1000).toInt())) }
    LaunchedEffect(end) { while (remaining > 0) { delay(200); remaining = max(0, ((end - SystemClock.elapsedRealtime() + 999) / 1000).toInt()) } }
    val duration = ui.get("w.restDuration", "90").toIntOrNull()?.coerceAtLeast(1) ?: 90
    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(238.dp)) {
            drawCircle(Color(0xFFDCE3DD), style = Stroke(7.dp.toPx()))
            drawArc(Celery, -90f, 360f * (remaining.toFloat() / duration).coerceIn(0f, 1f), false, style = Stroke(7.dp.toPx(), cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) { MutedText(if (remaining > 0) "남은 휴식" else "휴식 시간이 지났어요"); Text("${remaining / 60}:${(remaining % 60).toString().padStart(2, '0')}", fontSize = 58.sp, lineHeight = 66.sp, fontWeight = FontWeight.SemiBold); MutedText("설정 ${duration}초") }
    }
    WorkoutButtons("+30초", {
        ui.set(restKey, (max(end, SystemClock.elapsedRealtime()) + 30_000).toString()); ui.set("w.restDuration", (duration + 30).toString())
    }, "휴식 건너뛰기", { nextSet(ui) })
    UiCard {
        MutedText("방금 완료한 ${currentSet(ui)}세트")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { WorkoutTitle("${weight(ui)} kg × ${reps(ui)}회"); Badge("완료") }
        UiButton("기록 수정", { ui.set("w.editSet", currentSet(ui).toString()); ui.go("W09") }, primary = false)
    }
    KeyValue(if (currentSet(ui) < totalSets(ui)) "다음 · ${currentSet(ui) + 1}세트" else "본 세트 완료", "${weight(ui)} kg × ${reps(ui)}회")
    MutedText("휴식 시간은 내 상태에 맞게 조절해요.")
}

@Composable
private fun SubstitutePicker(ui: PreviewSession) {
    UiCard { MutedText("바꾸려던 운동"); WorkoutTitle("스미스 머신 스쿼트"); MutedText("내 목적: 대퇴사두 · 100 kg × 10회 × 3") }
    WorkoutChips("변경 이유 · 선택", listOf("기구 대기", "버거움", "시간 부족", "불편함", "기타"), "w.reason", "", ui)
    listOf("레그프레스" to "비슷한 부위 · 머신 사용", "프리 스쿼트" to "비슷한 부위 · 자세와 무게 조절 필요", "리버스 런지" to "비슷한 하체 부위 · 좌우 각각 기록").forEach { (name, detail) -> Choice(name, detail, ui.get("w.substitute", "레그프레스") == name, { ui.set("w.substitute", name) }) }
    UiButton("다른 운동 찾아보기", { ui.set("w.addContext", "replace"); ui.go("W05") }, primary = false)
}

@Composable
private fun SubstituteConfirm(ui: PreviewSession) {
    UiCard { MutedText("원래 계획"); WorkoutTitle("스미스 머신 스쿼트"); MutedText("대퇴사두 목적 · 100 kg × 10회 × 3"); DividerLine(); MutedText("대체"); WorkoutTitle(ui.get("w.substitute", "레그프레스")); MutedText("운동하는 부위와 동작을 확인하고, 무게는 새로 정해주세요.") }
    WorkoutNote("이 운동의 최근 기록을 참고해요. 이전 운동의 무게를 그대로 사용하지 말고 조절해주세요.")
    Input("계획 중량", ui.get("w.subWeight", "120"), { ui.set("w.subWeight", it) }, "kg", numeric = true)
    Input("반복수", ui.get("w.subReps", "12"), { ui.set("w.subReps", it) }, "회", numeric = true)
    Input("세트", ui.get("w.subSets", "3"), { ui.set("w.subSets", it) }, "세트", numeric = true)
    listOf("오늘만 교체", "기본 루틴에도 반영").forEach { Choice(it, selected = ui.get("w.subScope", "오늘만 교체") == it, onClick = { ui.set("w.subScope", it) }) }
}

@Composable
private fun ExerciseMemo(ui: PreviewSession) {
    val name = activeName(ui)
    UiCard { MutedText("메모를 남길 운동"); WorkoutTitle("$name · ${currentSet(ui)}세트") }
    Input("불편한 부위 · 선택", ui.get("w.discomfortPart.$name"), { ui.set("w.discomfortPart.$name", it) })
    MutedText("예: 오른쪽 무릎 앞쪽")
    WorkoutChips("불편한 정도", listOf("없음", "조금", "동작을 바꿈", "중단함"), "w.discomfort.$name", "", ui)
    WorkoutChips("마지막 세트 난도", listOf("여유", "적당", "버거움"), "w.difficulty.$name", "", ui)
    WorkoutExpand("몇 번 더 할 수 있었나요? · 선택", "w.rirOpen", ui) { Input("추가로 가능했을 반복수", ui.get("w.rir.$name"), { ui.set("w.rir.$name", it) }, "회", numeric = true) }
    Input("신경 쓸 점 · 메모", ui.get("w.memo.$name"), { ui.set("w.memo.$name", it) }, multiline = true)
}

@Composable
private fun MoveWorkout(ui: PreviewSession) {
    val from = ui.get("w.moveFrom", "목")
    val target = ui.get("w.moveTo", if (from == "금") "토" else "금")
    UiCard { MutedText("옮길 운동"); WorkoutTitle("${from}요일 · ${dayRoutine(ui, from)}"); MutedText("이번 주 운동 일정을 조정해요.") }
    WorkoutChips("옮길 요일", workoutDays.filter { it != from }, "w.moveTo", if (from == "금") "토" else "금", ui)
    UiCard { WorkoutTitle("${target}요일에는 ${dayRoutine(ui, target)}이 있어요."); MutedText("가능한 시간과 오늘 상태를 확인해주세요."); Chips(listOf("서로 맞바꾸기", "같은 날 배치"), ui.get("w.moveKind", "서로 맞바꾸기")) { ui.set("w.moveKind", it) } }
    listOf("이번 주만", "기본 반복 일정도 변경").forEach { Choice(it, selected = ui.get("w.moveScope", "이번 주만") == it, onClick = { ui.set("w.moveScope", it) }) }
    WorkoutNote("못 한 운동은 내 일정과 상태에 맞춰 옮겨도 괜찮아요. 이미 완료한 기록은 그대로 남아요.")
}

@Composable
private fun FinishWorkout(ui: PreviewSession) {
    val completed = completedExercises(ui)
    val remaining = todayExercises(ui).filterNot { it in completed }
    val actual = ui.values.filter { (key, value) -> key.startsWith("w.actual.") && value.isNotBlank() && value != "생략" }.size
    val initialCurlSet = if (ui.get("w.actual.시티드 레그컬.1").isBlank()) 1 else 0
    Text("오늘 한 만큼\n기록했어요.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    UiCard { KeyValue("실제 수행", "${completed.size} / 5종목 · ${6 + initialCurlSet + actual} 본 세트"); KeyValue("오늘 운동 시간", "22분"); KeyValue("변경", "스쿼트 → ${ui.get("w.replacement", "레그프레스")}"); KeyValue("남은 운동", remaining.joinToString(" · ").ifBlank { "모두 완료" }) }
    WorkoutChips("마무리하는 이유 · 선택", listOf("계획 조정", "시간 부족", "피로", "불편함", "기타"), "w.finishReason", "", ui)
    WorkoutNote("오늘 한 운동만 남겨요. 못 한 운동을 다음에 모두 채울 필요는 없어요.")
    UiButton("남은 운동 이어하기", { ui.go("W08") }, primary = false)
}

private fun warmupNames(ui: PreviewSession) = listOf("가벼운 걷기 · 5분", "고관절 준비 동작 · 1분", "개인 스트레칭 · 2분", "스쿼트 준비 세트 · 2세트") + ui.get("w.warmExtras").split('|').filter(String::isNotBlank)
@Composable
private fun Warmup(ui: PreviewSession) {
    MutedText("내가 자주 하는 준비 순서를 저장해두세요.")
    UiCard { warmupNames(ui).forEachIndexed { i, name -> Choice(name, selected = ui.flag("w.warm.$i", i < 2), onClick = { ui.toggle("w.warm.$i", i < 2) }) } }
    UiButton("+ 동작 추가", { ui.set("w.addContext", "warmup"); ui.go("W05") }, primary = false)
    Input("오늘 준비 메모", ui.get("w.warmMemo"), { ui.set("w.warmMemo", it) }, multiline = true)
    WorkoutNote("준비 운동은 본 운동 세트에 포함하지 않아요.")
}

private fun addExercise(ui: PreviewSession, name: String) {
    when (ui.get("w.addContext", "routine")) {
        "warmup" -> { ui.set("w.warmExtras", (ui.get("w.warmExtras").split('|').filter(String::isNotBlank) + name).distinct().joinToString("|")); ui.save("W17") }
        "replace" -> { ui.set("w.substitute", name); ui.go("W12") }
        else -> { ui.set("w.routineExercises", (routineExercises(ui) + name).distinct().joinToString("|")); ui.save("W04") }
    }
}
private fun completeSet(ui: PreviewSession) {
    val kg = weight(ui).toDoubleOrNull()
    val count = integer(reps(ui))
    if (kg == null || !kg.isFinite() || kg !in 0.0..1000.0 || count == null || count !in 1..3600) { ui.notify("중량과 횟수를 확인해주세요."); return }
    val name = activeName(ui)
    if (ui.get("w.setContext") == "plan") {
        val sets = integer(ui.get("w.plan.sets.$name", "3"))
        val rest = integer(ui.get("w.plan.rest.$name", "90"))
        if (sets == null || sets !in 1..12 || rest == null || rest !in 0..600) { ui.notify("세트는 1~12개, 휴식은 0~600초로 입력해주세요."); return }
        ui.set("w.plan.weight.$name", weight(ui)); ui.set("w.plan.reps.$name", reps(ui)); ui.save("W04"); return
    }
    val set = ui.get("w.editSet").toIntOrNull() ?: currentSet(ui)
    val timed = ui.get("w.custom.$name.record", if (name == "플랭크") "시간" else "중량 × 반복") in listOf("시간", "거리 + 시간")
    ui.set("w.actual.$name.$set", if (timed) "${reps(ui)}초" else "${weight(ui)} × ${reps(ui)}")
    if (ui.get("w.editSet").isNotBlank()) { ui.set("w.editSet", ""); ui.go("W10"); return }
    if (set >= totalSets(ui)) { ui.set("w.done.$name", "true"); ui.save("W08") }
    else { val duration = integer(ui.get("w.plan.rest.$name", "90"))?.coerceIn(0, 600) ?: 90; ui.set("w.restDuration", duration.toString()); ui.set("w.restDeadline", (SystemClock.elapsedRealtime() + duration * 1000L).toString()); ui.go("W10") }
}
private fun nextSet(ui: PreviewSession) { if (currentSet(ui) < totalSets(ui)) ui.set("w.currentSet.${activeName(ui)}", (currentSet(ui) + 1).toString()); ui.set("w.restDeadline", ""); ui.set("w.editSet", ""); ui.go("W09") }
private fun dayRoutine(ui: PreviewSession, day: String) = ui.get("w.day.$day", when (day) { "월" -> "상체 A"; "화", "수" -> "하체 A"; "목" -> "상체 B"; "금" -> "하체 B"; "토" -> "가벼운 걷기"; else -> "휴식" })
private fun programDefaultDays(program: WorkoutProgram) = when (program.sessions.size) { 4 -> listOf("월", "화", "목", "금"); 3 -> listOf("월", "수", "금"); else -> listOf("화", "금") }
private fun saveMove(ui: PreviewSession) {
    val from = ui.get("w.moveFrom", "목"); val to = ui.get("w.moveTo", if (from == "금") "토" else "금")
    if (from == to) { ui.notify("다른 요일을 골라주세요."); return }
    val first = dayRoutine(ui, from); val second = dayRoutine(ui, to)
    if (ui.get("w.moveKind", "서로 맞바꾸기") == "서로 맞바꾸기") { ui.set("w.day.$from", second); ui.set("w.day.$to", first) }
    else { ui.set("w.day.$from", "휴식"); ui.set("w.day.$to", if (second == "휴식") first else "$second + $first") }
    if (ui.get("w.moveScope", "이번 주만") == "기본 반복 일정도 변경") ui.set("w.routineDays", to)
    ui.save("W01")
}

@Composable private fun WorkoutTitle(text: String) { Text(text, fontWeight = FontWeight.SemiBold, color = Ink, fontSize = 16.sp, lineHeight = 24.sp) }
@Composable internal fun WorkoutNote(text: String) { Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFE3EAF2)).padding(16.dp)) { Text(text, color = Muted, fontSize = 13.sp, lineHeight = 21.sp) } }
@Composable private fun WorkoutButtons(first: String, onFirst: () -> Unit, second: String, onSecond: () -> Unit, secondPrimary: Boolean = false) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { UiButton(first, onFirst, primary = false, modifier = Modifier.weight(1f)); UiButton(second, onSecond, primary = secondPrimary, modifier = Modifier.weight(1f)) } }
@Composable internal fun WorkoutChips(title: String, options: List<String>, key: String, fallback: String, ui: PreviewSession) { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { SectionTitle(title); Chips(options, ui.get(key, fallback)) { ui.set(key, it) } } }
@Composable internal fun WorkoutExpand(title: String, key: String, ui: PreviewSession, content: @Composable ColumnScope.() -> Unit) { UiCard { Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { ui.toggle(key) }, verticalAlignment = Alignment.CenterVertically) { Text(title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 14.sp); UiIcon("ChevronRight", Modifier.size(20.dp).rotate(if (ui.flag(key)) -90f else 90f)) }; if (ui.flag(key)) { DividerLine(); content() } } }
@Composable internal fun WorkoutLink(label: String, url: String, ui: PreviewSession) { val uri = LocalUriHandler.current; TextButton(onClick = { runCatching { uri.openUri(url) }.onFailure { ui.notify("이 기기에서 링크를 열지 못했어요.") } }, contentPadding = PaddingValues(0.dp)) { Text("$label ↗", color = DeepBlue, textAlign = TextAlign.Start) } }
@Composable private fun ExerciseHeading(ui: PreviewSession, guide: Boolean = false) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { ExerciseGlyph(activeName(ui)); Column(Modifier.weight(1f)) { WorkoutTitle(activeName(ui)); MutedText("${exercise(ui)?.equipment ?: ui.get("w.custom.${activeName(ui)}.equipment", "운동")} · ${ui.get("w.custom.${activeName(ui)}.convention", "기구 표시 중량")}") }; if (guide) OutlinedButton(onClick = { ui.set("w.guideReturn", "W09"); ui.go("W06") }, contentPadding = PaddingValues(horizontal = 10.dp), shape = RoundedCornerShape(12.dp)) { Text("운동법", fontSize = 12.sp) } } }

@Composable
private fun ExerciseGlyph(name: String) {
    Canvas(Modifier.size(54.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFE6ECF1)).padding(8.dp)) {
        val c = Muted; val stroke = 1.7.dp.toPx()
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(c, Offset(size.width * x1, size.height * y1), Offset(size.width * x2, size.height * y2), stroke, StrokeCap.Round)
        drawCircle(c, size.width * .085f, Offset(size.width * .43f, size.height * .16f), style = Stroke(stroke))
        line(.43f, .28f, .43f, .59f)
        if (name.contains("컬") || name.contains("프레스")) { line(.43f, .59f, .76f, .62f); line(.76f, .62f, .81f, .86f); line(.43f, .39f, .69f, .48f); line(.17f, .31f, .17f, .67f); line(.17f, .67f, .61f, .67f); line(.3f, .67f, .3f, .92f) }
        else { line(.43f, .59f, .22f, .83f); line(.43f, .59f, .72f, .84f); line(.43f, .35f, .19f, .49f); line(.43f, .35f, .72f, .42f) }
        line(.08f, .93f, .93f, .93f)
    }
}

private data class WorkoutEvidence(val title: String, val type: String, val url: String, val summary: String)
private val workoutEvidence = mapOf(
    "currier2023" to WorkoutEvidence("Currier 등 · 2023", "체계적 문헌고찰·네트워크 메타분석", "https://pubmed.ncbi.nlm.nih.gov/37414459/", "건강한 성인의 여러 근력 구성에서 근력·근육 크기 개선을 관찰했어요. 개인에게 유일한 최적 루틴을 정한 연구는 아니에요."),
    "acsm2026" to WorkoutEvidence("ACSM · Currier 등 · 2026", "137개 체계적 문헌고찰 종합", "https://pubmed.ncbi.nlm.nih.gov/41843416/", "건강한 성인의 점진적 근력운동 원칙을 참고했어요. 여기의 종목 조합·첫 주 세트 수는 루틴로그가 구성한 초안이에요."),
    "ramos2024" to WorkoutEvidence("Ramos-Campo 등 · 2024", "14개 연구 · 392명 메타분석", "https://pubmed.ncbi.nlm.nih.gov/38595233/", "주간 운동량을 맞추면 전신과 분할 사이에 뚜렷한 평균 효과 차이가 없었어요. 선호와 일정도 함께 고려해요."),
    "binmahfoz2025" to WorkoutEvidence("Binmahfoz 등 · 2025", "25개 무작위 비교시험 메타분석", "https://pubmed.ncbi.nlm.nih.gov/40909191/", "과체중·비만 성인의 식이 감량 중 근력운동이 제지방 감소를 줄인 근거예요. 모든 체형·목표에 같은 결과를 보장하지 않아요."),
)
