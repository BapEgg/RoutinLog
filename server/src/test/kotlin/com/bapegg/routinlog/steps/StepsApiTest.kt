package com.bapegg.routinlog.steps
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
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.test.*
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class StepsApiTest @Autowired constructor(private val mvc:MockMvc,private val mapper:ObjectMapper,
    private val accounts:UserAccountRepository,private val profiles:ProfileService,private val steps:StepsService,private val jdbc:JdbcTemplate) {
    private val today get()=LocalDate.now(ZoneId.of("Asia/Seoul"))
    private fun id()=UUID.randomUUID().toString()
    private fun auth(user:UUID)=authentication(UsernamePasswordAuthenticationToken(AuthenticatedUser(user),null,emptyList()))
    private fun user(consent:Boolean=true)=accounts.saveAndFlush(UserAccountEntity()).id.also { if(consent) profiles.save(it,ProfileDto(
        age=32,sex=ProfileSex.MALE,heightCm=BigDecimal("178"),initialWeightKg=BigDecimal("83.2"),goal=ProfileGoal.MAINTAIN,
        activityLevel=ActivityLevel.LIGHT,exerciseDays=listOf(1,4),exerciseMinutes=40,experience=ExerciseExperience.BEGINNER,
        units=DisplayUnits.METRIC,nutritionMode=NutritionMode.MANUAL,dailyCalories=2400,carbohydrateG=BigDecimal("280"),proteinG=BigDecimal("170"),fatG=BigDecimal("66.67"),
        termsVersion=CURRENT_POLICY_VERSION,privacyVersion=CURRENT_POLICY_VERSION,healthConsentVersion=CURRENT_POLICY_VERSION,timeZone="Asia/Seoul",effectiveFrom=today,
    )) }

    private fun pastConnection(u:UUID):StepConnection {
        val c=requireNotNull(steps.connect(u,UUID.randomUUID()).active)
        val yesterday=today.minusDays(1).atStartOfDay(ZoneId.of(c.timeZone)).toInstant()
        jdbc.update("UPDATE step_connections SET started_at=? WHERE id=?",yesterday.atOffset(ZoneOffset.UTC),c.id)
        return c.copy(startedAt=yesterday)
    }
    private fun observation(c:StepConnection,count:Long=3000)=StepObservation(today.minusDays(1),c.startedAt,today.atStartOfDay(ZoneId.of(c.timeZone)).toInstant(),count)

    @Test fun `authentication and health consent are required`() {
        mvc.perform(get("/api/v1/steps")).andExpect(status().isUnauthorized)
        mvc.perform(put("/api/v1/step-connection").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized)
        val u=user(false)
        assertNull(steps.connection(u).active)
        assertEquals(403,assertFailsWith<StepException>{steps.connect(u,UUID.randomUUID())}.status.value())
    }
    @Test fun `same daily snapshot and retried request never double count`() {
        val u=user();val c=pastConnection(u);val first=observation(c)
        repeat(2){steps.save(u,c.id,StepBatch(listOf(first)))}
        val row=steps.days(u,today.minusDays(7),today).items.single()
        assertEquals(3000,row.steps);assertEquals(1,row.segments)
        assertEquals(409,assertFailsWith<StepException>{steps.save(u,c.id,StepBatch(listOf(first.copy(steps=4500))))}.status.value())
    }
    @Test fun `newer snapshots replace older totals and delayed uploads cannot regress`() {
        val u=user();val c=pastConnection(u);val end=observation(c)
        val earlier=end.copy(through=end.through.minusSeconds(3600),steps=2000)
        steps.save(u,c.id,StepBatch(listOf(earlier)))
        steps.save(u,c.id,StepBatch(listOf(end)))
        steps.save(u,c.id,StepBatch(listOf(earlier)))
        assertEquals(3000,steps.days(u,today.minusDays(1),today).items.single().steps)
    }
    @Test fun `zero observation differs from a missing day`() {
        val u=user();val c=pastConnection(u)
        assertTrue(steps.days(u,today.minusDays(1),today).items.isEmpty())
        steps.save(u,c.id,StepBatch(listOf(observation(c,0))))
        assertEquals(0,steps.days(u,today.minusDays(1),today).items.single().steps)
        assertTrue(steps.days(u,today,today).items.isEmpty())
    }
    @Test fun `different owner cannot read upload or disconnect another account`() {
        val a=user();val b=user();val c=pastConnection(a)
        steps.save(a,c.id,StepBatch(listOf(observation(c))))
        assertTrue(steps.days(b,today.minusDays(1),today).items.isEmpty())
        assertFailsWith<StepException>{steps.save(b,c.id,StepBatch(listOf(observation(c))))}
        steps.disconnect(b,c.id)
        assertEquals(c.id,steps.connection(a).active?.id)
        assertFailsWith<StepException>{steps.connect(b,c.id)}
    }
    @Test fun `reconnecting closes old device and keeps previously uploaded steps`() {
        val u=user();val c=pastConnection(u)
        steps.save(u,c.id,StepBatch(listOf(observation(c))))
        val next=requireNotNull(steps.connect(u,UUID.randomUUID()).active)
        assertEquals(next,steps.connect(u,next.id).active)
        assertFailsWith<StepException>{steps.save(u,c.id,StepBatch(listOf(observation(c))))}
        steps.disconnect(u,c.id)
        assertEquals(next.id,steps.connection(u).active?.id)
        assertEquals(3000,steps.days(u,today.minusDays(1),today).items.single().steps)
        steps.disconnect(u,next.id);steps.disconnect(u,next.id)
        assertNull(steps.connection(u).active)
    }
    @Test fun `out of boundary future and excessive input cannot become history`() {
        val u=user();val c=pastConnection(u);val value=observation(c)
        listOf(value.copy(steps=-1),value.copy(steps=300001),value.copy(from=value.from.minusSeconds(1)),value.copy(through=value.from),
            value.copy(through=value.through.plusSeconds(1)),value.copy(date=today.plusDays(1)),value.copy(date=LocalDate.MAX)).forEach {
            assertFailsWith<StepException>{steps.save(u,c.id,StepBatch(listOf(it)))}
        }
        assertFailsWith<StepException>{steps.save(u,c.id,StepBatch(listOf(value,value)))}
        assertTrue(steps.days(u,today.minusDays(1),today).items.isEmpty())
    }
    @Test fun `same day reconnection sums only disjoint collection segments`() {
        val u=user();val first=pastConnection(u)
        val noon=first.startedAt.plusSeconds(12*3600)
        steps.save(u,first.id,StepBatch(listOf(observation(first,2000).copy(through=noon))))
        val second=requireNotNull(steps.connect(u,UUID.randomUUID()).active)
        jdbc.update("UPDATE step_connections SET ended_at=? WHERE id=?",noon.atOffset(ZoneOffset.UTC),first.id)
        jdbc.update("UPDATE step_connections SET started_at=? WHERE id=?",noon.atOffset(ZoneOffset.UTC),second.id)
        val segment=StepObservation(today.minusDays(1),noon,today.atStartOfDay(ZoneId.of(second.timeZone)).toInstant(),3000)
        steps.save(u,second.id,StepBatch(listOf(segment)))
        val day=steps.days(u,today.minusDays(1),today).items.single()
        assertEquals(5000,day.steps);assertEquals(2,day.segments)
        assertFailsWith<StepException>{steps.save(u,second.id,StepBatch(listOf(segment.copy(from=first.startedAt))))}
    }
    @Test fun `account deletion cascades connections and observations`() {
        val u=user();val c=pastConnection(u);steps.save(u,c.id,StepBatch(listOf(observation(c))))
        accounts.flush();jdbc.update("DELETE FROM user_accounts WHERE id=?",u)
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM step_connections WHERE id=?",Long::class.java,c.id))
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM step_observations WHERE connection_id=?",Long::class.java,c.id))
    }
    @Test fun `HTTP uses no store and rejects malformed inputs`() {
        val u=user();val id=UUID.randomUUID()
        mvc.perform(put("/api/v1/step-connection").with(auth(u)).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(ConnectSteps(id))))
            .andExpect(status().isOk).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.active.id").value(id.toString()))
        mvc.perform(get("/api/v1/steps").with(auth(u)).param("from",today.toString()).param("to",today.toString()))
            .andExpect(status().isOk).andExpect(jsonPath("$.items").isEmpty)
        mvc.perform(put("/api/v1/step-connection").with(auth(u)).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest)
    }
}
