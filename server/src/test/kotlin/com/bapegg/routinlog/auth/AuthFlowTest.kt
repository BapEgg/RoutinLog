package com.bapegg.routinlog.auth

import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.doThrow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import tools.jackson.databind.ObjectMapper
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** H2 API/transaction checks with a mocked Google verification boundary; no real Google sign-in is simulated in production. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowTest @Autowired constructor(
    private val mvc: MockMvc,
    private val mapper: ObjectMapper,
    private val jdbc: JdbcTemplate,
    private val sessions: AuthSessionService,
) {
    @MockitoBean lateinit var verifier: GoogleTokenVerifier
    private lateinit var subject: String

    @BeforeEach
    fun configureVerifier() {
        subject = "verified-test-subject-${UUID.randomUUID()}"
        doReturn(VerifiedGoogleIdentity(subject)).`when`(verifier).verify(anyString(), anyString())
    }

    @Test
    fun `login creates an internal account and stores only credential hashes`() {
        val tokens = login()
        assertTrue(TokenSecrets.isAccess(tokens.access))
        assertTrue(TokenSecrets.isRefresh(tokens.refresh))
        val principal = assertNotNull(sessions.authenticate(tokens.access))
        assertEquals(tokens.userId, principal.userId)
        assertEquals(TokenSecrets.hash(tokens.access), jdbc.queryForObject(
            "select access_token_hash from auth_sessions where id = ?", String::class.java, principal.sessionId,
        ))
        assertEquals(1L, jdbc.queryForObject(
            "select count(*) from auth_refresh_tokens where token_hash = ?", Long::class.java, TokenSecrets.hash(tokens.refresh),
        ))
        assertEquals(0L, jdbc.queryForObject(
            "select count(*) from auth_sessions where access_token_hash = ?", Long::class.java, tokens.access,
        ))
        mvc.perform(get("/api/v1/future-feature").header(HttpHeaders.AUTHORIZATION, "Bearer ${tokens.access}"))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `same verified subject reuses its internal user ID across sessions`() {
        val first = login()
        val second = login()
        assertEquals(first.userId, second.userId)
        assertNotEquals(first.access, second.access)
    }

    @Test
    fun `challenge expires and cannot be consumed twice`() {
        val challenge = challenge()
        complete(challenge).andExpect(status().isOk)
        complete(challenge).andExpect(status().isUnauthorized).andExpect(jsonPath("$.code").value("AUTH_CHALLENGE_INVALID"))
        val expired = challenge()
        jdbc.update("update auth_challenges set created_at = ?, expires_at = ? where id = ?",
            Timestamp.from(Instant.now().minusSeconds(600)), Timestamp.from(Instant.now().minusSeconds(1)), UUID.fromString(expired))
        complete(expired).andExpect(status().isUnauthorized)
    }

    @Test
    fun `failed Google verification does not issue a session`() {
        val challenge = challenge()
        doThrow(AuthFailure()).`when`(verifier).verify(anyString(), anyString())
        complete(challenge).andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.accessToken").doesNotExist())
            .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
    }

    @Test
    fun `refresh rotates both credentials and replay revokes the newly rotated session`() {
        val original = login()
        val rotated = readTokens(refresh(original.refresh).andExpect(status().isOk).andReturn().response.contentAsString)
        assertNotEquals(original.access, rotated.access)
        assertNotEquals(original.refresh, rotated.refresh)
        assertNull(sessions.authenticate(original.access))
        assertNotNull(sessions.authenticate(rotated.access))
        refresh(original.refresh).andExpect(status().isUnauthorized)
        assertNull(sessions.authenticate(rotated.access))
        refresh(rotated.refresh).andExpect(status().isUnauthorized)
    }

    @Test
    fun `simultaneous refresh accepts at most one use and commits replay revocation`() {
        val original = login()
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val results = (1..2).map {
                executor.submit<SessionTokens?> { start.await(); sessions.refresh(original.refresh) }
            }
            start.countDown()
            val responses = results.map { it.get(10, TimeUnit.SECONDS) }
            assertEquals(1, responses.count { it != null })
            val accepted = responses.first { it != null }!!
            assertNull(sessions.authenticate(accepted.accessToken))
            assertNull(sessions.refresh(accepted.refreshToken))
        } finally { executor.shutdownNow() }
    }

    @Test
    fun `logout revokes only the authenticated session and does not set cookies`() {
        val first = login()
        val second = login()
        mvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer ${first.access}")
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(mapOf("refreshToken" to second.refresh))))
            .andExpect(status().isUnauthorized)
        assertNotNull(sessions.authenticate(first.access))
        mvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer ${first.access}"))
            .andExpect(status().isNoContent).andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
        assertNull(sessions.authenticate(first.access))
        refresh(first.refresh).andExpect(status().isUnauthorized)
        assertNotNull(sessions.authenticate(second.access))
    }

    @Test
    fun `expired access cannot authenticate while an unexpired refresh can renew it`() {
        val original = login()
        jdbc.update("update auth_sessions set access_expires_at = ? where access_token_hash = ?",
            Timestamp.from(Instant.now().minusSeconds(1)), TokenSecrets.hash(original.access))
        assertNull(sessions.authenticate(original.access))
        refresh(original.refresh).andExpect(status().isOk)
    }

    @Test
    fun `expired session and non-active account cannot refresh`() {
        val expired = login()
        jdbc.update("update auth_sessions set created_at = ?, expires_at = ? where access_token_hash = ?",
            Timestamp.from(Instant.now().minusSeconds(600)), Timestamp.from(Instant.now().minusSeconds(1)), TokenSecrets.hash(expired.access))
        refresh(expired.refresh).andExpect(status().isUnauthorized)
        val disabled = login()
        jdbc.update("update user_accounts set status = 'DELETION_REQUESTED' where id = ?", disabled.userId)
        assertNull(sessions.authenticate(disabled.access))
        refresh(disabled.refresh).andExpect(status().isUnauthorized)
    }

    @Test
    fun `invalid authentication requests return bounded errors without echoing credentials`() {
        mvc.perform(post("/api/v1/auth/google").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(mapOf("idToken" to "x".repeat(16385), "challengeId" to UUID.randomUUID().toString()))))
            .andExpect(status().isBadRequest).andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        refresh("not-a-valid-refresh").andExpect(status().isUnauthorized)
        mvc.perform(get("/api/v1/body-measurements").header(HttpHeaders.AUTHORIZATION, "Bearer invalid"))
            .andExpect(status().isUnauthorized).andExpect(jsonPath("$.code").value("AUTH_INVALID"))
    }

    private fun challenge(): String {
        val response = mvc.perform(post("/api/v1/auth/google/challenge"))
            .andExpect(status().isOk).andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
            .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE)).andReturn().response.contentAsString
        val json = mapper.readTree(response)
        val nonce = json["nonce"].asText()
        val id = json["challengeId"].asText()
        assertEquals(TokenSecrets.hash(nonce), jdbc.queryForObject("select nonce_hash from auth_challenges where id = ?", String::class.java, UUID.fromString(id)))
        return id
    }

    private fun complete(challenge: String) = mvc.perform(post("/api/v1/auth/google")
        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(mapOf("idToken" to "test-verifier-input", "challengeId" to challenge))))

    private fun login(): Tokens = readTokens(complete(challenge()).andExpect(status().isOk)
        .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
        .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE)).andExpect(jsonPath("$.expiresIn").value(3600))
        .andReturn().response.contentAsString)

    private fun refresh(token: String) = mvc.perform(post("/api/v1/auth/refresh")
        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(mapOf("refreshToken" to token))))

    private fun readTokens(body: String): Tokens {
        val json = mapper.readTree(body)
        return Tokens(json["accessToken"].asText(), json["refreshToken"].asText(), UUID.fromString(json["userId"].asText()))
    }

    private class Tokens(val access: String, val refresh: String, val userId: UUID)
}
