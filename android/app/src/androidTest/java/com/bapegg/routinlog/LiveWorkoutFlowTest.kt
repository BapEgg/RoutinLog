package com.bapegg.routinlog

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.bapegg.routinlog.data.*
import com.bapegg.routinlog.ui.PreviewSession
import com.bapegg.routinlog.ui.WorkoutViewModel
import com.bapegg.routinlog.ui.screens.LiveWorkoutScreens
import com.bapegg.routinlog.ui.theme.RoutineLogTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** Real Compose forms and ViewModel, with isolated test-only data. This does not contact a live server. */
class LiveWorkoutFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var model: WorkoutViewModel
    private lateinit var ui: PreviewSession
    private lateinit var source: FakeWorkouts

    private fun start(fake: FakeWorkouts = FakeWorkouts()) {
        source = fake
        compose.runOnUiThread {
            val factory = viewModelFactory { initializer { WorkoutViewModel(source) } }
            model = ViewModelProvider(compose.activity, factory)[WorkoutViewModel::class.java]
            ui = ViewModelProvider(compose.activity)[PreviewSession::class.java]
            ui.accountMode = true
            ui.go("W01")
            model.bind("2f91e843-75b3-4d51-9489-8cd6cde77c96")
        }
        compose.setContent {
            RoutineLogTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        LiveWorkoutScreens(ui.route, ui, model)
                    }
                }
            }
        }
        compose.waitUntil(5_000) { model.state.value.loaded && !model.state.value.loading }
        compose.waitForIdle()
    }

    private fun click(text: String) = compose.onNodeWithText(text).performScrollTo().performClick()
    private fun enter(label: String, text: String) = compose.onNodeWithContentDescription(label).performScrollTo().performTextReplacement(text)
    private fun choose(label: String) = compose.onNode(hasText(label) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)).performScrollTo().performClick()
    private fun field(label: String) = compose.onNodeWithContentDescription(label).fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    @Test fun catalogSearchImportAndAddToRoutineWithoutSuggestedWeights() {
        start()
        click("기본 운동에서 고르기")
        compose.waitUntil(5000){model.state.value.catalog!=null}
        enter("내 운동 검색","덤벨프레스")
        compose.onNodeWithText("기본 덤벨 프레스").performScrollTo().assertIsDisplayed()
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap->
            File(compose.activity.cacheDir,"qa-exercise-catalog.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        }
        click("내 운동으로 가져오기")
        compose.runOnIdle {
            assertEquals(1,source.exercises.size)
            assertEquals("PER_HAND",source.exercises.values.single().loadConvention)
            assertEquals("내 운동",ui.get("liveWorkout.libraryTab"))
            ui.go("W01")
        }
        click("새 루틴 만들기");enter("루틴 이름","새 상체 루틴");click("루틴에 운동 추가");click("루틴에 추가")
        compose.runOnIdle {
            val set=model.state.value.routineDraft!!.entries.single().sets.single()
            assertEquals("",set.weight);assertEquals("",set.reps)
        }
        click("기본 루틴 저장")
        compose.runOnIdle { assertEquals(1,source.routines.size);assertNull(source.routines.values.single().entries.single().sets.single().weightKg) }
    }

    @Test fun firstExerciseToRoutineToWeekdayToActualSetAndRest() {
        start()
        compose.onNodeWithText("내가 하는 운동부터 등록해요").assertExists()
        click("첫 운동 등록")
        enter("운동 이름", "테스트 레그프레스")
        enter("사용하는 기구", "테스트 머신")
        enter("주로 운동하는 부위 · 선택", "대퇴사두")
        click("기구 표시 중량")
        click("내 운동 저장")
        compose.runOnIdle {
            assertEquals("W01", ui.route)
            val exercise = source.exercises.values.single()
            assertEquals("WEIGHT_REPS", exercise.recordType)
            assertEquals("MACHINE", exercise.loadConvention)
        }
        click("새 루틴 만들기")
        enter("루틴 이름", "내 테스트 하체 루틴")
        click("루틴에 운동 추가")
        click("루틴에 추가")
        enter("1세트 계획 중량", "92.5")
        enter("1세트 계획 횟수", "10")
        click("계획 세트 추가")
        enter("세트 사이 휴식", "90")
        click("기본 루틴 저장")
        click("요일별 루틴 설정")
        click("${weekday(LocalDate.parse(source.today).dayOfWeek.value)}요일")
        choose("내 테스트 하체 루틴")
        click("요일별 루틴 저장")
        compose.runOnIdle {
            val routine = source.routines.values.single()
            assertEquals(2, routine.entries.single().sets.size)
            assertDecimal("92.5", routine.entries.single().sets.first().weightKg)
            assertEquals(routine.id, source.plan.slots.single { it.dayOfWeek == LocalDate.parse(source.today).dayOfWeek.value }.routineId)
            assertEquals(routine.id, model.state.value.day?.planned?.routineId)
        }
        click("이날 운동 시작")
        click("이 운동 기록")
        assertEquals("92.5", field("실제 중량"))
        enter("실제 중량", "80")
        enter("실제 반복수", "8")
        click("이 세트 완료")
        compose.runOnIdle {
            assertEquals("W10", ui.route)
            val session = requireNotNull(model.state.value.session)
            assertDecimal("92.5", session.planned?.entries?.single()?.sets?.first()?.weightKg)
            val actual = session.entries.single().sets
            assertEquals("DONE", actual[0].status)
            assertDecimal("80", actual[0].weightKg)
            assertEquals(8, actual[0].reps)
            assertEquals("PENDING", actual[1].status)
            assertNull(actual[1].weightKg)
            assertTrue(requireNotNull(model.state.value.restDeadlineElapsedMs) > SystemClock.elapsedRealtime())
        }
        capture("qa-live-workout-rest.png")
        click("휴식 끝내고 다음 세트")
        compose.runOnIdle {
            assertEquals("W09", ui.route)
            assertNull(model.state.value.restDeadlineElapsedMs)
            assertEquals(model.state.value.session!!.entries.single().sets[1].id, ui.get("liveWorkout.setId"))
        }
        compose.onNodeWithContentDescription("실제 중량").performScrollTo()
        capture("qa-live-workout-set.png")
    }

    @Test fun failedActualSaveKeepsDraftAndConfirmedMetricsUntilRetrySucceeds() {
        start(FakeWorkouts.withPlan().apply { seedSession() })
        click("운동 이어서 기록")
        click("이 운동 기록")
        enter("실제 중량", "75.25")
        enter("실제 반복수", "9")
        choose("2세트")
        enter("실제 중량", "60")
        choose("1세트")
        assertEquals("75.25", field("실제 중량"))
        click("불편함·수행 메모")
        enter("이 운동의 수행 메모", "메모를 열어도 세트 입력은 남음")
        click("수행 메모 저장")
        assertEquals("75.25", field("실제 중량"))
        assertEquals("9", field("실제 반복수"))
        val writesBeforeSet = source.sessionWrites.size
        val gate = CompletableDeferred<Unit>()
        compose.runOnIdle {
            source.saveGate = gate
            source.saveFailure = AccountException(AccountErrorKind.NETWORK, SAVE_ERROR)
        }
        click("이 세트 완료")
        compose.runOnIdle {
            assertTrue(model.state.value.busy)
            assertEquals(writesBeforeSet + 1, source.sessionWrites.size)
            assertEquals("PENDING", model.state.value.session!!.entries.single().sets.first().status)
            assertNull(model.state.value.session!!.entries.single().sets.first().weightKg)
        }
        gate.complete(Unit)
        compose.waitUntil(5_000) { !model.state.value.busy && model.state.value.error != null }
        compose.onNodeWithText(SAVE_ERROR).assertExists()
        assertEquals("75.25", field("실제 중량"))
        assertEquals("9", field("실제 반복수"))
        compose.runOnIdle {
            assertEquals("W09", ui.route)
            assertNull(model.state.value.restDeadlineElapsedMs)
            source.saveGate = null
            source.saveFailure = null
        }
        click("이 세트 완료")
        compose.runOnIdle {
            assertEquals("W10", ui.route)
            val saved = model.state.value.session!!.entries.single().sets.first()
            assertDecimal("75.25", saved.weightKg)
            assertEquals(9, saved.reps)
            assertEquals(writesBeforeSet + 2, source.sessionWrites.size)
            val attempts = source.sessionWrites.takeLast(2)
            assertEquals(attempts[0].second.version, attempts[1].second.version)
        }
    }

    @Test fun replacingAfterOneDoneSetPreservesOriginalAndRequiresNewActualValues() {
        val fake = FakeWorkouts.withPlan().apply {
            val seeded = seedSession()
            val entry = seeded.entries.single()
            sessions[seeded.id] = seeded.copy(entries = listOf(entry.copy(sets = entry.sets.mapIndexed { index, set ->
                if (index == 0) set.copy(status = "DONE", weightKg = BigDecimal("80"), reps = 8) else set
            })))
            val replacement = ExerciseDto(newId(), "테스트 런지", "맨몸", "둔근", "REPS", "BODYWEIGHT", 0)
            exercises[replacement.id] = replacement
        }
        start(fake)
        val originalPlan = model.state.value.session!!.planned
        click("운동 이어서 기록")
        click("운동 교체")
        enter("내 운동 검색", "런지")
        click("이 운동과 비교")
        click("기구 대기")
        click("이 운동으로 교체")
        compose.onNodeWithText("교체").performClick()
        compose.runOnIdle {
            assertEquals("W08", ui.route)
            val session = model.state.value.session!!
            assertEquals(originalPlan, session.planned)
            assertEquals(2, session.entries.size)
            val old = session.entries[0]
            assertEquals("DONE", old.sets[0].status)
            assertDecimal("80", old.sets[0].weightKg)
            assertEquals("SKIPPED", old.sets[1].status)
            assertNull(old.sets[1].weightKg)
            val replacement = session.entries[1]
            assertEquals("테스트 런지", replacement.exercise.name)
            assertEquals(old.plannedEntryId, replacement.plannedEntryId)
            assertEquals("기구 대기", replacement.replacementReason)
            assertEquals("PENDING", replacement.sets.single().status)
            assertNull(replacement.sets.single().weightKg)
            assertNull(replacement.sets.single().reps)
        }
        compose.onAllNodesWithText("이 운동 기록")[1].performScrollTo().performClick()
        compose.onNodeWithContentDescription("실제 중량").assertDoesNotExist()
        assertEquals("", field("실제 반복수"))
        enter("실제 반복수", "12")
        click("이 세트 완료")
        compose.runOnIdle {
            val session = model.state.value.session!!
            assertDecimal("80", session.entries[0].sets.first().weightKg)
            assertEquals(12, session.entries[1].sets.single().reps)
            assertNull(session.entries[1].sets.single().weightKg)
            assertEquals(originalPlan, session.planned)
        }
    }

    @Test fun timedWorkoutMemoAndFinishLeaveUnperformedSetsPending() {
        start(FakeWorkouts.withPlan(type = "DURATION", sets = 3, rest = 0).apply { seedSession() })
        click("운동 이어서 기록")
        click("이 운동 기록")
        compose.onNodeWithContentDescription("실제 중량").assertDoesNotExist()
        compose.onNodeWithContentDescription("실제 반복수").assertDoesNotExist()
        click("불편함·수행 메모")
        enter("이 운동의 수행 메모", "테스트용 속도와 경사를 확인함")
        click("수행 메모 저장")
        enter("실제 수행 시간", "0")
        click("이 세트 완료")
        compose.runOnIdle {
            assertEquals("W09", ui.route)
            assertNotNull(model.state.value.error)
            assertEquals(1, source.sessionWrites.size) // Only the explicit memo was saved.
        }
        enter("실제 수행 시간", "90")
        click("이 세트 완료")
        compose.runOnIdle {
            assertEquals("W08", ui.route)
            assertNull(model.state.value.restDeadlineElapsedMs)
            val entry = model.state.value.session!!.entries.single()
            assertEquals(90, entry.sets.first().durationSeconds)
            assertNull(entry.sets.first().weightKg)
            assertNull(entry.sets.first().reps)
            assertEquals("테스트용 속도와 경사를 확인함", entry.note)
        }
        click("오늘 운동 마무리")
        enter("오늘 운동 메모 · 선택", "일정 때문에 일부만 수행")
        val writesBeforeFinish = source.sessionWrites.size
        click("이날 운동 마무리")
        compose.runOnIdle {
            assertEquals("W01", ui.route)
            assertEquals(writesBeforeFinish + 1, source.sessionWrites.size)
            val saved = model.state.value.session!!
            assertEquals("COMPLETED", saved.status)
            assertEquals("일정 때문에 일부만 수행", saved.note)
            assertEquals(listOf("DONE", "PENDING", "PENDING"), saved.entries.single().sets.map { it.status })
            assertNotNull(saved.finishedAt)
        }
        click("운동 기록 보기")
        compose.onNodeWithText("마무리한 운동").assertExists()
        click("다시 이어서 기록")
        compose.runOnIdle {
            assertEquals("IN_PROGRESS", model.state.value.session?.status)
            assertNull(model.state.value.session?.finishedAt)
            assertEquals(2, model.state.value.session!!.entries.single().sets.count { it.status == "PENDING" })
        }
    }

    @Test fun changingOneFutureDateToRestDoesNotMoveAnotherDayOrStartedSession() {
        val fake = FakeWorkouts.withPlan().apply { seedSession() }
        start(fake)
        val todaySession = model.state.value.session
        val tomorrow = LocalDate.parse(fake.today).plusDays(1).toString()
        click("이 날짜의 루틴 변경")
        enter("변경할 날짜", tomorrow)
        compose.onNodeWithText("이 날짜에 적용").assertIsNotEnabled()
        click("이 날짜 확인")
        click("이날의 루틴")
        choose("휴식")
        enter("일정 변경 메모 · 선택", "테스트 일정 변경")
        click("이 날짜에 적용")
        compose.runOnIdle {
            assertEquals(tomorrow, model.state.value.date)
            assertNull(model.state.value.day?.planned)
            assertEquals("테스트 일정 변경", model.state.value.day?.`override`?.note)
            assertEquals(listOf(tomorrow), source.overrides.keys.toList())
            assertEquals(todaySession, source.sessions.values.single())
            assertTrue(source.plan.slots.all { it.routineId != null })
        }
        compose.onNodeWithText("자유 운동 기록 시작").assertIsNotEnabled()
        click("이 날짜의 루틴 변경")
        click("날짜 변경 취소하고 기본 일정으로")
        compose.onNodeWithText("되돌리기").performClick()
        compose.runOnIdle {
            assertTrue(source.overrides.isEmpty())
            assertNotNull(model.state.value.day?.planned)
            assertEquals(todaySession, source.sessions.values.single())
            assertEquals(tomorrow, model.state.value.date)
        }
    }

    @Test fun historyWithSameExerciseIdButDifferentEquipmentOrLoadBasisCannotFillCurrentSet() {
        val fake = FakeWorkouts.withPlan().apply {
            val current = seedSession()
            val entry = current.entries.single()
            val older = current.copy(id = newId(), date = LocalDate.parse(today).minusDays(1).toString(), planned = null,
                entries = listOf(entry.copy(id = newId(), plannedEntryId = null,
                    exercise = entry.exercise.copy(equipment = "이전 덤벨", loadConvention = "PER_HAND"),
                    sets = listOf(ActualSet(newId(), status = "DONE", weightKg = BigDecimal("35"), reps = 12)))),
                status = "COMPLETED", finishedAt = Instant.now().toString())
            sessions[older.id] = older
        }
        start(fake)
        click("운동 이어서 기록")
        click("이 운동 기록")
        compose.waitUntil(5_000) { !model.state.value.historyLoading && model.state.value.history.isNotEmpty() }
        compose.onNodeWithText("이전 덤벨 · 하체 · 중량 × 반복").assertExists()
        compose.onNodeWithText("현재 운동과 기구 또는 기록 기준이 달라 값을 바로 불러올 수 없어요.").assertExists()
        compose.onNodeWithText("이날 마지막 수행값 불러오기").assertDoesNotExist()
        assertEquals("100", field("실제 중량"))
        assertEquals("10", field("실제 반복수"))
        compose.runOnIdle {
            assertTrue(source.sessionWrites.isEmpty())
            assertEquals("PENDING", model.state.value.session!!.entries.single().sets.first().status)
        }
    }

    @Test fun laterWeeklyPlanDoesNotRelabelAnAlreadyStartedFreeSession() {
        val fake = FakeWorkouts.withPlan().apply {
            val current = seedSession()
            sessions[current.id] = current.copy(planned = null, entries = emptyList())
        }
        start(fake)
        compose.onNodeWithText("자유 운동").assertExists()
        compose.runOnIdle {
            assertNull(model.state.value.session?.planned)
            assertNotNull(model.state.value.day?.planned)
        }
        click("운동 이어서 기록")
        compose.onNodeWithText("자유 운동").assertExists()
        compose.onNodeWithText("시작할 때의 원래 계획").assertDoesNotExist()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        try { FileOutputStream(File(instrumentation.targetContext.cacheDir, name)).use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
        finally { bitmap.recycle() }
    }

    /** Keeps immutable snapshots so tests can detect accidentally rewriting original plans or actual values. */
    internal class FakeWorkouts : WorkoutDataSource, ExerciseCatalogDataSource {
        override suspend fun exerciseCatalog(owner:String)=ExerciseCatalogDto("test","test source","https://example.com",listOf(
            CatalogExercise("dumbbell-bench","기본 덤벨 프레스","덤벨","가슴","가슴","WEIGHT_REPS","PER_HAND",listOf("DB press"),"덤벨 한 개의 무게를 적어요.")))
        override suspend fun importCatalogExercise(owner:String,key:String):ExerciseDto = exercises["catalog-copy"] ?: ExerciseDto("catalog-copy","기본 덤벨 프레스","덤벨","가슴","WEIGHT_REPS","PER_HAND",0).also { exercises[it.id]=it }

        val today = LocalDate.now().toString()
        val exercises = linkedMapOf<String, ExerciseDto>()
        val routines = linkedMapOf<String, RoutineDto>()
        val overrides = linkedMapOf<String, WorkoutOverrideDto>()
        val sessions = linkedMapOf<String, WorkoutSessionDto>()
        var plan = WorkoutPlanDto((1..7).map { WorkoutPlanSlot(it) })
        private var planSnapshots = emptyMap<Int, PlannedWorkout?>()
        var saveGate: CompletableDeferred<Unit>? = null
        var saveFailure: AccountException? = null
        val sessionWrites = mutableListOf<Pair<String, WorkoutSessionWrite>>()

        override suspend fun listExercises() = exercises.values.toList()
        override suspend fun saveExercise(id: String, exercise: ExerciseWrite): ExerciseDto {
            check(exercise.version == exercises[id]?.version)
            return ExerciseDto(id, exercise.name, exercise.equipment, exercise.target, exercise.recordType, exercise.loadConvention,
                (exercises[id]?.version ?: -1) + 1).also { exercises[id] = it }
        }
        override suspend fun deleteExercise(id: String, version: Long) {
            check(exercises[id]?.version == version)
            check(routines.values.none { routine -> routine.entries.any { it.exerciseId == id } })
            exercises.remove(id)
        }
        override suspend fun listRoutines() = routines.values.toList()
        override suspend fun saveRoutine(id: String, routine: RoutineWrite): RoutineDto {
            check(routine.version == routines[id]?.version)
            return RoutineDto(id, routine.name, routine.entries, routine.note, (routines[id]?.version ?: -1) + 1).also { routines[id] = it }
        }
        override suspend fun deleteRoutine(id: String, version: Long) {
            check(routines[id]?.version == version)
            check(plan.slots.none { it.routineId == id })
            routines.remove(id)
        }
        override suspend fun getWorkoutPlan() = plan
        override suspend fun saveWorkoutPlan(plan: WorkoutPlanWrite): WorkoutPlanDto {
            check(plan.version == this.plan.version)
            return applyPlan(plan.slots, (this.plan.version ?: -1) + 1)
        }
        private fun applyPlan(slots: List<WorkoutPlanSlot>, version: Long): WorkoutPlanDto {
            planSnapshots = slots.associate { it.dayOfWeek to it.routineId?.let(::resolve) }
            return WorkoutPlanDto(slots, version, today).also { plan = it }
        }
        private fun resolve(routineId: String): PlannedWorkout {
            val routine = routines.getValue(routineId)
            return PlannedWorkout(routine.id, routine.name, routine.version, routine.entries.map { entry ->
                PlannedExercise(entry.id, exercises.getValue(entry.exerciseId).snapshot(), entry.sets.toList(), entry.restSeconds, entry.note)
            })
        }
        private fun projection(date: String): PlannedWorkout? {
            val override = overrides[date]
            if (override != null) return override.workout
            if (date < (plan.effectiveFrom ?: today)) return null
            return planSnapshots[LocalDate.parse(date).dayOfWeek.value]
        }
        override suspend fun saveWorkoutOverride(date: String, override: WorkoutOverrideWrite): WorkoutOverrideDto {
            check(override.version == overrides[date]?.version)
            return WorkoutOverrideDto(date, override.routineId, override.routineId?.let(::resolve), override.note,
                (overrides[date]?.version ?: -1) + 1).also { overrides[date] = it }
        }
        override suspend fun deleteWorkoutOverride(date: String, version: Long) {
            check(overrides[date]?.version == version)
            overrides.remove(date)
        }
        override suspend fun getWorkoutDays(from: String, to: String): List<WorkoutDayDto> =
            generateSequence(LocalDate.parse(from)) { it.plusDays(1) }.takeWhile { it <= LocalDate.parse(to) }.map { date ->
                val key = date.toString()
                WorkoutDayDto(key, projection(key), overrides[key], sessions.values.firstOrNull { it.date == key })
            }.toList()

        fun seedSession(id: String = newId(), date: String = today): WorkoutSessionDto {
            val original = projection(date)
            return WorkoutSessionDto(id, date, original, original?.entries.orEmpty().map { planned ->
                WorkoutEntryDto(newId(), planned.id, planned.exercise, planned.sets.map { ActualSet(newId(), it.id) }, planned.restSeconds, planned.note)
            }, "IN_PROGRESS", version = 0, startedAt = Instant.now().toString()).also { sessions[id] = it }
        }
        override suspend fun startWorkoutSession(id: String, start: WorkoutStartWrite): WorkoutSessionDto =
            sessions[id]?.also { check(it.date == start.date) } ?: seedSession(id, start.date)

        override suspend fun saveWorkoutSession(id: String, session: WorkoutSessionWrite): WorkoutSessionDto {
            sessionWrites += id to session
            saveGate?.await()
            saveFailure?.let { throw it }
            val old = sessions.getValue(id)
            check(old.version == session.version)
            val entries = session.entries.map { write ->
                val existing = old.entries.firstOrNull { it.id == write.id && it.exercise.id == write.exerciseId }
                val snapshot = existing?.exercise ?: exercises.getValue(write.exerciseId).snapshot()
                WorkoutEntryDto(write.id, write.plannedEntryId, snapshot, write.sets.toList(), write.restSeconds, write.note, write.replacementReason)
            }
            return old.copy(entries = entries, status = session.status, note = session.note, version = old.version + 1,
                finishedAt = if (session.status == "COMPLETED") old.finishedAt ?: Instant.now().toString() else null).also { sessions[id] = it }
        }
        override suspend fun deleteWorkoutSession(id: String, version: Long) {
            check(sessions[id]?.version == version)
            sessions.remove(id)
        }
        override suspend fun getWorkoutHistory(exerciseId: String, before: String): List<WorkoutHistoryItem> =
            sessions.values.filter { it.date < before }.sortedByDescending { it.date }.flatMap { session ->
                session.entries.filter { entry -> entry.exercise.id == exerciseId && entry.sets.any { it.status == "DONE" } }
                    .map { WorkoutHistoryItem(session.date, session.id, it) }
            }.take(10)

        companion object {
            fun withPlan(type: String = "WEIGHT_REPS", sets: Int = 2, rest: Int = 90) = FakeWorkouts().apply {
                val exercise = ExerciseDto(newId(), if (type == "DURATION") "테스트 경사 걷기" else "테스트 바벨 스쿼트",
                    if (type == "DURATION") "테스트 트레드밀" else "테스트 바벨", "하체", type, "TOTAL", 0)
                exercises[exercise.id] = exercise
                val routine = RoutineDto(newId(), "테스트 기본 루틴", listOf(RoutineEntry(newId(), exercise.id, (1..sets).map {
                    PlannedSet(newId(), weightKg = if (type == "WEIGHT_REPS") BigDecimal("100") else null,
                        reps = if (type in setOf("WEIGHT_REPS", "REPS")) 10 else null,
                        durationSeconds = if (type == "DURATION") 60 else null)
                }, rest)), version = 0)
                routines[routine.id] = routine
                applyPlan((1..7).map { WorkoutPlanSlot(it, routine.id) }, 0)
            }
        }
    }

    private companion object {
        const val SAVE_ERROR = "저장하지 못했어요. 연결을 확인하고 다시 시도해주세요."
        fun newId() = UUID.randomUUID().toString()
        fun weekday(day: Int) = listOf("월", "화", "수", "목", "금", "토", "일")[day - 1]
        fun ExerciseDto.snapshot() = ExerciseSnapshot(id, name, equipment, target, recordType, loadConvention)
        fun assertDecimal(expected: String, actual: BigDecimal?) {
            assertNotNull(actual)
            assertEquals(0, BigDecimal(expected).compareTo(requireNotNull(actual)))
        }
    }
}
