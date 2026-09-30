package com.bapegg.routinlog.data

import com.bapegg.routinlog.auth.SessionStore
import com.bapegg.routinlog.auth.StoredSession
import com.google.gson.JsonParser
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class AccountRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var store: MemoryStore
    private lateinit var repository: AccountRepository

    @Before fun setup() {
        server = MockWebServer().apply { start(InetAddress.getByName("127.0.0.1"), 0) }
        store = MemoryStore(StoredSession("stored-access", "stored-refresh", 9000, "user-1"))
        repository = AccountRepository.forTesting(loopbackUrl(server), store) { 1000 }
    }
    @After fun shutdown() { server.shutdown() }

    @Test fun `restoration rotates refresh and exposes only account identity`() = runBlocking {
        restore()
        assertEquals("user-1", repository.identity.value?.userId)
        assertEquals("access-1", store.value?.accessToken)
        assertEquals("refresh-1", store.value?.refreshToken)
        assertFalse(store.value.toString().contains("refresh-1"))
        assertFalse(repository.identity.value.toString().contains("access-1"))
    }

    @Test fun `simultaneous rejected requests share a single refresh rotation`() = runBlocking {
        restore()
        val rejected = CountDownLatch(2)
        val refreshes = AtomicInteger(0)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path == "/api/v1/auth/refresh" -> { refreshes.incrementAndGet(); json(tokens("2")) }
                request.getHeader("Authorization") == "Bearer access-1" -> {
                    rejected.countDown(); check(rejected.await(5, TimeUnit.SECONDS)); json("{}", 401)
                }
                request.getHeader("Authorization") == "Bearer access-2" -> json(profileJson)
                else -> json("{}", 500)
            }
        }
        val profiles = listOf(async { repository.getProfile() }, async { repository.getProfile() }).awaitAll()
        assertEquals(2, profiles.size)
        assertEquals(1, refreshes.get())
        assertEquals("refresh-2", store.value?.refreshToken)
        assertEquals(0L, profiles[0]?.version)
    }

    @Test fun `invalid refresh clears stored session and reports expiration`() = runBlocking {
        server.enqueue(json("{\"code\":\"AUTH_INVALID\"}", 401))
        val error = failure { repository.restoreSession() }
        assertEquals(AccountErrorKind.EXPIRED, error.kind)
        assertNull(store.value)
        assertNull(repository.identity.value)
    }

    @Test fun `network failure during restore preserves refresh token for retry`() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        val error = failure { repository.restoreSession() }
        assertEquals(AccountErrorKind.NETWORK, error.kind)
        assertEquals("stored-refresh", store.value?.refreshToken)
        assertNull(repository.identity.value)
    }

    @Test fun `refresh service failure does not log user out`() = runBlocking {
        restore()
        server.enqueue(json("{}", 401))
        server.enqueue(json("{\"code\":\"TEMPORARY_FAILURE\"}", 503))
        val error = failure { repository.getProfile() }
        assertEquals(AccountErrorKind.SERVER, error.kind)
        assertEquals("refresh-1", store.value?.refreshToken)
        assertEquals("user-1", repository.identity.value?.userId)
    }

    @Test fun `request retries only once when fresh access is also rejected`() = runBlocking {
        restore()
        val before = server.requestCount
        server.enqueue(json("{}", 401)); server.enqueue(json(tokens("2"))); server.enqueue(json("{}", 401))
        assertEquals(AccountErrorKind.EXPIRED, failure { repository.getProfile() }.kind)
        assertEquals(3, server.requestCount - before)
        assertNull(store.value)
    }

    @Test fun `missing profile is distinct from network failure`() = runBlocking {
        restore()
        server.enqueue(json("{\"code\":\"PROFILE_NOT_FOUND\"}", 404))
        assertNull(repository.getProfile())
        assertNotNull(repository.identity.value)
    }

    @Test fun `conflict is surfaced without backend message or credential material`() = runBlocking {
        restore()
        server.enqueue(json("{\"code\":\"VERSION_CONFLICT\",\"message\":\"must-not-surface access-1\"}", 409))
        val error = failure { repository.saveBody("2026-09-30", BodyMeasurementWriteDto(83.2, null, 0)) }
        assertEquals(AccountErrorKind.CONFLICT, error.kind)
        assertEquals("VERSION_CONFLICT", error.code)
        assertFalse(error.toString().contains("must-not-surface"))
        assertFalse(error.toString().contains("access-1"))
        assertNull(error.cause)
    }

    @Test fun `logout remains signed in on server failure and clears only after success`() = runBlocking {
        restore()
        server.enqueue(json("{}", 500))
        assertEquals(AccountErrorKind.SERVER, failure { repository.logout() }.kind)
        assertNotNull(store.value)
        assertNotNull(repository.identity.value)
        server.enqueue(MockResponse().setResponseCode(204))
        repository.logout()
        assertNull(store.value)
        assertNull(repository.identity.value)
    }

    @Test fun `measurement writes preserve unknown null and first-create version`() = runBlocking {
        restore()
        server.enqueue(json("{\"date\":\"2026-09-30\",\"weightKg\":83.2,\"waistCm\":null,\"version\":0,\"memo\":\"기상 후\"}"))
        val result = repository.saveBody("2026-09-30", BodyMeasurementWriteDto(83.2, null, memo = "기상 후"))
        val request = server.takeRequest(2, TimeUnit.SECONDS)!!
        val body = JsonParser.parseString(request.body.readUtf8()).asJsonObject
        assertEquals("PUT", request.method)
        assertEquals("/api/v1/body-measurements/2026-09-30", request.path)
        assertTrue(body.get("waistCm").isJsonNull)
        assertTrue(body.get("version").isJsonNull)
        assertEquals("기상 후", result.memo)
        assertEquals(0L, result.version)
    }

    @Test fun `account deletion requires fresh nonce and clears only after server deletion`() = runBlocking {
        var providerClears = 0
        repository = AccountRepository.forTesting(loopbackUrl(server), store, clearProviderState = {
            providerClears++
            throw IllegalStateException("Provider picker cleanup failure must not undo deletion")
        }) { 1000 }
        restore()
        server.enqueue(json("""{"challengeId":"challenge-delete","nonce":"fresh-server-nonce"}"""))
        server.enqueue(MockResponse().setResponseCode(204))
        repository.deleteAccountWithCredential { nonce ->
            assertEquals("fresh-server-nonce", nonce)
            assertNotNull(store.value)
            assertNotNull(repository.identity.value)
            "fresh-google-id-token"
        }
        assertEquals("/api/v1/auth/google/challenge", server.takeRequest(2, TimeUnit.SECONDS)!!.path)
        val deleted = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("DELETE", deleted.method)
        assertEquals("/api/v1/me", deleted.path)
        assertEquals("Bearer access-1", deleted.getHeader("Authorization"))
        val body = JsonParser.parseString(deleted.body.readUtf8()).asJsonObject
        assertEquals("fresh-google-id-token", body.get("idToken").asString)
        assertEquals("challenge-delete", body.get("challengeId").asString)
        assertNull(store.value)
        assertNull(repository.identity.value)
        assertEquals(1, providerClears)
    }

    @Test fun `deletion rejects another Google account without refresh or session loss`() = runBlocking {
        restore()
        val before = server.requestCount
        server.enqueue(json("""{"challengeId":"challenge-delete","nonce":"fresh-server-nonce"}"""))
        server.enqueue(json("""{"code":"AUTH_ACCOUNT_MISMATCH","message":"private token details"}""", 403))
        val error = failure { repository.deleteAccountWithCredential { "different-google-id-token" } }
        assertEquals(AccountErrorKind.CREDENTIAL, error.kind)
        assertEquals("AUTH_ACCOUNT_MISMATCH", error.code)
        assertFalse(error.toString().contains("private token details"))
        assertEquals(2, server.requestCount - before)
        assertEquals("refresh-1", store.value?.refreshToken)
        assertEquals("user-1", repository.identity.value?.userId)
    }

    @Test fun `deletion server failure keeps the account available for retry`() = runBlocking {
        restore()
        server.enqueue(json("""{"challengeId":"challenge-delete","nonce":"fresh-server-nonce"}"""))
        server.enqueue(json("{}", 503))
        assertEquals(AccountErrorKind.SERVER, failure { repository.deleteAccountWithCredential { "google-id-token" } }.kind)
        assertNotNull(store.value)
        assertNotNull(repository.identity.value)
    }

    @Test fun `profile NONE mode serializes unknown nutrition and planned versus recent exercise`() = runBlocking {
        restore()
        server.enqueue(json(profileJson))
        repository.saveProfile(profile(nutritionMode = "NONE"))
        val request = server.takeRequest(2, TimeUnit.SECONDS)!!
        val body = JsonParser.parseString(request.body.readUtf8()).asJsonObject
        assertEquals("/api/v1/me/profile", request.path)
        assertTrue(body.get("dailyCalories").isJsonNull)
        assertTrue(body.get("proteinG").isJsonNull)
        assertTrue(body.get("fiberG").isJsonNull)
        assertEquals(4, body.getAsJsonArray("exerciseDays").size())
        assertEquals(2, body.get("recentExerciseDays").asInt)
        assertEquals("MIXED", body.get("recentExerciseType").asString)
    }

    @Test fun `body queries pass explicit date bounds and delete version`() = runBlocking {
        restore()
        server.enqueue(json("{\"items\":[]}"))
        assertTrue(repository.listBody("2026-09-01", "2026-09-30").isEmpty())
        assertEquals("/api/v1/body-measurements?from=2026-09-01&to=2026-09-30", server.takeRequest(2, TimeUnit.SECONDS)!!.path)
        server.enqueue(MockResponse().setResponseCode(204))
        repository.deleteBody("2026-09-30", 4)
        val delete = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("DELETE", delete.method)
        assertEquals("/api/v1/body-measurements/2026-09-30?version=4", delete.path)
    }

    @Test fun `redirect cannot forward authorization to another origin`() = runBlocking {
        restore()
        val other = MockWebServer().apply { start(InetAddress.getByName("127.0.0.1"), 0) }
        try {
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", loopbackUrl(other, "/collect")))
            assertEquals(AccountErrorKind.SERVER, failure { repository.getProfile() }.kind)
            assertEquals(0, other.requestCount)
        } finally { other.shutdown() }
    }

    @Test fun `nonlocal cleartext and credential-bearing URLs are rejected before a request`() {
        for (url in listOf("http://example.com/", "https://user:password@example.com/", "https://example.com/?token=bad")) {
            try { AccountRepository.forTesting(url, store); fail("Expected rejected URL") }
            catch (error: AccountException) { assertEquals(AccountErrorKind.CONFIGURATION, error.kind); assertFalse(error.message.orEmpty().contains(url)) }
        }
    }

    private suspend fun restore() {
        server.enqueue(json(tokens("1")))
        assertEquals("user-1", repository.restoreSession()?.userId)
        val request = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("/api/v1/auth/refresh", request.path)
        assertEquals("stored-refresh", JsonParser.parseString(request.body.readUtf8()).asJsonObject.get("refreshToken").asString)
    }
    private suspend fun failure(block: suspend () -> Any?): AccountException {
        try { block(); fail("Expected AccountException") } catch (error: AccountException) { return error }
        error("Unreachable")
    }
    private fun tokens(n: String) = """{"accessToken":"access-$n","refreshToken":"refresh-$n","expiresIn":3600,"userId":"user-1"}"""
    // On Windows MockWebServer may reverse-resolve a loopback bind address to the machine hostname.
    private fun loopbackUrl(server: MockWebServer, path: String = "/") =
        server.url(path).newBuilder().host("127.0.0.1").build().toString()
    private fun json(value: String, status: Int = 200) = MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(value)
    private fun profile(nutritionMode: String) = ProfileDto(
        age = 32, sex = "MALE", heightCm = 178.0, initialWeightKg = 83.2, goal = "GAIN", activityLevel = "SEDENTARY",
        exerciseDays = listOf(1, 2, 4, 5), exerciseMinutes = 45, experience = "INTERMEDIATE", units = "METRIC",
        nutritionMode = nutritionMode, termsVersion = "2026-09-30", privacyVersion = "2026-09-30", healthConsentVersion = "2026-09-30",
        timeZone = "Asia/Seoul", effectiveFrom = "2026-09-30", recentExerciseDays = 2, recentExerciseMinutes = 45, recentExerciseType = "MIXED",
    )
    private val profileJson = """{"age":32,"sex":"MALE","heightCm":178.0,"initialWeightKg":83.2,"goal":"GAIN","activityLevel":"SEDENTARY","exerciseDays":[1,2,4,5],"exerciseMinutes":45,"experience":"INTERMEDIATE","units":"METRIC","nutritionMode":"NONE","dailyCalories":null,"carbohydrateG":null,"proteinG":null,"fatG":null,"fiberG":null,"termsVersion":"2026-09-30","privacyVersion":"2026-09-30","healthConsentVersion":"2026-09-30","timeZone":"Asia/Seoul","effectiveFrom":"2026-09-30","version":0,"recentExerciseType":"MIXED","recentExerciseIntensity":"MODERATE"}"""

    private class MemoryStore(@Volatile var value: StoredSession?) : SessionStore {
        override fun read() = value
        override fun write(session: StoredSession) { value = session }
        override fun clear() { value = null }
    }
}
