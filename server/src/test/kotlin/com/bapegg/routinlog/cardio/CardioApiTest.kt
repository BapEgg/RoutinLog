package com.bapegg.routinlog.cardio
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
class CardioApiTest @Autowired constructor(private val mvc:MockMvc,private val mapper:ObjectMapper,
    private val accounts:UserAccountRepository,private val profiles:ProfileService,private val cardio:CardioService,private val reports:com.bapegg.routinlog.report.WeeklyReportService,private val calendar:com.bapegg.routinlog.report.RecordCalendarService,private val jdbc:JdbcTemplate) {
    private val today get()=LocalDate.now(ZoneId.of("Asia/Seoul"))
    private fun id()=UUID.randomUUID().toString()
    private fun auth(user:UUID)=authentication(UsernamePasswordAuthenticationToken(AuthenticatedUser(user),null,emptyList()))
    private fun user(consent:Boolean=true)=accounts.saveAndFlush(UserAccountEntity()).id.also { if(consent) profiles.save(it,ProfileDto(
        age=32,sex=ProfileSex.MALE,heightCm=BigDecimal("178"),initialWeightKg=BigDecimal("83.2"),goal=ProfileGoal.MAINTAIN,
        activityLevel=ActivityLevel.LIGHT,exerciseDays=listOf(1,4),exerciseMinutes=40,experience=ExerciseExperience.BEGINNER,
        units=DisplayUnits.METRIC,nutritionMode=NutritionMode.MANUAL,dailyCalories=2400,carbohydrateG=BigDecimal("280"),proteinG=BigDecimal("170"),fatG=BigDecimal("66.67"),
        termsVersion=CURRENT_POLICY_VERSION,privacyVersion=CURRENT_POLICY_VERSION,healthConsentVersion=CURRENT_POLICY_VERSION,timeZone="Asia/Seoul",effectiveFrom=today,
    )) }

    private fun record(minutes:Int=30)=CardioWrite(today,CardioValues("걷기",minutes))
    @Test fun `auth consent and malformed requests are gated`() {
        mvc.perform(get("/api/v1/cardio").param("from",today.toString()).param("to",today.toString())).andExpect(status().isUnauthorized)
        mvc.perform(put("/api/v1/cardio/${id()}").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized)
        mvc.perform(delete("/api/v1/cardio/${id()}").param("version","0")).andExpect(status().isUnauthorized)
        val u=user(false)
        assertEquals(403,assertFailsWith<CardioException>{cardio.save(u,UUID.randomUUID(),record())}.status.value())
        mvc.perform(put("/api/v1/cardio/bad").with(auth(u)).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest)
    }
    @Test fun `owner isolation zero calories and missing calories survive HTTP round trip`() {
        val a=user();val b=user();val id=UUID.randomUUID()
        val write=record().copy(values=CardioValues("실내 자전거",30,BigDecimal.ZERO,CardioEnergyKind.UNKNOWN,"bike",effort=7,memo="  harder today  "))
        mvc.perform(put("/api/v1/cardio/$id").with(auth(a)).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(write)))
            .andExpect(status().isOk).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(jsonPath("$.values.deviceKcal").value(0)).andExpect(jsonPath("$.values.memo").value("harder today"))
        cardio.save(a,UUID.randomUUID(),record(20))
        assertEquals(2,cardio.list(a,today,today).items.size)
        assertTrue(cardio.list(b,today,today).items.isEmpty())
        assertEquals(404,assertFailsWith<CardioException>{cardio.delete(b,id,0)}.status.value())
        assertEquals(409,assertFailsWith<CardioException>{cardio.save(b,id,write.copy(version=0))}.status.value())
        assertEquals(1,cardio.list(a,today,today).items.count { it.values.deviceKcal==null })
    }
    @Test fun `response loss retry is idempotent but stale changes conflict`() {
        val u=user();val id=UUID.randomUUID();val original=cardio.save(u,id,record())
        assertEquals(original,cardio.save(u,id,record()))
        assertEquals(1,cardio.list(u,today,today).items.size)
        assertEquals(409,assertFailsWith<CardioException>{cardio.save(u,id,record(40))}.status.value())
        val changed=cardio.save(u,id,record(40).copy(version=0));assertEquals(1,changed.version)
        assertEquals(changed,cardio.save(u,id,record(40).copy(version=0)))
        assertEquals(409,assertFailsWith<CardioException>{cardio.delete(u,id,0)}.status.value())
        cardio.delete(u,id,1);assertTrue(cardio.list(u,today,today).items.isEmpty())
    }
    @Test fun `range and device provenance validation never persists invalid records`() {
        val u=user();val good=record().values
        listOf(good.copy(minutes=0),good.copy(minutes=1441),good.copy(activity="  "),good.copy(deviceKcal=BigDecimal.TEN),
            good.copy(deviceName="watch"),good.copy(energyKind=CardioEnergyKind.ACTIVE),good.copy(effort=11),good.copy(fatigue="X"),
            good.copy(speedKmh=BigDecimal("-1")),good.copy(inclinePercent=BigDecimal("101")),good.copy(distanceKm=BigDecimal("0.0001")),
            good.copy(memo="x".repeat(1001)),good.copy(activity="x\u0000y")).forEach { v->
            assertEquals(400,assertFailsWith<CardioException>{cardio.save(u,UUID.randomUUID(),CardioWrite(today,v))}.status.value())
        }
        assertFailsWith<CardioException>{cardio.save(u,UUID.randomUUID(),record().copy(date=today.plusDays(1)))}
        assertFailsWith<CardioException>{cardio.list(u,today.minusDays(31),today)}
        assertFailsWith<CardioException>{cardio.list(u,today,today.minusDays(1))}
        assertTrue(cardio.list(u,today,today).items.isEmpty())
    }
    @Test fun `daily duration and record count limits account for edits`() {
        val u=user();val id=UUID.randomUUID();cardio.save(u,id,record(1400))
        assertFailsWith<CardioException>{cardio.save(u,UUID.randomUUID(),record(41))}
        cardio.save(u,id,record(30).copy(version=0))
        repeat(23){cardio.save(u,UUID.randomUUID(),record(1))}
        assertFailsWith<CardioException>{cardio.save(u,UUID.randomUUID(),record(1))}
        assertEquals(24,cardio.list(u,today,today).items.size)
    }
    @Test fun `weekly report and calendar use actual records without changing nutrition or duplicating steps`() {
        val u=user();val week=today.with(java.time.DayOfWeek.MONDAY)
        val before=reports.get(u,week);val id=UUID.randomUUID()
        cardio.save(u,id,CardioWrite(week,CardioValues("러닝",25,BigDecimal("200"),CardioEnergyKind.TOTAL,"watch")))
        cardio.save(u,UUID.randomUUID(),CardioWrite(week,CardioValues("걷기",15)))
        val report=reports.get(u,week)
        assertEquals(before.nutrition,report.nutrition);assertEquals(before.steps,report.steps)
        assertEquals(40,report.cardio.sumOf { it.values.minutes });assertTrue(report.workouts.all { it.session==null })
        val day=calendar.get(u,week.withDayOfMonth(1)).days.single { it.date==week }
        assertEquals(2,day.cardioCount);assertEquals(40,day.cardioMinutes);assertFalse(day.workoutRecorded)
        assertTrue(reports.get(user(),week).cardio.isEmpty())
        cardio.delete(u,id,0)
        assertEquals(15,reports.get(u,week).cardio.sumOf { it.values.minutes })
    }
    @Test fun `account removal cascades cardio records`() {
        val u=user();cardio.save(u,UUID.randomUUID(),record());accounts.flush()
        jdbc.update("DELETE FROM user_accounts WHERE id=?",u)
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM cardio_records WHERE user_id=?",Long::class.java,u))
    }
}
