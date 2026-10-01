package com.bapegg.routinlog.ui

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bapegg.routinlog.data.*
import com.bapegg.routinlog.domain.WorkoutNumbers
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

data class WorkoutUiState(
    val userId: String? = null, val units: String = "METRIC", val loading: Boolean = false,
    val busy: Boolean = false, val loaded: Boolean = false, val date: String = LocalDate.now().toString(),
    val exercises: List<ExerciseDto> = emptyList(), val routines: List<RoutineDto> = emptyList(),
    val plan: WorkoutPlanDto? = null, val days: List<WorkoutDayDto> = emptyList(),
    val routineDraft: RoutineDraft? = null, val history: List<WorkoutHistoryItem> = emptyList(),
    val setDrafts: Map<String, WorkoutSetDraft> = emptyMap(),
    val historyLoading: Boolean = false, val restDeadlineElapsedMs: Long? = null,
    val error: String? = null, val notice: String? = null,
) {
    val day get() = days.firstOrNull { it.date == date }
    val session get() = day?.session
}
data class RoutineDraft(val id: String, val name: String, val entries: List<RoutineDraftEntry>,
    val note: String, val version: Long?, val units: String)
data class RoutineDraftEntry(val id: String, val exercise: ExerciseSnapshot, val sets: List<PlannedSetDraft>,
    val restSeconds: String, val note: String)
data class PlannedSetDraft(val id: String, val weight: String = "", val reps: String = "",
    val seconds: String = "", val warmup: Boolean = false)
data class WorkoutSetDraft(val weight: String, val reps: String, val seconds: String,
    val units: String, val source: ActualSet)

/** IDs are server-validated UUIDs. A session prefix also permits scoped deletion. */
fun workoutSetDraftKey(sessionId: String, entryId: String, setId: String) = "$sessionId/$entryId/$setId"

