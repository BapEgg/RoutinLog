package com.bapegg.routinlog.condition
import com.bapegg.routinlog.account.persistence.*
import com.bapegg.routinlog.profile.*
import com.bapegg.routinlog.security.AuthenticatedUser
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.test.*
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class ConditionApiTest @Autowired constructor(private val mvc:MockMvc,private val mapper:ObjectMapper,
    private val accounts:UserAccountRepository,private val profiles:ProfileService,private val conditions:ConditionService,private val jdbc:JdbcTemplate) {
    private val today get()=LocalDate.now(ZoneId.of("Asia/Seoul"))
    private fun id()=UUID.randomUUID().toString()
    private fun auth(user:UUID)=authentication(UsernamePasswordAuthenticationToken(AuthenticatedUser(user),null,emptyList()))
    private fun user(consent:Boolean=true)=accounts.saveAndFlush(UserAccountEntity()).id.also { if(consent) profiles.save(it,ProfileDto(
        age=32,sex=ProfileSex.MALE,heightCm=BigDecimal("178"),initialWeightKg=BigDecimal("83.2"),goal=ProfileGoal.MAINTAIN,
        activityLevel=ActivityLevel.LIGHT,exerciseDays=listOf(1,4),exerciseMinutes=40,experience=ExerciseExperience.BEGINNER,
        units=DisplayUnits.METRIC,nutritionMode=NutritionMode.MANUAL,dailyCalories=2400,carbohydrateG=BigDecimal("280"),proteinG=BigDecimal("170"),fatG=BigDecimal("66.67"),
        termsVersion=CURRENT_POLICY_VERSION,privacyVersion=CURRENT_POLICY_VERSION,healthConsentVersion=CURRENT_POLICY_VERSION,timeZone="Asia/Seoul",effectiveFrom=today,
    )) }

    @Test fun `authentication and consent gate all writes`() {
        mvc.perform(get("/api/v1/conditions").param("from",today.toString()).param("to",today.toString())).andExpect(status().isUnauthorized)
        mvc.perform(put("/api/v1/conditions/$today").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized)
        mvc.perform(delete("/api/v1/conditions/$today").param("version","0")).andExpect(status().isUnauthorized)
        val u=user(false)
        assertTrue(conditions.list(u,today,today).items.isEmpty())
        assertEquals(403,assertFailsWith<ConditionException>{conditions.save(u,today,ConditionWrite(ConditionValues(memo="test")))}.status.value())
    }
    @Test fun `zero sleep and no soreness differ from unrecorded fields`() {
        val u=user();val saved=conditions.save(u,today,ConditionWrite(ConditionValues(sleepMinutes=0,soreness="NONE")))
        assertEquals(0,saved.values.sleepMinutes);assertNull(saved.values.fatigue);assertNull(saved.values.activity)
        mvc.perform(get("/api/v1/conditions").with(auth(u)).param("from",today.toString()).param("to",today.toString()))
            .andExpect(status().isOk).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(jsonPath("$.items[0].values.sleepMinutes").value(0)).andExpect(jsonPath("$.items[0].values.fatigue").isEmpty)
    }
    @Test fun `only the owning account can see update and delete its date`() {
        val a=user();val b=user();conditions.save(a,today,ConditionWrite(ConditionValues(memo="private")))
        assertTrue(conditions.list(b,today,today).items.isEmpty())
        assertEquals(404,assertFailsWith<ConditionException>{conditions.delete(b,today,0)}.status.value())
        conditions.save(b,today,ConditionWrite(ConditionValues(memo="other")))
        assertEquals("private",conditions.list(a,today,today).items.single().values.memo)
    }
    @Test fun `stale and duplicate writes cannot overwrite confirmed observations`() {
        val u=user();val original=conditions.save(u,today,ConditionWrite(ConditionValues(fatigue="HIGH")))
        assertEquals(0,original.version)
        assertEquals(409,assertFailsWith<ConditionException>{conditions.save(u,today,ConditionWrite(ConditionValues(fatigue="LOW")))}.status.value())
        val saved=conditions.save(u,today,ConditionWrite(ConditionValues(sleepMinutes=425,memo="  after shift  "),0))
        assertEquals(1,saved.version);assertNull(saved.values.fatigue);assertEquals("after shift",saved.values.memo)
        assertEquals(409,assertFailsWith<ConditionException>{conditions.delete(u,today,0)}.status.value())
        conditions.delete(u,today,1);assertTrue(conditions.list(u,today,today).items.isEmpty())
    }
    @Test fun `invalid ranges enums and empty observations are rejected`() {
        val u=user()
        listOf(ConditionValues(),ConditionValues(memo="  "),ConditionValues(sleepMinutes=-1),ConditionValues(sleepMinutes=1441),
            ConditionValues(fatigue="UNKNOWN"),ConditionValues(soreness="NONE",sorenessArea="leg"),ConditionValues(memo="x".repeat(1001)),
            ConditionValues(memo="x\u0000y"),ConditionValues(activity="FAST")).forEach { values->
            assertEquals(400,assertFailsWith<ConditionException>{conditions.save(u,today,ConditionWrite(values))}.status.value())
        }
        assertTrue(conditions.list(u,today,today).items.isEmpty())
        conditions.save(u,today,ConditionWrite(ConditionValues(sleepMinutes=1440)))
    }
    @Test fun `dates are bounded and history returns only the requested period`() {
        val u=user();val yesterday=today.minusDays(1)
        conditions.save(u,yesterday,ConditionWrite(ConditionValues(stress="LOW")))
        conditions.save(u,today,ConditionWrite(ConditionValues(activity="HIGH")))
        assertEquals(listOf(today,yesterday),conditions.list(u,yesterday,today).items.map { it.date })
        assertEquals(1,conditions.list(u,today,today).items.size)
        assertFailsWith<ConditionException>{conditions.save(u,today.plusDays(1),ConditionWrite(ConditionValues(memo="future")))}
        assertFailsWith<ConditionException>{conditions.list(u,today,today.minusDays(1))}
        assertFailsWith<ConditionException>{conditions.list(u,today.minusDays(366),today)}
        assertFailsWith<ConditionException>{conditions.list(u,LocalDate.of(1899,1,1),today)}
    }
    @Test fun `account deletion cascades self reported health data`() {
        val u=user();conditions.save(u,today,ConditionWrite(ConditionValues(memo="private",soreness="MILD",sorenessArea="leg")))
        accounts.flush();jdbc.update("DELETE FROM user_accounts WHERE id=?",u)
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM daily_conditions WHERE user_id=?",Long::class.java,u))
    }
    @Test fun `HTTP rejects malformed inputs and returns versioned saved values`() {
        val u=user()
        mvc.perform(put("/api/v1/conditions/$today").with(auth(u)).contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(ConditionWrite(ConditionValues(sleepMinutes=390,fatigue="MODERATE")))))
            .andExpect(status().isOk).andExpect(jsonPath("$.version").value(0)).andExpect(jsonPath("$.values.sleepMinutes").value(390))
        mvc.perform(put("/api/v1/conditions/$today").with(auth(u)).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest)
        mvc.perform(get("/api/v1/conditions").with(auth(u)).param("from","bad").param("to",today.toString())).andExpect(status().isBadRequest)
    }
}
