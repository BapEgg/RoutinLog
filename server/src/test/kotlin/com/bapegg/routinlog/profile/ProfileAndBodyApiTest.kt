package com.bapegg.routinlog.profile

import com.bapegg.routinlog.account.persistence.UserAccountEntity
import com.bapegg.routinlog.account.persistence.UserAccountRepository
import com.bapegg.routinlog.profile.persistence.UserProfileRepository
import com.bapegg.routinlog.security.AuthenticatedUser
import jakarta.persistence.EntityManager
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.annotation.Propagation
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** H2 HTTP/persistence contracts. A production PostgreSQL integration run remains separate. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProfileAndBodyApiTest @Autowired constructor(
    private val mvc: MockMvc,
    private val users: UserAccountRepository,
    private val profiles: UserProfileRepository,
    private val entities: EntityManager,
    private val mapper: ObjectMapper,
) {
    private val today get() = LocalDate.now(ZoneId.of("Asia/Seoul"))
    private fun user() = users.saveAndFlush(UserAccountEntity()).id
    private fun auth(id: UUID) = authentication(UsernamePasswordAuthenticationToken(AuthenticatedUser(id), null, emptyList()))
    private fun dto(version: Long? = null) = ProfileDto(
        age = 32, sex = ProfileSex.MALE, heightCm = BigDecimal("178.00"), initialWeightKg = BigDecimal("83.200"),
        initialWaistCm = BigDecimal("80.00"), goal = ProfileGoal.GAIN, targetWeightKg = null,
        activityLevel = ActivityLevel.LIGHT, exerciseDays = listOf(1, 2, 4, 5), exerciseMinutes = 60,
        experience = ExerciseExperience.INTERMEDIATE, units = DisplayUnits.METRIC, nutritionMode = NutritionMode.MANUAL,
        dailyCalories = 2400, carbohydrateG = BigDecimal("280.00"), proteinG = BigDecimal("170.00"), fatG = BigDecimal("66.67"), fiberG = null,
        termsVersion = CURRENT_POLICY_VERSION, privacyVersion = CURRENT_POLICY_VERSION, healthConsentVersion = CURRENT_POLICY_VERSION,
        timeZone = "Asia/Seoul", effectiveFrom = today, version = version,
        recentExerciseDays = 3, recentExerciseMinutes = 45, recentExerciseType = RecentExerciseType.MIXED,
        recentExerciseIntensity = RecentExerciseIntensity.MODERATE, weeklyFrequency = 4, targetFormulaVersion = "prototype-start-0.1",
    )

    @Test fun `record and profile endpoints require verified authentication`() {
        mvc.perform(get("/api/v1/me/profile")).andExpect(status().isUnauthorized)
        mvc.perform(put("/api/v1/me/profile").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(dto()))).andExpect(status().isUnauthorized)
        mvc.perform(get("/api/v1/body-measurements").param("from", today.toString()).param("to", today.toString())).andExpect(status().isUnauthorized)
        mvc.perform(put("/api/v1/body-measurements/$today").contentType(MediaType.APPLICATION_JSON).content("""{"weightKg":83.2}""")).andExpect(status().isUnauthorized)
        mvc.perform(delete("/api/v1/body-measurements/$today").param("version", "0")).andExpect(status().isUnauthorized)
    }

    @Test fun `profile persists reloads and retains immutable goal nutrition snapshots`() {
        val id = user()
        mvc.perform(get("/api/v1/me/profile").with(auth(id))).andExpect(status().isNotFound).andExpect(jsonPath("$.code").value("PROFILE_NOT_FOUND"))
        mvc.perform(put("/api/v1/me/profile").with(auth(id)).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(dto())))
            .andExpect(status().isOk).andExpect(jsonPath("$.version").value(0)).andExpect(jsonPath("$.dailyCalories").value(2400))
        entities.flush(); entities.clear()
        mvc.perform(get("/api/v1/me/profile").with(auth(id)))
            .andExpect(status().isOk).andExpect(header().string("Cache-Control", containsString("no-store")))
            .andExpect(jsonPath("$.heightCm").value(178)).andExpect(jsonPath("$.fiberG").isEmpty)
            .andExpect(jsonPath("$.recentExerciseDays").value(3)).andExpect(jsonPath("$.weeklyFrequency").value(4))
        mvc.perform(put("/api/v1/me/profile").with(auth(id)).contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(dto(0).copy(goal = ProfileGoal.MAINTAIN, dailyCalories = 2300))))
            .andExpect(status().isOk).andExpect(jsonPath("$.version").value(1))
        entities.flush(); entities.clear()
        assertEquals(2L, profiles.countByUserId(id))
        val all = profiles.findAll().filter { it.user.id == id }.sortedBy { it.revision }
        assertEquals(ProfileGoal.GAIN, all[0].goal)
        assertEquals(2400, all[0].dailyCalories)
        assertEquals(ProfileGoal.MAINTAIN, all[1].goal)
        mvc.perform(get("/api/v1/me/profile").with(auth(id))).andExpect(jsonPath("$.goal").value("MAINTAIN"))
    }

    @Test fun `profile supports no target without turning unknown nutrients into zero`() {
        val id = user()
        val request = dto().copy(nutritionMode = NutritionMode.NONE, dailyCalories = null, carbohydrateG = null, proteinG = null, fatG = null, fiberG = null, targetFormulaVersion = null)
        mvc.perform(put("/api/v1/me/profile").with(auth(id)).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request)))
            .andExpect(status().isOk).andExpect(jsonPath("$.dailyCalories").isEmpty).andExpect(jsonPath("$.proteinG").isEmpty)
        entities.flush(); entities.clear()
        assertNull(profiles.findFirstByUserIdOrderByRevisionDesc(id)!!.proteinG)
        mvc.perform(put("/api/v1/me/profile").with(auth(id)).contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(request.copy(version = 0, proteinG = BigDecimal.ZERO))))
            .andExpect(status().isBadRequest).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
    }

    @Test fun `profile rejects stale versions invalid consents and invalid values without input echoes`() {
        val id = user()
        mvc.perform(put("/api/v1/me/profile").with(auth(id)).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(dto()))).andExpect(status().isOk)
        mvc.perform(put("/api/v1/me/profile").with(auth(id)).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(dto())))
            .andExpect(status().isConflict).andExpect(jsonPath("$.code").value("VERSION_CONFLICT"))
        listOf(
            dto(0).copy(age = 18), dto(0).copy(heightCm = BigDecimal("178.001")), dto(0).copy(exerciseDays = listOf(1, 1)),
            dto(0).copy(recentExerciseDays = 8), dto(0).copy(proteinG = BigDecimal("-1")), dto(0).copy(effectiveFrom = today.plusDays(1)),
            dto(0).copy(timeZone = "private-marker-invalid-time-zone"),
        ).forEach { request ->
            mvc.perform(put("/api/v1/me/profile").with(auth(id)).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest).andExpect(content().string(not(containsString("private-marker"))))
        }
        mvc.perform(put("/api/v1/me/profile").with(auth(id)).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(dto(0).copy(healthConsentVersion = "old"))))
            .andExpect(status().isBadRequest).andExpect(jsonPath("$.code").value("POLICY_VERSION_REQUIRED"))
    }

    @Test fun `body update delete and reload enforce optimistic versions and preserve nullable measurements`() {
        val id = user()
        mvc.perform(put("/api/v1/body-measurements/$today").with(auth(id)).contentType(MediaType.APPLICATION_JSON).content("""{"weightKg":83.2,"memo":"아침 측정"}"""))
            .andExpect(status().isOk).andExpect(jsonPath("$.version").value(0)).andExpect(jsonPath("$.waistCm").isEmpty)
        entities.flush(); entities.clear()
        mvc.perform(get("/api/v1/body-measurements").with(auth(id)).param("from", today.toString()).param("to", today.toString()))
            .andExpect(status().isOk).andExpect(jsonPath("$.items[0].weightKg").value(83.2)).andExpect(jsonPath("$.items[0].memo").value("아침 측정"))
        mvc.perform(put("/api/v1/body-measurements/$today").with(auth(id)).contentType(MediaType.APPLICATION_JSON).content("""{"waistCm":80.12}"""))
            .andExpect(status().isConflict)
        mvc.perform(put("/api/v1/body-measurements/$today").with(auth(id)).contentType(MediaType.APPLICATION_JSON).content("""{"waistCm":80.12,"version":0}"""))
            .andExpect(status().isOk).andExpect(jsonPath("$.version").value(1)).andExpect(jsonPath("$.weightKg").isEmpty)
        mvc.perform(delete("/api/v1/body-measurements/$today").with(auth(id)).param("version", "0")).andExpect(status().isConflict)
        mvc.perform(delete("/api/v1/body-measurements/$today").with(auth(id)).param("version", "1")).andExpect(status().isNoContent)
        mvc.perform(delete("/api/v1/body-measurements/$today").with(auth(id)).param("version", "1")).andExpect(status().isNotFound)
    }

    @Test fun `one user cannot read overwrite or delete another users records or profile`() {
        val a = user(); val b = user()
        mvc.perform(put("/api/v1/me/profile").with(auth(a)).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(dto()))).andExpect(status().isOk)
        mvc.perform(get("/api/v1/me/profile").with(auth(b))).andExpect(status().isNotFound)
        mvc.perform(put("/api/v1/body-measurements/$today").with(auth(a)).contentType(MediaType.APPLICATION_JSON).content("""{"weightKg":83.2}""")).andExpect(status().isOk)
        mvc.perform(get("/api/v1/body-measurements").with(auth(b)).param("from", today.toString()).param("to", today.toString())).andExpect(jsonPath("$.items").isEmpty)
        mvc.perform(put("/api/v1/body-measurements/$today").with(auth(b)).contentType(MediaType.APPLICATION_JSON).content("""{"weightKg":90,"version":0}""")).andExpect(status().isConflict)
        mvc.perform(delete("/api/v1/body-measurements/$today").with(auth(b)).param("version", "0")).andExpect(status().isNotFound)
        mvc.perform(put("/api/v1/body-measurements/$today").with(auth(b)).contentType(MediaType.APPLICATION_JSON).content("""{"weightKg":90}""")).andExpect(status().isOk)
        mvc.perform(get("/api/v1/body-measurements").with(auth(a)).param("from", today.toString()).param("to", today.toString())).andExpect(jsonPath("$.items[0].weightKg").value(83.2))
    }

    @Test fun `body range precision malformed numbers and excessive memo are rejected`() {
        val id = user()
        listOf("{}", """{"weightKg":0}""", """{"weightKg":1000.001}""", """{"weightKg":83.2001}""", """{"waistCm":80.001}""", """{"weightKg":"NaN"}""").forEach { body ->
            mvc.perform(put("/api/v1/body-measurements/$today").with(auth(id)).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest)
        }
        mvc.perform(put("/api/v1/body-measurements/${today.plusDays(1)}").with(auth(id)).contentType(MediaType.APPLICATION_JSON).content("""{"weightKg":83.2}""")).andExpect(status().isBadRequest)
        mvc.perform(put("/api/v1/body-measurements/1899-12-31").with(auth(id)).contentType(MediaType.APPLICATION_JSON).content("""{"weightKg":83.2}""")).andExpect(status().isBadRequest)
        mvc.perform(put("/api/v1/body-measurements/$today").with(auth(id)).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(mapOf("weightKg" to 83.2, "memo" to "private-marker".repeat(100)))))
            .andExpect(status().isBadRequest).andExpect(content().string(not(containsString("private-marker"))))
        mvc.perform(get("/api/v1/body-measurements").with(auth(id)).param("from", today.minusDays(365).toString()).param("to", today.toString())).andExpect(status().isOk)
        mvc.perform(get("/api/v1/body-measurements").with(auth(id)).param("from", today.minusDays(366).toString()).param("to", today.toString())).andExpect(status().isBadRequest)
        mvc.perform(get("/api/v1/body-measurements").with(auth(id)).param("from", "bad-date").param("to", today.toString())).andExpect(status().isBadRequest).andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `simultaneous creates for one user and date produce one record and one conflict`() {
        val id = user()
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val results = listOf("83.2", "84.1").map { value ->
                pool.submit<Int> {
                    ready.countDown()
                    check(start.await(10, TimeUnit.SECONDS))
                    mvc.perform(put("/api/v1/body-measurements/$today").with(auth(id)).contentType(MediaType.APPLICATION_JSON)
                        .content("""{"weightKg":$value}""")).andReturn().response.status
                }
            }
            check(ready.await(10, TimeUnit.SECONDS)); start.countDown()
            assertEquals(listOf(200, 409), results.map { it.get(20, TimeUnit.SECONDS) }.sorted())
            mvc.perform(get("/api/v1/body-measurements").with(auth(id)).param("from", today.toString()).param("to", today.toString()))
                .andExpect(jsonPath("$.items.length()").value(1))
        } finally {
            start.countDown(); pool.shutdownNow(); pool.awaitTermination(5, TimeUnit.SECONDS)
            users.deleteById(id)
        }
    }
}