/** Plans, editable routine forms and confirmed performance are intentionally separate. */
class WorkoutViewModel(private val repository: WorkoutDataSource,
    private val elapsedMillis: () -> Long = { SystemClock.elapsedRealtime() }) : ViewModel() {
    private val mutableState = MutableStateFlow(WorkoutUiState())
    val state = mutableState.asStateFlow()
    private var zone = ZoneId.systemDefault()
    private var generation = 0L
    private var historyGeneration = 0L
    private var readJob: Job? = null
    private var writeJob: Job? = null
    private var historyJob: Job? = null
    private val startIds = mutableMapOf<String, String>()

    fun bind(userId: String?, timeZone: String? = null, units: String = "METRIC") {
        val nextZone = timeZone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()
        val zoneChanged = nextZone != zone
        zone = nextZone
        if (state.value.userId == userId) {
            mutableState.update { it.copy(units = units) }
            if (zoneChanged && userId != null) refresh()
            return
        }
        generation++; historyGeneration++
        readJob?.cancel(); writeJob?.cancel(); historyJob?.cancel(); startIds.clear()
        mutableState.value = WorkoutUiState(userId = userId, units = units, date = LocalDate.now(zone).toString())
        if (userId != null) refresh()
    }

    fun refresh() {
        if (state.value.userId == null || state.value.busy) return
        readJob?.cancel()
        val epoch = ++generation
        val (from, to) = weekBounds(state.value.date)
        mutableState.update { it.copy(loading = true, error = null) }
        readJob = viewModelScope.launch {
            try {
                coroutineScope {
                    val exercises = async { repository.listExercises() }
                    val routines = async { repository.listRoutines() }
                    val plan = async { repository.getWorkoutPlan() }
                    val days = async { repository.getWorkoutDays(from, to) }
                    val loaded = WorkoutUiState(exercises = exercises.await(), routines = routines.await(), plan = plan.await(), days = days.await())
                    if (epoch == generation) mutableState.update { it.copy(exercises = loaded.exercises,
                        routines = loaded.routines, plan = loaded.plan, days = loaded.days, loaded = true) }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { if (epoch == generation) showError(error) }
            finally { if (epoch == generation) mutableState.update { it.copy(loading = false) } }
        }
    }
    fun loadDate(date: String) {
        if (state.value.busy) return
        val parsed = runCatching { LocalDate.parse(date) }.getOrNull()
        if (parsed == null || parsed !in LocalDate.of(1900, 1, 1)..LocalDate.now(zone).plusDays(366)) {
            fail("1900년부터 앞으로 1년 이내의 날짜를 선택해주세요."); return
        }
        historyGeneration++; historyJob?.cancel()
        mutableState.update { it.copy(date = date, days = emptyList(), loaded = false, history = emptyList(), historyLoading = false, restDeadlineElapsedMs = null) }
        refresh()
    }

    fun beginRoutine(id: String? = null) {
        if (!editable()) return
        val old = id?.let { wanted -> state.value.routines.firstOrNull { it.id == wanted } }
        if (id != null && old == null) { fail("루틴을 다시 불러온 뒤 선택해주세요."); return }
        if (old?.entries?.any { row -> state.value.exercises.none { it.id == row.exerciseId } } == true) {
            fail("루틴의 운동 정보가 변경됐어요. 다시 불러온 뒤 수정해주세요."); return
        }
        val units = state.value.units
        val entries = old?.entries.orEmpty().map { row -> RoutineDraftEntry(row.id,
            state.value.exercises.first { it.id == row.exerciseId }.snapshot(), row.sets.map { set ->
                PlannedSetDraft(set.id, set.weightKg?.let { display(it, units) }.orEmpty(), set.reps?.toString().orEmpty(),
                    set.durationSeconds?.toString().orEmpty(), set.warmup)
            }, row.restSeconds.toString(), row.note.orEmpty()) }
        mutableState.update { it.copy(routineDraft = RoutineDraft(old?.id ?: newId(), old?.name.orEmpty(), entries,
            old?.note.orEmpty(), old?.version, units), error = null) }
    }
    fun discardRoutine() { if (!state.value.busy) mutableState.update { it.copy(routineDraft = null, error = null) } }
    private fun editRoutine(change: (RoutineDraft) -> RoutineDraft) { if (!state.value.busy) mutableState.update { it.copy(routineDraft = it.routineDraft?.let(change)) } }
    fun setRoutineName(value: String) = editRoutine { it.copy(name = value) }
    fun setRoutineNote(value: String) = editRoutine { it.copy(note = value) }
    fun addRoutineExercise(exerciseId: String) {
        val exercise = state.value.exercises.firstOrNull { it.id == exerciseId } ?: return
        editRoutine { if (it.entries.size >= 40) it else it.copy(entries = it.entries + RoutineDraftEntry(newId(), exercise.snapshot(), listOf(PlannedSetDraft(newId())), "90", "")) }
    }
    fun removeRoutineEntry(entryId: String) = editRoutine { it.copy(entries = it.entries.filterNot { row -> row.id == entryId }) }
    fun moveRoutineEntry(entryId: String, direction: Int) = editRoutine { draft ->
        val rows = draft.entries.toMutableList(); val index = rows.indexOfFirst { it.id == entryId }
        val target = index + direction.coerceIn(-1, 1)
        if (index >= 0 && target in rows.indices) { val row = rows.removeAt(index); rows.add(target, row) }
        draft.copy(entries = rows)
    }
    private fun editRoutineEntry(entryId: String, change: (RoutineDraftEntry) -> RoutineDraftEntry) = editRoutine { it.copy(entries = it.entries.map { row -> if (row.id == entryId) change(row) else row }) }
    fun addRoutineSet(entryId: String) = editRoutineEntry(entryId) { if (it.sets.size >= 30) it else it.copy(sets = it.sets + (it.sets.lastOrNull()?.copy(id = newId()) ?: PlannedSetDraft(newId()))) }
    fun removeRoutineSet(entryId: String, setId: String) = editRoutineEntry(entryId) { if (it.sets.size <= 1) it else it.copy(sets = it.sets.filterNot { set -> set.id == setId }) }
    fun updateRoutineSet(entryId: String, setId: String, weight: String, reps: String, seconds: String, warmup: Boolean) = editRoutineEntry(entryId) { it.copy(sets = it.sets.map { set -> if (set.id == setId) PlannedSetDraft(setId, weight, reps, seconds, warmup) else set }) }
    fun setRoutineRest(entryId: String, value: String) = editRoutineEntry(entryId) { it.copy(restSeconds = value) }
    fun setRoutineEntryNote(entryId: String, value: String) = editRoutineEntry(entryId) { it.copy(note = value) }
    fun saveRoutine(onSaved: () -> Unit) {
        val draft = state.value.routineDraft ?: return
        val request = try {
            checkInput(draft.name.trim().length in 1..80 && '\u0000' !in draft.name, "루틴 이름은 1~80자로 입력해주세요.")
            checkInput(draft.entries.size in 1..40, "운동을 1~40개 담아주세요.")
            RoutineWrite(draft.name.trim(), draft.entries.map { row ->
                checkInput(row.sets.size in 1..30, "운동마다 세트는 1~30개로 정해주세요.")
                val rest = whole(row.restSeconds, 0..1800, "휴식은 0~1,800초로 입력해주세요.")
                RoutineEntry(row.id, row.exercise.id, row.sets.map { set ->
                    val values = metrics(row.exercise.recordType, set.weight, set.reps, set.seconds, draft.units, false)
                    PlannedSet(set.id, values.first, values.second, values.third, set.warmup)
                }, rest, validatedNote(row.note))
            }, validatedNote(draft.note), draft.version)
        } catch (error: DraftValidation) { fail(error.message.orEmpty()); return }
        mutate {
            val saved = confirmed { repository.saveRoutine(draft.id, request) }
            mutableState.update { it.copy(routines = it.routines.filterNot { row -> row.id == saved.id } + saved,
                routineDraft = null, notice = "루틴을 저장했어요. 요일 계획에 연결해 사용하세요.") }
            onSaved()
        }
    }
    fun deleteRoutine(routine: RoutineDto, onDeleted: () -> Unit = {}) = mutate {
        confirmed { repository.deleteRoutine(routine.id, routine.version) }
        mutableState.update { it.copy(routines = it.routines.filterNot { row -> row.id == routine.id },
            routineDraft = it.routineDraft?.takeUnless { draft -> draft.id == routine.id }, notice = "루틴을 삭제했어요. 지난 기록은 유지돼요.") }
        onDeleted()
    }
    fun saveExercise(id: String, write: ExerciseWrite, onSaved: () -> Unit) = mutate {
        val saved = confirmed { repository.saveExercise(id, write) }
        mutableState.update { it.copy(exercises = it.exercises.filterNot { row -> row.id == id } + saved, notice = "운동을 저장했어요.") }
        onSaved()
    }
    fun deleteExercise(exercise: ExerciseDto, onDeleted: () -> Unit = {}) = mutate {
        confirmed { repository.deleteExercise(exercise.id, exercise.version) }
        mutableState.update { it.copy(exercises = it.exercises.filterNot { row -> row.id == exercise.id }, notice = "운동을 삭제했어요. 지난 기록은 유지돼요.") }
        onDeleted()
    }
    fun savePlan(write: WorkoutPlanWrite, onSaved: () -> Unit) = mutate {
        val saved = confirmed { repository.saveWorkoutPlan(write) }
        mutableState.update { it.copy(plan = saved, notice = "요일별 루틴을 저장했어요.") }
        reloadDaysAfterSave(); onSaved()
    }
    fun saveOverride(date: String, write: WorkoutOverrideWrite, onSaved: () -> Unit) = mutate {
        val saved = confirmed { repository.saveWorkoutOverride(date, write) }
        mutableState.update { it.copy(days = it.days.map { day -> if (day.date == date) day.copy(planned = saved.workout, `override` = saved) else day }, notice = "이 날짜의 계획을 저장했어요.") }
        onSaved()
    }
    fun deleteOverride(date: String, version: Long, onSaved: () -> Unit) = mutate {
        confirmed { repository.deleteWorkoutOverride(date, version) }
        mutableState.update { it.copy(notice = "기본 요일 계획으로 돌렸어요.") }
        reloadDaysAfterSave(); onSaved()
    }
    private suspend fun reloadDaysAfterSave() {
        try {
            val (from, to) = weekBounds(state.value.date)
            val days = confirmed { repository.getWorkoutDays(from, to) }
            mutableState.update { it.copy(days = days) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { mutableState.update { it.copy(days = emptyList(), loaded = false, error = "저장은 완료했지만 새 일정을 불러오지 못했어요. 다시 불러와주세요.") } }
    }

    fun startSession(onStarted: () -> Unit) {
        if (!editable()) return
        if (state.value.session != null) { onStarted(); return }
        val date = state.value.date
        if (LocalDate.parse(date) > LocalDate.now(zone)) { fail("미래 날짜에는 계획만 설정할 수 있어요."); return }
        val id = startIds.getOrPut(date) { newId() }
        mutate {
            val saved = confirmed { repository.startWorkoutSession(id, WorkoutStartWrite(date)) }
            confirmSession(saved); onStarted()
        }
    }
    fun addSessionExercise(exerciseId: String, onSaved: () -> Unit = {}) {
        val session = state.value.session ?: return
        val exercise = state.value.exercises.firstOrNull { it.id == exerciseId } ?: return
        val extra = WorkoutEntryWrite(newId(), exerciseId = exercise.id, sets = listOf(ActualSet(newId())))
        persistSession(session, session.write(entries = session.entries.map { it.write() } + extra), "운동을 추가했어요.", onSaved)
    }
    fun addSessionSet(entryId: String, onSaved: () -> Unit = {}) = editSessionEntry(entryId, "세트를 추가했어요.", onSaved) {
        checkInput(it.sets.size < 30, "세트는 30개까지 기록할 수 있어요.")
        it.copy(sets = it.sets + ActualSet(newId()))
    }
    fun removeSessionSet(entryId: String, setId: String, onSaved: () -> Unit = {}) = editSessionEntry(entryId, "세트를 삭제했어요.", onSaved) {
        checkInput(it.sets.size > 1, "마지막 세트는 건너뛰거나 운동 항목을 삭제해주세요.")
        it.copy(sets = it.sets.filterNot { set -> set.id == setId })
    }
    fun removeSessionEntry(entryId: String, onSaved: () -> Unit = {}) {
        val session = state.value.session ?: return
        persistSession(session, session.write(entries = session.entries.filterNot { it.id == entryId }.map { it.write() }), "이 운동 기록을 삭제했어요.", onSaved)
    }
    fun skipPendingSets(entryId: String, onSaved: () -> Unit = {}) = editSessionEntry(entryId, "남은 세트를 건너뛴 것으로 기록했어요.", onSaved) {
        it.copy(sets = it.sets.map { set -> if (set.status == "PENDING") set.copy(status = "SKIPPED") else set })
    }
    fun prepareSetDraft(entryId: String, setId: String) {
        if (!editable()) return
        val current = state.value
        val session = current.session ?: return
        val entry = session.entries.firstOrNull { it.id == entryId } ?: return
        val set = entry.sets.firstOrNull { it.id == setId } ?: return
        val key = workoutSetDraftKey(session.id, entryId, setId)
        if (key in current.setDrafts) return
        val draft = setDraft(session, entry, set, current.units)
        mutableState.update { it.copy(setDrafts = it.setDrafts + (key to draft)) }
    }
    fun updateSetDraft(entryId: String, setId: String, weight: String, reps: String, seconds: String) {
        if (!editable()) return
        prepareSetDraft(entryId, setId)
        val session = state.value.session ?: return
        val key = workoutSetDraftKey(session.id, entryId, setId)
        val draft = state.value.setDrafts[key] ?: return
        mutableState.update { it.copy(setDrafts = it.setDrafts + (key to draft.copy(weight = weight, reps = reps, seconds = seconds))) }
    }
    fun resolveSetDraft(entryId: String, setId: String, useSaved: Boolean) {
        if (!editable()) return
        val current = state.value
        val session = current.session ?: return
        val entry = session.entries.firstOrNull { it.id == entryId } ?: return
        val set = entry.sets.firstOrNull { it.id == setId } ?: return
        val key = workoutSetDraftKey(session.id, entryId, setId)
        val draft = current.setDrafts[key] ?: return
        val resolved = if (useSaved) setDraft(session, entry, set, current.units) else draft.copy(source = set)
        mutableState.update { it.copy(setDrafts = it.setDrafts + (key to resolved), error = null) }
    }
    private fun setDraft(session: WorkoutSessionDto, entry: WorkoutEntryDto, set: ActualSet, units: String): WorkoutSetDraft {
        val planned = session.planned?.entries?.firstOrNull { it.id == entry.plannedEntryId }
        // An unchanged ID alone is insufficient if its recording convention has changed.
        val sameExercise = planned?.exercise?.let { it.id == entry.exercise.id &&
            it.recordType == entry.exercise.recordType && it.loadConvention == entry.exercise.loadConvention } == true
        val planSet = planned?.sets?.firstOrNull { it.id == set.planSetId }?.takeIf { sameExercise }
        return WorkoutSetDraft(
            (set.weightKg ?: planSet?.weightKg)?.let { display(it, units) }.orEmpty(),
            (set.reps ?: planSet?.reps)?.toString().orEmpty(),
            (set.durationSeconds ?: planSet?.durationSeconds)?.toString().orEmpty(), units, set)
    }
    fun recordSet(entryId: String, setId: String, weight: String, reps: String, seconds: String,
        status: String, units: String, onSaved: () -> Unit) {
        val session = state.value.session ?: return
        val entry = session.entries.firstOrNull { it.id == entryId } ?: return
        val set = entry.sets.firstOrNull { it.id == setId } ?: return
        val key = workoutSetDraftKey(session.id, entryId, setId)
        val draft = state.value.setDrafts[key]
        if (draft != null && draft.source != set) {
            fail("서버의 세트 기록이 바뀌었어요. 저장된 값을 사용할지, 입력한 값을 유지할지 먼저 선택해주세요."); return
        }
        val changed = try {
            checkInput(status in setOf("DONE", "PENDING", "SKIPPED"), "세트 상태를 확인해주세요.")
            val values = if (status == "DONE") metrics(entry.exercise.recordType, weight, reps, seconds, draft?.units ?: units, true) else Triple(null, null, null)
            set.copy(status = status, weightKg = values.first, reps = values.second, durationSeconds = values.third)
        } catch (error: DraftValidation) { fail(error.message.orEmpty()); return }
        editSessionEntry(entryId, if (status == "DONE") "세트를 기록했어요." else "세트 상태를 바꿨어요.", {
            mutableState.update { it.copy(setDrafts = it.setDrafts - key,
                restDeadlineElapsedMs = if (status == "DONE" && it.session?.status == "IN_PROGRESS" && entry.restSeconds > 0) elapsedMillis() + entry.restSeconds * 1000L else null) }
            onSaved()
        }) { it.copy(sets = it.sets.map { old -> if (old.id == setId) changed else old }) }
    }
    fun replaceEntry(entryId: String, exerciseId: String, reason: String, onSaved: () -> Unit) {
        val session = state.value.session ?: return
        val old = session.entries.firstOrNull { it.id == entryId } ?: return
        val exercise = state.value.exercises.firstOrNull { it.id == exerciseId } ?: return
        if (reason.isBlank() || reason.length > 1000 || '\u0000' in reason) { fail("운동을 바꾼 이유를 1~1,000자로 남겨주세요."); return }
        if (old.exercise.id == exerciseId) { fail("현재 운동과 다른 운동을 선택해주세요."); return }
        val hasDone = old.sets.any { it.status == "DONE" }
        val pending = old.sets.filter { it.status == "PENDING" }.ifEmpty { if (hasDone) emptyList() else old.sets }
        val sets = pending.map { ActualSet(newId(), it.planSetId) }.ifEmpty { listOf(ActualSet(newId())) }
        val replacement = WorkoutEntryWrite(if (hasDone) newId() else old.id, old.plannedEntryId, exercise.id,
            sets, old.restSeconds, old.note, reason.trim())
        val entries = session.entries.flatMap { row ->
            when {
                row.id != entryId -> listOf(row.write())
                hasDone -> listOf(row.copy(sets = row.sets.map { if (it.status == "PENDING") it.copy(status = "SKIPPED") else it }).write(), replacement)
                else -> listOf(replacement)
            }
        }
        persistSession(session, session.write(entries), "운동 변경을 기록했어요. 원래 계획은 유지돼요.", onSaved)
    }
    fun saveEntryNote(entryId: String, note: String, onSaved: () -> Unit) = editSessionEntry(entryId, "운동 메모를 저장했어요.", onSaved) { it.copy(note = validatedNote(note)) }
    fun saveSessionNote(note: String, onSaved: () -> Unit) {
        val session = state.value.session ?: return
        val value = try { validatedNote(note) } catch (error: DraftValidation) { fail(error.message.orEmpty()); return }
        persistSession(session, session.write(note = value), "운동 메모를 저장했어요.", onSaved)
    }
    fun finishSession(onSaved: () -> Unit) {
        val session = state.value.session ?: return
        persistSession(session, session.write(status = "COMPLETED"), "운동 기록을 마무리했어요.", onSaved)
    }
    fun finishSession(note: String, onSaved: () -> Unit) {
        val session = state.value.session ?: return
        val value = try { validatedNote(note) } catch (error: DraftValidation) { fail(error.message.orEmpty()); return }
        persistSession(session, session.write(status = "COMPLETED", note = value), "운동 기록을 마무리했어요.", onSaved)
    }
    fun reopenSession(onSaved: () -> Unit) {
        val session = state.value.session ?: return
        persistSession(session, session.write(status = "IN_PROGRESS"), "운동 기록을 다시 열었어요.", onSaved)
    }
    fun deleteSession(onDeleted: () -> Unit) {
        val session = state.value.session ?: return
        mutate {
            confirmed { repository.deleteWorkoutSession(session.id, session.version) }
            startIds.remove(session.date)
            mutableState.update { it.copy(days = it.days.map { day -> if (day.date == session.date) day.copy(session = null) else day },
                setDrafts = it.setDrafts.filterKeys { key -> !key.startsWith("${session.id}/") },
                restDeadlineElapsedMs = null, notice = "이 날짜의 운동 기록을 삭제했어요.") }
            onDeleted()
        }
    }
    private fun editSessionEntry(entryId: String, message: String, onSaved: () -> Unit, change: (WorkoutEntryDto) -> WorkoutEntryDto) {
        val session = state.value.session ?: return
        val entries = try { session.entries.map { if (it.id == entryId) change(it) else it }.map { it.write() } }
        catch (error: DraftValidation) { fail(error.message.orEmpty()); return }
        persistSession(session, session.write(entries), message, onSaved)
    }
    private fun persistSession(session: WorkoutSessionDto, write: WorkoutSessionWrite, message: String, onSaved: () -> Unit) = mutate {
        val saved = confirmed { repository.saveWorkoutSession(session.id, write) }
        confirmSession(saved)
        mutableState.update { it.copy(notice = message, restDeadlineElapsedMs = if (saved.status == "COMPLETED") null else it.restDeadlineElapsedMs) }
        onSaved()
    }
    private fun confirmSession(saved: WorkoutSessionDto) = mutableState.update { it.copy(days = it.days.map { day -> if (day.date == saved.date) day.copy(session = saved) else day }) }

    fun loadHistory(exerciseId: String) {
        if (state.value.userId == null) return
        historyJob?.cancel()
        val epoch = ++historyGeneration
        val date = state.value.date
        mutableState.update { it.copy(history = emptyList(), historyLoading = true) }
        historyJob = viewModelScope.launch {
            try {
                val history = repository.getWorkoutHistory(exerciseId, date)
                if (epoch == historyGeneration) mutableState.update { it.copy(history = history) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { if (epoch == historyGeneration) showError(error) }
            finally { if (epoch == historyGeneration) mutableState.update { it.copy(historyLoading = false) } }
        }
    }
    fun extendRest(seconds: Int) {
        val deadline = state.value.restDeadlineElapsedMs ?: return
        mutableState.update { it.copy(restDeadlineElapsedMs = maxOf(deadline, elapsedMillis()) + seconds.coerceIn(0, 300) * 1000L) }
    }
    fun finishRest() = mutableState.update { it.copy(restDeadlineElapsedMs = null) }
    fun clearError() = mutableState.update { it.copy(error = null) }
    fun clearNotice() = mutableState.update { it.copy(notice = null) }
    private fun editable() = state.value.userId != null && state.value.loaded && !state.value.loading && !state.value.busy
    private fun mutate(block: suspend () -> Unit) {
        if (!editable()) return
        val epoch = generation
        mutableState.update { it.copy(busy = true, error = null) }
        writeJob = viewModelScope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { if (epoch == generation) showError(error) }
            finally { if (epoch == generation) mutableState.update { it.copy(busy = false) } }
        }
    }
    private suspend fun <T> confirmed(call: suspend () -> T): T {
        val value = call(); currentCoroutineContext().ensureActive(); return value
    }
    private fun fail(message: String) = mutableState.update { it.copy(error = message) }
    private fun showError(error: Exception) = fail(when (error) {
        is AccountException -> error.userMessage
        is DraftValidation -> error.message.orEmpty()
        else -> "요청을 완료하지 못했어요. 연결을 확인하고 다시 시도해주세요."
    })
    private fun weekBounds(date: String): Pair<String, String> {
        val selected = LocalDate.parse(date)
        val from = selected.minusDays(selected.dayOfWeek.value - 1L)
        return from.toString() to minOf(from.plusDays(6), LocalDate.now(zone).plusDays(366)).toString()
    }
    private fun metrics(type: String, weight: String, reps: String, seconds: String, units: String, required: Boolean): Triple<BigDecimal?, Int?, Int?> {
        fun weightValue(): BigDecimal? {
            if (!required && weight.isBlank()) return null
            val normalized = weight.trim().replace(',', '.')
            checkInput(Regex("^[0-9]{1,5}(\\.[0-9]{1,3})?$").matches(normalized), "중량은 0 이상, 소수 셋째 자리까지 입력해주세요.")
            checkInput(units in setOf("METRIC", "IMPERIAL"), "중량 단위를 확인해주세요.")
            val value = WorkoutNumbers.displayToKg(normalized.toBigDecimal(), units)
            checkInput(value <= BigDecimal("2000"), "중량의 입력 범위를 확인해주세요.")
            return value
        }
        fun intValue(text: String, range: IntRange, label: String): Int? = if (!required && text.isBlank()) null else whole(text, range, label)
        return when (type) {
            "WEIGHT_REPS" -> Triple(weightValue(), intValue(reps, 1..1000, "횟수는 1~1,000회로 입력해주세요."), null)
            "REPS" -> Triple(null, intValue(reps, 1..1000, "횟수는 1~1,000회로 입력해주세요."), null)
            "DURATION" -> Triple(null, null, intValue(seconds, 1..86400, "시간은 1~86,400초로 입력해주세요."))
            else -> throw DraftValidation("운동의 기록 유형을 확인해주세요.")
        }
    }
    private fun whole(value: String, range: IntRange, message: String): Int {
        val number = value.trim().toIntOrNull(); checkInput(number != null && number in range, message); return number!!
    }
    private fun validatedNote(value: String): String? { checkInput(value.length <= 1000 && '\u0000' !in value, "메모는 1,000자까지 입력해주세요."); return value.trim().ifBlank { null } }
    private fun checkInput(condition: Boolean, message: String) { if (!condition) throw DraftValidation(message) }
    private class DraftValidation(message: String) : IllegalArgumentException(message)
    private fun newId() = UUID.randomUUID().toString()
    private fun display(kg: BigDecimal, units: String) = WorkoutNumbers.kgToDisplay(kg, units).stripTrailingZeros().toPlainString()
    private fun ExerciseDto.snapshot() = ExerciseSnapshot(id, name, equipment, target, recordType, loadConvention)
    private fun WorkoutEntryDto.write() = WorkoutEntryWrite(id, plannedEntryId, exercise.id, sets, restSeconds, note, replacementReason)
    private fun WorkoutSessionDto.write(entries: List<WorkoutEntryWrite> = this.entries.map { it.write() }, status: String = this.status, note: String? = this.note) = WorkoutSessionWrite(entries, status, note, version)
}
