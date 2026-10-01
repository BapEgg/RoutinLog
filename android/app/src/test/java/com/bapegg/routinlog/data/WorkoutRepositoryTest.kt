package com.bapegg.routinlog.data

import com.bapegg.routinlog.auth.SessionStore
import com.bapegg.routinlog.auth.StoredSession
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/** Synthetic transport fixtures, never a shipped exercise prescription or public program. */
class WorkoutRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: AccountRepository
    private lateinit var store: MemoryStore
    private val exerciseId = "10000000-0000-0000-0000-000000000001"
    private val routineId = "20000000-0000-0000-0000-000000000001"
    private val plannedEntryId = "30000000-0000-0000-0000-000000000001"
    private val planSetId = "40000000-0000-0000-0000-000000000001"
    private val sessionId = "50000000-0000-0000-0000-000000000001"
    private val actualEntryId = "60000000-0000-0000-0000-000000000001"
    private val actualSetId = "70000000-0000-0000-0000-000000000001"

    @Before fun setUp() = runBlocking {
        server = MockWebServer().apply { start(InetAddress.getByName("127.0.0.1"), 0) }
        store = MemoryStore(StoredSession("fake-old-access", "fake-old-refresh", 9000, "fake-user"))
        repository = AccountRepository.forTesting(server.url("/").newBuilder().host("127.0.0.1").build().toString(), store) { 1000 }
        server.enqueue(json(tokens("1")))
        repository.restoreSession()
        take()
        Unit
    }
    @After fun tearDown() { server.shutdown() }

    @Test fun `exercise library uses owner-authenticated requests and explicit load convention`() = runBlocking {
        server.enqueue(json("""{"items":[$exerciseJson]}"""))
        val exercise = repository.listExercises().single()
        val list = take()
        assertEquals("/api/v1/workout-exercises", list.path)
        assertEquals("Bearer fake-access-1", list.getHeader("Authorization"))
        assertEquals("PER_HAND", exercise.loadConvention)
        server.enqueue(json(exerciseJson))
        repository.saveExercise(exerciseId, ExerciseWrite("사용자 종목", "덤벨", "직접 지정", "WEIGHT_REPS", "PER_HAND"))
        val request = take()
        assertEquals("PUT", request.method)
        assertEquals("/api/v1/workout-exercises/$exerciseId", request.path)
        val body = body(request)
        assertEquals("WEIGHT_REPS", body.get("recordType").asString)
        assertTrue(body.get("version").isJsonNull)
        server.enqueue(MockResponse().setResponseCode(204))
        repository.deleteExercise(exerciseId, 0)
        assertEquals("/api/v1/workout-exercises/$exerciseId?version=0", take().path)
    }

    @Test fun `routine writes preserve decimal kg unknown targets and warmup without client snapshots`() = runBlocking {
        server.enqueue(json(routineJson))
        repository.saveRoutine(routineId, RoutineWrite("내 루틴", listOf(RoutineEntry(plannedEntryId, exerciseId,
            listOf(PlannedSet(planSetId, weightKg = BigDecimal("16.125"), reps = null, warmup = true)), restSeconds = 60))))
        val request = take()
        val body = body(request)
        val entry = body.getAsJsonArray("entries").single().asJsonObject
        val set = entry.getAsJsonArray("sets").single().asJsonObject
        assertEquals("/api/v1/workout-routines/$routineId", request.path)
        assertEquals(BigDecimal("16.125"), set.get("weightKg").asBigDecimal)
        assertTrue(set.get("weightKg").asJsonPrimitive.isNumber)
        assertTrue(set.get("reps").isJsonNull)
        assertTrue(set.get("durationSeconds").isJsonNull)
        assertTrue(set.get("warmup").asBoolean)
        assertFalse(entry.has("exercise"))
        assertTrue(body.get("version").isJsonNull)
        server.enqueue(json("""{"items":[$routineJson]}"""))
        assertEquals(routineId, repository.listRoutines().single().id)
        assertEquals("/api/v1/workout-routines", take().path)
        server.enqueue(MockResponse().setResponseCode(204))
        repository.deleteRoutine(routineId, 2)
        assertEquals("/api/v1/workout-routines/$routineId?version=2", take().path)
    }

    @Test fun `weekly plan sends seven weekday references and reads immutable revision metadata`() = runBlocking {
        val planJson = """{"slots":[$slotsJson],"version":3,"effectiveFrom":"2026-10-01"}"""
        server.enqueue(json(planJson))
        assertEquals(3L, repository.getWorkoutPlan().version)
        assertEquals("/api/v1/workout-plan", take().path)
        server.enqueue(json(planJson))
        val saved = repository.saveWorkoutPlan(WorkoutPlanWrite((1..7).map { WorkoutPlanSlot(it, if (it == 1) routineId else null) }, 2))
        val request = take()
        assertEquals("PUT", request.method)
        val body = body(request)
        assertEquals(7, body.getAsJsonArray("slots").size())
        assertEquals(2L, body.get("version").asLong)
        assertFalse(body.has("effectiveFrom"))
        assertFalse(body.has("planned"))
        assertEquals("2026-10-01", saved.effectiveFrom)
    }

    @Test fun `dated rest override stays explicit null and delete restores projection with version`() = runBlocking {
        server.enqueue(json("""{"date":"2026-10-01","routineId":null,"workout":null,"note":"오늘은 휴식","version":0}"""))
        val saved = repository.saveWorkoutOverride("2026-10-01", WorkoutOverrideWrite(note = "오늘은 휴식"))
        val request = take()
        assertEquals("/api/v1/workout-overrides/2026-10-01", request.path)
        assertTrue(body(request).get("routineId").isJsonNull)
        assertNull(saved.workout)
        server.enqueue(MockResponse().setResponseCode(204))
        repository.deleteWorkoutOverride("2026-10-01", 0)
        assertEquals("/api/v1/workout-overrides/2026-10-01?version=0", take().path)
    }

    @Test fun `day retains current projection separately from started session baseline`() = runBlocking {
        val currentPlan = plannedJson.replace("내 루틴", "오늘 바뀐 루틴")
        server.enqueue(json("""{"items":[{"date":"2026-10-01","planned":$currentPlan,"override":null,"session":$sessionJson}]}"""))
        val day = repository.getWorkoutDays("2026-10-01", "2026-10-01").single()
        assertEquals("/api/v1/workout-days?from=2026-10-01&to=2026-10-01", take().path)
        assertEquals("오늘 바뀐 루틴", day.planned!!.routineName)
        assertEquals("내 루틴", day.session!!.planned!!.routineName)
        assertNull(day.`override`)
        assertEquals(BigDecimal("100.125"), day.session!!.planned!!.entries.single().sets.single().weightKg)
        assertNull(day.session!!.entries.single().sets.single().weightKg)
    }

    @Test fun `session start retry reuses stable id and sends date only`() = runBlocking {
        repeat(2) {
            server.enqueue(json(sessionJson))
            val saved = repository.startWorkoutSession(sessionId, WorkoutStartWrite("2026-10-01"))
            assertEquals(sessionId, saved.id)
            assertEquals("PENDING", saved.entries.single().sets.single().status)
            assertNull(saved.entries.single().sets.single().weightKg)
            val request = take()
            assertEquals("PUT", request.method)
            assertEquals("/api/v1/workout-sessions/$sessionId/start", request.path)
            assertEquals(setOf("date"), body(request).keySet())
        }
    }

    @Test fun `session update contains references and actuals never trusted planned or exercise snapshot`() = runBlocking {
        val updatedEntry = """{"id":"$actualEntryId","plannedEntryId":"$plannedEntryId","exercise":$snapshotJson,"sets":[{"id":"$actualSetId","planSetId":"$planSetId","status":"DONE","weightKg":80.125,"reps":8,"durationSeconds":null,"note":null},{"id":"70000000-0000-0000-0000-000000000002","planSetId":null,"status":"SKIPPED","weightKg":null,"reps":null,"durationSeconds":null,"note":null}],"restSeconds":90,"note":null,"replacementReason":"기구 사용 중"}"""
        server.enqueue(json(sessionJson.replace(actualEntryJson, updatedEntry).replace("\"version\":0", "\"version\":5")))
        val entry = WorkoutEntryWrite(actualEntryId, plannedEntryId, exerciseId, listOf(
            ActualSet(actualSetId, planSetId, "DONE", BigDecimal("80.125"), 8),
            ActualSet("70000000-0000-0000-0000-000000000002", status = "SKIPPED"),
        ), replacementReason = "기구 사용 중")
        repository.saveWorkoutSession(sessionId, WorkoutSessionWrite(listOf(entry), note = "사용자 메모", version = 4))
        val request = take()
        assertEquals("/api/v1/workout-sessions/$sessionId", request.path)
        val body = body(request)
        assertEquals(setOf("entries", "status", "note", "version"), body.keySet())
        assertEquals(4L, body.get("version").asLong)
        val sentEntry = body.getAsJsonArray("entries").single().asJsonObject
        assertTrue(sentEntry.has("exerciseId"))
        assertFalse(sentEntry.has("exercise"))
        assertFalse(sentEntry.has("planned"))
        val sets = sentEntry.getAsJsonArray("sets")
        assertEquals(BigDecimal("80.125"), sets[0].asJsonObject.get("weightKg").asBigDecimal)
        assertTrue(sets[1].asJsonObject.get("weightKg").isJsonNull)
        assertTrue(sets[1].asJsonObject.get("reps").isJsonNull)
        assertTrue(sets[1].asJsonObject.get("durationSeconds").isJsonNull)
    }

    @Test fun `workout conflicts remain actionable without leaking server details or losing session`() = runBlocking {
        for (code in listOf("SESSION_EXISTS", "REPLACEMENT_REQUIRES_NEW_ENTRY", "RESOURCE_IN_USE")) {
            server.enqueue(json("""{"code":"$code","message":"private exercise or token detail"}""", 409))
            val error = failure { repository.deleteExercise(exerciseId, 0) }
            assertEquals(code, error.code)
            assertEquals(AccountErrorKind.CONFLICT, error.kind)
            assertFalse(error.userMessage.contains("private exercise"))
            assertNotNull(repository.identity.value)
            assertNotNull(store.value)
        }
    }

    @Test fun `history keeps prior exercise snapshot and delete passes latest session version`() = runBlocking {
        val doneEntry = actualEntryJson.replace("\"status\":\"PENDING\"", "\"status\":\"DONE\"")
            .replace("\"weightKg\":null", "\"weightKg\":80.125").replace("\"reps\":null", "\"reps\":8")
        server.enqueue(json("""{"items":[{"date":"2026-09-30","sessionId":"$sessionId","entry":$doneEntry}]}"""))
        val history = repository.getWorkoutHistory(exerciseId, "2026-10-01").single()
        assertEquals("/api/v1/workout-history?exerciseId=$exerciseId&before=2026-10-01", take().path)
        assertEquals("기록 당시 종목", history.entry.exercise.name)
        assertEquals("DONE", history.entry.sets.single().status)
        server.enqueue(MockResponse().setResponseCode(204))
        repository.deleteWorkoutSession(sessionId, 5)
        val deleted = take()
        assertEquals("DELETE", deleted.method)
        assertEquals("/api/v1/workout-sessions/$sessionId?version=5", deleted.path)
    }

    @Test fun `workout transport reuses authenticated refresh once`() = runBlocking {
        server.enqueue(json("{}", 401))
        server.enqueue(json(tokens("2")))
        server.enqueue(json("""{"items":[]}"""))
        assertTrue(repository.listExercises().isEmpty())
        assertEquals("Bearer fake-access-1", take().getHeader("Authorization"))
        assertEquals("/api/v1/auth/refresh", take().path)
        assertEquals("Bearer fake-access-2", take().getHeader("Authorization"))
        assertEquals("fake-refresh-2", store.value?.refreshToken)
    }

    private fun take(): RecordedRequest = checkNotNull(server.takeRequest(2, TimeUnit.SECONDS))
    private fun body(request: RecordedRequest) = JsonParser.parseString(request.body.readUtf8()).asJsonObject
    private fun json(value: String, code: Int = 200) = MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(value)
    private fun tokens(suffix: String) = """{"accessToken":"fake-access-$suffix","refreshToken":"fake-refresh-$suffix","expiresIn":3600,"userId":"fake-user"}"""
    private suspend fun failure(block: suspend () -> Any?): AccountException {
        try { block(); fail("Expected AccountException") } catch (error: AccountException) { return error }
        error("Unreachable")
    }
    private val exerciseJson get() = """{"id":"$exerciseId","name":"사용자 종목","equipment":"덤벨","target":"직접 지정","recordType":"WEIGHT_REPS","loadConvention":"PER_HAND","version":0}"""
    private val snapshotJson get() = """{"id":"$exerciseId","name":"기록 당시 종목","equipment":"덤벨","target":"직접 지정","recordType":"WEIGHT_REPS","loadConvention":"PER_HAND"}"""
    private val plannedSetJson get() = """{"id":"$planSetId","weightKg":100.125,"reps":10,"durationSeconds":null,"warmup":false}"""
    private val routineJson get() = """{"id":"$routineId","name":"내 루틴","entries":[{"id":"$plannedEntryId","exerciseId":"$exerciseId","sets":[$plannedSetJson],"restSeconds":90,"note":null}],"note":null,"version":2}"""
    private val slotsJson get() = (1..7).joinToString(",") { """{"dayOfWeek":$it,"routineId":${if (it == 1) "\"$routineId\"" else "null"}}""" }
    private val plannedJson get() = """{"routineId":"$routineId","routineName":"내 루틴","routineVersion":2,"entries":[{"id":"$plannedEntryId","exercise":$snapshotJson,"sets":[$plannedSetJson],"restSeconds":90,"note":null}]}"""
    private val actualEntryJson get() = """{"id":"$actualEntryId","plannedEntryId":"$plannedEntryId","exercise":$snapshotJson,"sets":[{"id":"$actualSetId","planSetId":"$planSetId","status":"PENDING","weightKg":null,"reps":null,"durationSeconds":null,"note":null}],"restSeconds":90,"note":null,"replacementReason":null}"""
    private val sessionJson get() = """{"id":"$sessionId","date":"2026-10-01","planned":$plannedJson,"entries":[$actualEntryJson],"status":"IN_PROGRESS","note":null,"version":0,"startedAt":"2026-10-01T01:02:03Z","finishedAt":null}"""
    private class MemoryStore(var value: StoredSession?) : SessionStore {
        override fun read() = value
        override fun write(session: StoredSession) { value = session }
        override fun clear() { value = null }
    }
}
