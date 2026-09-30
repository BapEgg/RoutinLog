package com.bapegg.routinlog.auth

import com.bapegg.routinlog.account.persistence.UserAccountEntity
import com.bapegg.routinlog.account.persistence.UserAccountRepository
import com.bapegg.routinlog.profile.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.doThrow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountDeletionTest @Autowired constructor(
    private val mvc: MockMvc,
    private val mapper: ObjectMapper,
    private val jdbc: JdbcTemplate,
    private val accounts: UserAccountRepository,
    private val sessions: AuthSessionService,
) {
    @MockitoBean lateinit var verifier: GoogleTokenVerifier
    private lateinit var subject: String
    private lateinit var source: String
    private val today get() = LocalDate.now(ZoneId.of("Asia/Seoul"))

    @BeforeEach
    fun setup() {
        subject = "delete-test-subject-${UUID.randomUUID()}"
        source = "198.18.0.${sequence.incrementAndGet()}"
        doReturn(VerifiedGoogleIdentity(subject)).`when`(verifier).verify(anyString(), anyString())
    }

    @Test
    fun `fresh same-account proof deletes all owned records identities and sessions`() {
        val account = login()
        val secondSession = login()
        val untouched = accounts.saveAndFlush(UserAccountEntity())
        val sessionIds = jdbc.queryForList("select id from auth_sessions where user_id = ?", UUID::class.java, account.userId)
        val profile = ProfileDto(
            age = 32, sex = ProfileSex.MALE, heightCm = BigDecimal("178.00"), initialWeightKg = BigDecimal("83.200"),
            goal = ProfileGoal.MAINTAIN, activityLevel = ActivityLevel.LIGHT, exerciseDays = listOf(1, 4), exerciseMinutes = 40,
            experience = ExerciseExperience.BEGINNER, units = DisplayUnits.METRIC, nutritionMode = NutritionMode.NONE,
            termsVersion = CURRENT_POLICY_VERSION, privacyVersion = CURRENT_POLICY_VERSION, healthConsentVersion = CURRENT_POLICY_VERSION,
            timeZone = "Asia/Seoul", effectiveFrom = today, weeklyFrequency = 2,
        )
        mvc.perform(put("/api/v1/me/profile").header("Authorization", "Bearer ${account.access}")
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(profile))).andExpect(status().isOk)
        mvc.perform(put("/api/v1/body-measurements/$today").header("Authorization", "Bearer ${account.access}")
            .contentType(MediaType.APPLICATION_JSON).content("""{"weightKg":83.2,"waistCm":80}""")).andExpect(status().isOk)
        assertEquals(1L, count("user_profile_revisions", account.userId))
        assertEquals(1L, count("body_measurements", account.userId))

        erase(account, challenge()).andExpect(status().isNoContent)
            .andExpect(header().string("Cache-Control", "no-store")).andExpect(header().doesNotExist("Set-Cookie"))
        assertTrue(!accounts.existsById(account.userId))
        assertTrue(accounts.existsById(untouched.id))
        listOf("external_identities", "auth_sessions", "body_measurements", "user_profile_revisions").forEach { table ->
            assertEquals(0L, count(table, account.userId), table)
        }
        sessionIds.forEach { id -> assertEquals(0L, jdbc.queryForObject("select count(*) from auth_refresh_tokens where session_id = ?", Long::class.java, id)) }
        assertNull(sessions.authenticate(account.access))
        assertNull(sessions.authenticate(secondSession.access))
        assertNull(sessions.refresh(account.refresh))
        assertNull(sessions.refresh(secondSession.refresh))
    }

    @Test
    fun `access token alone is insufficient and invalid Google proof cannot delete data`() {
        val account = login()
        mvc.perform(delete("/api/v1/me").with(address()).header("Authorization", "Bearer ${account.access}"))
            .andExpect(status().isBadRequest)
        mvc.perform(delete("/api/v1/me").with(address()).contentType(MediaType.APPLICATION_JSON).content(credentials(challenge())))
            .andExpect(status().isUnauthorized)
        doThrow(AuthFailure()).`when`(verifier).verify(anyString(), anyString())
        erase(account, challenge()).andExpect(status().isForbidden).andExpect(jsonPath("$.code").value("AUTH_REAUTH_REQUIRED"))
        assertTrue(accounts.existsById(account.userId))
        assertNotNull(sessions.authenticate(account.access))
    }

    @Test
    fun `verified proof from another Google account cannot delete the signed-in account`() {
        val account = login()
        doReturn(VerifiedGoogleIdentity("another-google-subject-${UUID.randomUUID()}")).`when`(verifier).verify(anyString(), anyString())
        erase(account, challenge()).andExpect(status().isForbidden).andExpect(jsonPath("$.code").value("AUTH_ACCOUNT_MISMATCH"))
        assertTrue(accounts.existsById(account.userId))
        assertNotNull(sessions.authenticate(account.access))
    }

    @Test
    fun `a challenge consumed by sign-in cannot authorize account deletion`() {
        val account = login()
        erase(account, account.usedChallenge).andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("AUTH_CHALLENGE_INVALID"))
        assertTrue(accounts.existsById(account.userId))
    }

    private fun login(): Login {
        val challenge = challenge()
        val response = mvc.perform(post("/api/v1/auth/google").with(address()).contentType(MediaType.APPLICATION_JSON).content(credentials(challenge)))
            .andExpect(status().isOk).andReturn().response.contentAsString
        val json = mapper.readTree(response)
        return Login(json["accessToken"].stringValue(), json["refreshToken"].stringValue(), UUID.fromString(json["userId"].stringValue()), challenge)
    }

    private fun challenge(): String {
        val response = mvc.perform(post("/api/v1/auth/google/challenge").with(address())).andExpect(status().isOk).andReturn().response.contentAsString
        return mapper.readTree(response)["challengeId"].stringValue()
    }
    private fun credentials(challenge: String) = mapper.writeValueAsString(mapOf("idToken" to "test-verifier-input", "challengeId" to challenge))
    private fun erase(account: Login, challenge: String) = mvc.perform(delete("/api/v1/me").with(address())
        .header("Authorization", "Bearer ${account.access}").contentType(MediaType.APPLICATION_JSON).content(credentials(challenge)))
    private fun address() = RequestPostProcessor { it.apply { remoteAddr = source } }
    private fun count(table: String, userId: UUID) = jdbc.queryForObject("select count(*) from $table where user_id = ?", Long::class.java, userId)
    private class Login(val access: String, val refresh: String, val userId: UUID, val usedChallenge: String)
    private companion object { val sequence = AtomicInteger() }
}
