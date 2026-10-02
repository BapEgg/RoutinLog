package com.bapegg.routinlog.ui

import androidx.lifecycle.viewModelScope
import com.bapegg.routinlog.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeWorkouts
    private lateinit var viewModel: WorkoutViewModel
    private var elapsed = 10_000L
    private val today get() = LocalDate.now(ZoneOffset.UTC).toString()

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeWorkouts()
        viewModel = WorkoutViewModel(repository) { elapsed }
    }
    @After fun tearDown() {
        viewModel.viewModelScope.cancel()
        Dispatchers.resetMain()
    }

    @Test fun `catalog is lazy cached searchable and import preserves draft without inventing targets`()=runTest(dispatcher) {
        load();viewModel.beginRoutine();viewModel.setRoutineName("내 초안")
        assertTrue(repository.catalogOwners.isEmpty())
        viewModel.loadCatalog();advanceUntilIdle();viewModel.loadCatalog();advanceUntilIdle()
        assertEquals(listOf("account-a"),repository.catalogOwners)
        assertTrue(catalogItem.matches("db PRESS","가슴"));assertTrue(catalogItem.matches("덤벨프레스","전체"));assertFalse(catalogItem.matches("press","하체"))
        var saved:ExerciseDto?=null
        viewModel.importCatalog("basic"){saved=it};advanceUntilIdle()
        assertEquals(repository.imported,saved);assertEquals("내 초안",viewModel.state.value.routineDraft?.name)
        viewModel.addRoutineExercise(saved!!.id)
        val set=viewModel.state.value.routineDraft!!.entries.single().sets.single()
        assertEquals("",set.weight);assertEquals("",set.reps)
        viewModel.importCatalog("basic"){};advanceUntilIdle();assertEquals(1,viewModel.state.value.exercises.count { it.id==saved!!.id })
    }
    @Test fun `catalog load and import failures keep personal data and allow retry`()=runTest(dispatcher) {
        load();repository.catalogFailure=failure();viewModel.loadCatalog();advanceUntilIdle()
        assertNotNull(viewModel.state.value.catalogError);assertEquals(2,viewModel.state.value.exercises.size)
        repository.catalogFailure=null;viewModel.loadCatalog(true);advanceUntilIdle();assertNull(viewModel.state.value.catalogError)
        repository.catalogFailure=failure();viewModel.importCatalog("basic"){fail("should not navigate")};advanceUntilIdle()
        assertNotNull(viewModel.state.value.error);assertEquals(2,viewModel.state.value.exercises.size)
        repository.catalogFailure=null;viewModel.importCatalog("basic"){};advanceUntilIdle();assertEquals(3,viewModel.state.value.exercises.size)
    }
    @Test fun `late catalog and import responses cannot refill another account`()=runTest(dispatcher) {
        load();repository.catalogGate=CompletableDeferred();viewModel.loadCatalog();runCurrent()
        viewModel.bind(null);repository.catalogGate!!.complete(ExerciseCatalogDto("old","old","https://example.com",listOf(catalogItem)));advanceUntilIdle()
        assertNull(viewModel.state.value.catalog);assertFalse(viewModel.state.value.catalogLoading)
        repository.catalogGate=null;load();viewModel.loadCatalog();advanceUntilIdle()
        repository.importGate=CompletableDeferred();viewModel.importCatalog("basic"){fail("late navigation")};runCurrent()
        viewModel.bind(null);repository.importGate!!.complete(repository.imported);advanceUntilIdle()
        assertTrue(viewModel.state.value.exercises.isEmpty());assertNull(viewModel.state.value.catalog)
    }

    @Test fun `old account read cannot refill a new account after cancellation`() = runTest(dispatcher) {
        val pending = CompletableDeferred<List<WorkoutDayDto>>()
        repository.nextDaysGate = pending
        viewModel.bind("account-a", "UTC")
        runCurrent()
        repository.exercises = listOf(exerciseB)
        viewModel.bind("account-b", "UTC")
        advanceUntilIdle()
        pending.complete(listOf(WorkoutDayDto(today, session = session())))
        advanceUntilIdle()
        assertEquals("account-b", viewModel.state.value.userId)
        assertEquals(exerciseB, viewModel.state.value.exercises.single())
        assertNull(viewModel.state.value.session)
    }

    @Test fun `late noncancellable write response cannot repopulate signed-out state or navigate`() = runTest(dispatcher) {
        repository.sessions[today] = session()
        load()
        val pending = CompletableDeferred<WorkoutSessionDto>()
        repository.nextSaveGate = pending
        repository.ignoreSaveCancellation = true
        var navigated = false
        viewModel.recordSet("entry-a", "actual-1", "80", "8", "", "DONE", "METRIC") { navigated = true }
        runCurrent()
        assertTrue(viewModel.state.value.busy)
        viewModel.bind(null)
        pending.complete(session(version = 1))
        advanceUntilIdle()
        assertNull(viewModel.state.value.userId)
        assertTrue(viewModel.state.value.days.isEmpty())
        assertNull(viewModel.state.value.restDeadlineElapsedMs)
        assertFalse(navigated)
        assertFalse(viewModel.state.value.busy)
    }

    @Test fun `routine draft survives refresh and keeps captured pounds after account unit change`() = runTest(dispatcher) {
        load("IMPERIAL")
        viewModel.beginRoutine("routine-a")
        viewModel.updateRoutineSet("planned-entry", "plan-1", "100", "8", "", false)
        viewModel.setRoutineNote("아직 저장하지 않은 메모")
        val draft = viewModel.state.value.routineDraft
        viewModel.bind("account-a", "UTC", "METRIC")
        viewModel.refresh()
        advanceUntilIdle()
        assertEquals("METRIC", viewModel.state.value.units)
        assertEquals(draft, viewModel.state.value.routineDraft)
        assertEquals("IMPERIAL", viewModel.state.value.routineDraft!!.units)

        viewModel.saveRoutine { }
        advanceUntilIdle()
        assertEquals(d("45.359"), repository.routineWrites.single().second.entries.single().sets.first().weightKg)
        assertNull(viewModel.state.value.routineDraft)
    }

    @Test fun `failed routine save retains draft and the last confirmed reusable routine`() = runTest(dispatcher) {
        load()
        viewModel.beginRoutine("routine-a")
        viewModel.setRoutineName("저장 전 수정")
        val before = viewModel.state.value.routines
        val draft = viewModel.state.value.routineDraft
        repository.nextRoutineFailure = failure()
        viewModel.saveRoutine { fail("Failed save must not navigate") }
        advanceUntilIdle()
        assertEquals(before, viewModel.state.value.routines)
        assertEquals(draft, viewModel.state.value.routineDraft)
        assertNotNull(viewModel.state.value.error)
    }

    @Test fun `missing exercise rejects entire routine draft rather than dropping a planned entry`() = runTest(dispatcher) {
        repository.routines = listOf(routine().copy(entries = routine().entries + RoutineEntry("missing-entry", "missing-exercise", listOf(PlannedSet("missing-set")))))
        load()
        viewModel.beginRoutine("routine-a")
        assertNull(viewModel.state.value.routineDraft)
        assertNotNull(viewModel.state.value.error)
        assertTrue(repository.routineWrites.isEmpty())
    }

    @Test fun `failed set write keeps confirmed performance and does not start rest`() = runTest(dispatcher) {
        val original = session()
        repository.sessions[today] = original
        load()
        repository.nextSaveFailure = failure()
        viewModel.recordSet("entry-a", "actual-1", "80", "8", "", "DONE", "METRIC") { fail("Failed set must not advance") }
        advanceUntilIdle()
        assertEquals(original, viewModel.state.value.session)
        assertNull(viewModel.state.value.restDeadlineElapsedMs)
        assertEquals(d("80.000"), repository.sessionWrites.single().second.entries.single().sets.first().weightKg)
        assertFalse(viewModel.state.value.busy)
    }

    @Test fun `partial substitution preserves completed performance and links fresh pending replacement to baseline`() = runTest(dispatcher) {
        val completed = ActualSet("actual-1", "plan-1", "DONE", d("80"), 8)
        val original = session(sets = listOf(completed, ActualSet("actual-2", "plan-2")))
        repository.sessions[today] = original
        load()
        viewModel.replaceEntry("entry-a", exerciseB.id, "기구 사용 중") { }
        advanceUntilIdle()

        val write = repository.sessionWrites.single().second
        assertEquals(2, write.entries.size)
        val retained = write.entries[0]
        val replacement = write.entries[1]
        assertEquals("entry-a", retained.id)
        assertEquals(completed, retained.sets[0])
        assertEquals("SKIPPED", retained.sets[1].status)
        assertNull(retained.sets[1].weightKg)
        assertNotEquals("entry-a", replacement.id)
        assertEquals(exerciseB.id, replacement.exerciseId)
        assertEquals("planned-entry", replacement.plannedEntryId)
        assertEquals("plan-2", replacement.sets.single().planSetId)
        assertEquals("PENDING", replacement.sets.single().status)
        assertNull(replacement.sets.single().weightKg)
        assertNull(replacement.sets.single().reps)
        assertEquals("기구 사용 중", replacement.replacementReason)
        assertEquals(original.planned, viewModel.state.value.session!!.planned)
    }

    @Test fun `replacement before any completed set keeps entry identity and never copies planned weights`() = runTest(dispatcher) {
        repository.sessions[today] = session()
        load()
        viewModel.replaceEntry("entry-a", exerciseB.id, "다른 기구 선택") { }
        advanceUntilIdle()
        val changed = repository.sessionWrites.single().second.entries.single()
        assertEquals("entry-a", changed.id)
        assertEquals(listOf("plan-1", "plan-2"), changed.sets.map { it.planSetId })
        assertTrue(changed.sets.all { it.status == "PENDING" && it.weightKg == null && it.reps == null })
        assertEquals(d("100"), viewModel.state.value.session!!.planned!!.entries.single().sets.first().weightKg)
    }

    @Test fun `finishing session leaves pending and skipped sets distinct from done`() = runTest(dispatcher) {
        val sets = listOf(ActualSet("actual-1", "plan-1", "DONE", d("80"), 8), ActualSet("actual-2", "plan-2"), ActualSet("actual-3", status = "SKIPPED"))
        repository.sessions[today] = session(sets = sets)
        load()
        viewModel.finishSession { }
        advanceUntilIdle()
        val sent = repository.sessionWrites.single().second
        assertEquals("COMPLETED", sent.status)
        assertEquals(listOf("DONE", "PENDING", "SKIPPED"), sent.entries.single().sets.map { it.status })
        assertEquals("COMPLETED", viewModel.state.value.session!!.status)
        assertEquals(1, viewModel.state.value.session!!.entries.single().sets.count { it.status == "DONE" })
        assertNull(viewModel.state.value.restDeadlineElapsedMs)
    }

    @Test fun `finishing with a note sends one atomic write without completing pending sets`() = runTest(dispatcher) {
        repository.sessions[today] = session()
        load()
        var navigated = false
        viewModel.finishSession("  마무리 메모  ") { navigated = true }
        advanceUntilIdle()
        val sent = repository.sessionWrites.single().second
        assertEquals("COMPLETED", sent.status)
        assertEquals("마무리 메모", sent.note)
        assertTrue(sent.entries.single().sets.all { it.status == "PENDING" })
        assertEquals("마무리 메모", viewModel.state.value.session!!.note)
        assertTrue(navigated)
    }

    @Test fun `rest deadline uses injected monotonic clock and extension after elapsed time`() = runTest(dispatcher) {
        repository.sessions[today] = session()
        load()
        viewModel.recordSet("entry-a", "actual-1", "100", "8", "", "DONE", "IMPERIAL") { }
        advanceUntilIdle()
        assertEquals(d("45.359"), repository.sessionWrites.single().second.entries.single().sets.first().weightKg)
        assertEquals(100_000L, viewModel.state.value.restDeadlineElapsedMs)
        elapsed = 200_000L
        viewModel.extendRest(30)
        assertEquals(230_000L, viewModel.state.value.restDeadlineElapsedMs)
        viewModel.finishRest()
        assertNull(viewModel.state.value.restDeadlineElapsedMs)
        assertEquals(1, repository.sessionWrites.size)
    }

    @Test fun `editing a completed session does not restart a rest timer`() = runTest(dispatcher) {
        repository.sessions[today] = session(status = "COMPLETED")
        load()
        viewModel.recordSet("entry-a", "actual-1", "80", "8", "", "DONE", "METRIC") { }
        advanceUntilIdle()
        assertEquals("COMPLETED", viewModel.state.value.session!!.status)
        assertNull(viewModel.state.value.restDeadlineElapsedMs)
    }

    @Test fun `future day accepts a plan view but cannot start an actual session`() = runTest(dispatcher) {
        load()
        viewModel.loadDate(LocalDate.now(ZoneOffset.UTC).plusDays(1).toString())
        advanceUntilIdle()
        assertTrue(viewModel.state.value.loaded)
        viewModel.startSession { fail("Future workout must not start") }
        advanceUntilIdle()
        assertTrue(repository.starts.isEmpty())
        assertNull(viewModel.state.value.session)
        assertNotNull(viewModel.state.value.error)
    }

    @Test fun `ambiguous failed session start retries stable client id and never fabricates actual values`() = runTest(dispatcher) {
        load()
        repository.nextStartFailure = failure()
        viewModel.startSession { fail("Failed start must not navigate") }
        advanceUntilIdle()
        assertNull(viewModel.state.value.session)
        viewModel.startSession { }
        advanceUntilIdle()
        assertEquals(2, repository.starts.size)
        assertEquals(repository.starts[0].first, repository.starts[1].first)
        assertEquals(today, repository.starts[0].second.date)
        assertTrue(viewModel.state.value.session!!.entries.single().sets.all { it.status == "PENDING" && it.weightKg == null && it.reps == null })
        viewModel.startSession { }
        advanceUntilIdle()
        assertEquals(2, repository.starts.size)
    }

    @Test fun `changing date hides prior session history and rest but keeps unsent routine`() = runTest(dispatcher) {
        repository.sessions[today] = session()
        load()
        viewModel.beginRoutine("routine-a")
        viewModel.setRoutineNote("남겨둘 초안")
        val draft = viewModel.state.value.routineDraft
        val pending = CompletableDeferred<List<WorkoutDayDto>>()
        repository.nextDaysGate = pending
        viewModel.loadDate("2000-01-01")
        assertNull(viewModel.state.value.session)
        assertTrue(viewModel.state.value.days.isEmpty())
        assertTrue(viewModel.state.value.history.isEmpty())
        assertNull(viewModel.state.value.restDeadlineElapsedMs)
        assertEquals(draft, viewModel.state.value.routineDraft)
        runCurrent()
        pending.completeExceptionally(failure())
        advanceUntilIdle()
        assertNull(viewModel.state.value.session)
        assertEquals(draft, viewModel.state.value.routineDraft)
    }

    @Test fun `set drafts survive screen and set changes without turning planned values into performance`() = runTest(dispatcher) {
        val original = session(sets = listOf(ActualSet("actual-1", "plan-1", "DONE", d("80"), 8), ActualSet("actual-2", "plan-2")))
        repository.sessions[today] = original
        load()
        viewModel.prepareSetDraft("entry-a", "actual-1")
        assertEquals("80", setDraft("actual-1").weight)
        assertEquals("8", setDraft("actual-1").reps)
        viewModel.updateSetDraft("entry-a", "actual-1", "85", "9", "")
        val changed = setDraft("actual-1")
        viewModel.prepareSetDraft("entry-a", "actual-2")
        assertEquals("100", setDraft("actual-2").weight)
        assertEquals("10", setDraft("actual-2").reps)
        viewModel.prepareSetDraft("entry-a", "actual-1")
        assertEquals(changed, setDraft("actual-1"))
        assertEquals(original, viewModel.state.value.session)
        assertTrue(repository.sessionWrites.isEmpty())
    }

    @Test fun `set draft keeps captured pounds and input across both note saves and unit preference change`() = runTest(dispatcher) {
        repository.sessions[today] = session()
        load("IMPERIAL")
        viewModel.prepareSetDraft("entry-a", "actual-1")
        viewModel.updateSetDraft("entry-a", "actual-1", "100", "8", "")
        val draft = setDraft("actual-1")
        viewModel.bind("account-a", "UTC", "METRIC")
        viewModel.saveEntryNote("entry-a", "자리 높이 메모") { }
        advanceUntilIdle()
        viewModel.saveSessionNote("오늘 운동 메모") { }
        advanceUntilIdle()
        viewModel.prepareSetDraft("entry-a", "actual-1")
        assertEquals(2L, viewModel.state.value.session!!.version)
        assertEquals(draft, setDraft("actual-1"))
        // A changed preference must never reinterpret the already-entered pounds as kg.
        viewModel.recordSet("entry-a", "actual-1", draft.weight, draft.reps, draft.seconds, "DONE", "METRIC") { }
        advanceUntilIdle()
        assertEquals(d("45.359"), repository.sessionWrites.last().second.entries.single().sets.first().weightKg)
        assertFalse(workoutSetDraftKey("session-a", "entry-a", "actual-1") in viewModel.state.value.setDrafts)
    }

    @Test fun `failed set save preserves all drafts and successful retry clears only saved set`() = runTest(dispatcher) {
        repository.sessions[today] = session()
        load()
        viewModel.updateSetDraft("entry-a", "actual-1", "80", "8", "")
        viewModel.updateSetDraft("entry-a", "actual-2", "75", "10", "")
        val drafts = viewModel.state.value.setDrafts
        repository.nextSaveFailure = failure()
        viewModel.recordSet("entry-a", "actual-1", "80", "8", "", "DONE", "METRIC") { fail("Failed write must keep editor open") }
        advanceUntilIdle()
        assertEquals(drafts, viewModel.state.value.setDrafts)
        assertEquals("PENDING", viewModel.state.value.session!!.entries.single().sets.first().status)
        viewModel.recordSet("entry-a", "actual-1", "80", "8", "", "DONE", "METRIC") { }
        advanceUntilIdle()
        assertEquals(mapOf(workoutSetDraftKey("session-a", "entry-a", "actual-2") to drafts.getValue(workoutSetDraftKey("session-a", "entry-a", "actual-2"))), viewModel.state.value.setDrafts)
    }

    @Test fun `server set change blocks stale draft until user explicitly keeps entered values`() = runTest(dispatcher) {
        repository.sessions[today] = session()
        load()
        viewModel.updateSetDraft("entry-a", "actual-1", "80", "8", "")
        val draft = setDraft("actual-1")
        val serverSet = ActualSet("actual-1", "plan-1", "DONE", d("90"), 6)
        repository.sessions[today] = session(sets = listOf(serverSet, ActualSet("actual-2", "plan-2")), version = 1)
        viewModel.refresh()
        advanceUntilIdle()
        viewModel.prepareSetDraft("entry-a", "actual-1")
        viewModel.recordSet("entry-a", "actual-1", "80", "8", "", "DONE", "METRIC") { fail("Stale draft must require a choice") }
        advanceUntilIdle()
        assertTrue(repository.sessionWrites.isEmpty())
        assertEquals(draft, setDraft("actual-1"))
        assertNotNull(viewModel.state.value.error)
        viewModel.resolveSetDraft("entry-a", "actual-1", useSaved = false)
        assertEquals(draft.copy(source = serverSet), setDraft("actual-1"))
        assertNull(viewModel.state.value.error)
        viewModel.recordSet("entry-a", "actual-1", "80", "8", "", "DONE", "METRIC") { }
        advanceUntilIdle()
        assertEquals(1L, repository.sessionWrites.single().second.version)
        assertEquals(d("80.000"), repository.sessionWrites.single().second.entries.single().sets.first().weightKg)
    }

    @Test fun `accepting server set discards only chosen draft input and uses current display unit`() = runTest(dispatcher) {
        repository.sessions[today] = session()
        load("IMPERIAL")
        viewModel.updateSetDraft("entry-a", "actual-1", "100", "8", "")
        viewModel.updateSetDraft("entry-a", "actual-2", "110", "10", "")
        val otherDraft = setDraft("actual-2")
        val saved = ActualSet("actual-1", "plan-1", "DONE", d("90"), 6)
        repository.sessions[today] = session(sets = listOf(saved, ActualSet("actual-2", "plan-2")), version = 1)
        viewModel.bind("account-a", "UTC", "METRIC")
        viewModel.refresh()
        advanceUntilIdle()
        viewModel.resolveSetDraft("entry-a", "actual-1", useSaved = true)
        assertEquals(WorkoutSetDraft("90", "6", "", "METRIC", saved), setDraft("actual-1"))
        assertEquals(otherDraft, setDraft("actual-2"))
        assertEquals("IMPERIAL", setDraft("actual-2").units)
        assertTrue(repository.sessionWrites.isEmpty())
    }

    @Test fun `date changes preserve drafts and deleting session clears only its drafts`() = runTest(dispatcher) {
        val yesterday = LocalDate.parse(today).minusDays(1).toString()
        repository.sessions[today] = session()
        repository.sessions[yesterday] = session().copy(id = "session-b", date = yesterday)
        load()
        viewModel.updateSetDraft("entry-a", "actual-1", "80", "8", "")
        val todayDraft = setDraft("actual-1")
        viewModel.loadDate(yesterday)
        advanceUntilIdle()
        viewModel.updateSetDraft("entry-a", "actual-1", "70", "7", "")
        assertEquals(2, viewModel.state.value.setDrafts.size)
        viewModel.deleteSession { }
        advanceUntilIdle()
        assertNull(viewModel.state.value.session)
        assertEquals(mapOf(workoutSetDraftKey("session-a", "entry-a", "actual-1") to todayDraft), viewModel.state.value.setDrafts)
        viewModel.loadDate(today)
        advanceUntilIdle()
        viewModel.prepareSetDraft("entry-a", "actual-1")
        assertEquals(todayDraft, setDraft("actual-1"))
        viewModel.bind(null)
        assertTrue(viewModel.state.value.setDrafts.isEmpty())
    }

    @Test fun `replaced exercise or changed load convention never seeds old planned weights`() = runTest(dispatcher) {
        val original = session()
        repository.sessions[today] = original.copy(entries = original.entries.map { it.copy(exercise = exerciseB.snapshot()) })
        load()
        viewModel.prepareSetDraft("entry-a", "actual-1")
        assertEquals("", setDraft("actual-1").weight)
        assertEquals("", setDraft("actual-1").reps)
        repository.sessions[today] = original.copy(entries = original.entries.map { it.copy(exercise = it.exercise.copy(loadConvention = "PER_HAND")) })
        viewModel.refresh()
        advanceUntilIdle()
        viewModel.resolveSetDraft("entry-a", "actual-1", useSaved = true)
        assertEquals("", setDraft("actual-1").weight)
        assertEquals("", setDraft("actual-1").reps)
        assertTrue(repository.sessionWrites.isEmpty())
    }

    private fun setDraft(setId: String) = viewModel.state.value.setDrafts.getValue(workoutSetDraftKey("session-a", "entry-a", setId))

    private fun TestScope.load(units: String = "METRIC") {
        viewModel.bind("account-a", "UTC", units)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.loaded)
    }
    private fun d(value: String) = BigDecimal(value)
    private fun failure() = AccountException(AccountErrorKind.NETWORK, "서버 응답을 확인하지 못했어요.")
    private val exerciseA = ExerciseDto("exercise-a", "사용자 운동 A", "머신", "사용자 부위 A", "WEIGHT_REPS", "MACHINE", 0)
    private val exerciseB = ExerciseDto("exercise-b", "사용자 운동 B", "바벨", "사용자 부위 B", "WEIGHT_REPS", "TOTAL", 0)
    private fun ExerciseDto.snapshot() = ExerciseSnapshot(id, name, equipment, target, recordType, loadConvention)
    private fun routine() = RoutineDto("routine-a", "내 루틴", listOf(RoutineEntry("planned-entry", exerciseA.id,
        listOf(PlannedSet("plan-1", d("100"), 10), PlannedSet("plan-2", d("100"), 10)))), version = 0)
    private fun baseline() = PlannedWorkout("routine-a", "기록 당시 루틴", 0,
        listOf(PlannedExercise("planned-entry", exerciseA.snapshot(), routine().entries.single().sets, 90)))
    private fun session(sets: List<ActualSet> = listOf(ActualSet("actual-1", "plan-1"), ActualSet("actual-2", "plan-2")),
        status: String = "IN_PROGRESS", version: Long = 0) = WorkoutSessionDto("session-a", today, baseline(),
        listOf(WorkoutEntryDto("entry-a", "planned-entry", exerciseA.snapshot(), sets, 90)), status,
        version = version, startedAt = "2026-10-01T01:00:00Z", finishedAt = if (status == "COMPLETED") "2026-10-01T02:00:00Z" else null)

    /** Explicit test-only responses. Production authorization and validation are tested separately. */
    private val catalogItem=CatalogExercise("basic","기본 덤벨 프레스","덤벨","가슴","가슴","WEIGHT_REPS","PER_HAND",listOf("DB press"),"한 손 중량")
    private inner class FakeWorkouts : WorkoutDataSource, ExerciseCatalogDataSource {
        var catalogFailure:AccountException?=null
        var catalogGate:CompletableDeferred<ExerciseCatalogDto>?=null
        var importGate:CompletableDeferred<ExerciseDto>?=null
        var imported=exerciseB.copy(id="catalog-copy",loadConvention="PER_HAND")
        val catalogOwners=mutableListOf<String>()
        override suspend fun exerciseCatalog(owner:String):ExerciseCatalogDto {
            catalogOwners+=owner;catalogFailure?.let { throw it }
            catalogGate?.let { return withContext(NonCancellable){it.await()} }
            return ExerciseCatalogDto("test","test source","https://example.com",listOf(catalogItem))
        }
        override suspend fun importCatalogExercise(owner:String,key:String):ExerciseDto {
            catalogFailure?.let { throw it }
            importGate?.let { return withContext(NonCancellable){it.await()} }
            return imported
        }
        var exercises = listOf(exerciseA, exerciseB)
        var routines = listOf(routine())
        val sessions = mutableMapOf<String, WorkoutSessionDto>()
        var nextDaysGate: CompletableDeferred<List<WorkoutDayDto>>? = null
        var nextSaveGate: CompletableDeferred<WorkoutSessionDto>? = null
        var ignoreSaveCancellation = false
        var nextSaveFailure: AccountException? = null
        var nextStartFailure: AccountException? = null
        var nextRoutineFailure: AccountException? = null
        val routineWrites = mutableListOf<Pair<String, RoutineWrite>>()
        val sessionWrites = mutableListOf<Pair<String, WorkoutSessionWrite>>()
        val starts = mutableListOf<Pair<String, WorkoutStartWrite>>()

        override suspend fun listExercises() = exercises
        override suspend fun listRoutines() = routines
        override suspend fun getWorkoutPlan() = WorkoutPlanDto((1..7).map { WorkoutPlanSlot(it, "routine-a") }, 0, today)
        override suspend fun getWorkoutDays(from: String, to: String): List<WorkoutDayDto> {
            nextDaysGate?.let { nextDaysGate = null; return it.await() }
            return generateSequence(LocalDate.parse(from)) { it.plusDays(1) }.takeWhile { it <= LocalDate.parse(to) }
                .map { WorkoutDayDto(it.toString(), baseline(), session = sessions[it.toString()]) }.toList()
        }
        override suspend fun saveRoutine(id: String, routine: RoutineWrite): RoutineDto {
            routineWrites += id to routine
            nextRoutineFailure?.let { nextRoutineFailure = null; throw it }
            return RoutineDto(id, routine.name, routine.entries, routine.note, (routine.version ?: -1) + 1)
        }
        override suspend fun startWorkoutSession(id: String, start: WorkoutStartWrite): WorkoutSessionDto {
            starts += id to start
            nextStartFailure?.let { nextStartFailure = null; throw it }
            return session().copy(id = id, date = start.date).also { sessions[it.date] = it }
        }
        override suspend fun saveWorkoutSession(id: String, session: WorkoutSessionWrite): WorkoutSessionDto {
            sessionWrites += id to session
            nextSaveFailure?.let { nextSaveFailure = null; throw it }
            nextSaveGate?.let {
                nextSaveGate = null
                return if (ignoreSaveCancellation) withContext(NonCancellable) { it.await() } else it.await()
            }
            val original = sessions.values.first { it.id == id }
            val entries = session.entries.map { write -> WorkoutEntryDto(write.id, write.plannedEntryId,
                exercises.first { it.id == write.exerciseId }.snapshot(), write.sets, write.restSeconds, write.note, write.replacementReason) }
            return original.copy(entries = entries, status = session.status, note = session.note, version = session.version + 1,
                finishedAt = if (session.status == "COMPLETED") "2026-10-01T02:00:00Z" else null).also { sessions[it.date] = it }
        }
        override suspend fun getWorkoutHistory(exerciseId: String, before: String) = emptyList<WorkoutHistoryItem>()
        override suspend fun saveExercise(id: String, exercise: ExerciseWrite) = error("Unused operation")
        override suspend fun deleteExercise(id: String, version: Long) = error("Unused operation")
        override suspend fun deleteRoutine(id: String, version: Long) = error("Unused operation")
        override suspend fun saveWorkoutPlan(plan: WorkoutPlanWrite) = error("Unused operation")
        override suspend fun saveWorkoutOverride(date: String, override: WorkoutOverrideWrite) = error("Unused operation")
        override suspend fun deleteWorkoutOverride(date: String, version: Long) = error("Unused operation")
        override suspend fun deleteWorkoutSession(id: String, version: Long) {
            val saved = sessions.values.first { it.id == id }
            check(saved.version == version)
            sessions.remove(saved.date)
        }
    }
}
