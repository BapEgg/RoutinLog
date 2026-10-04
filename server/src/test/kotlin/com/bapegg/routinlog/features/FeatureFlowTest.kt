package com.bapegg.routinlog.features

import com.bapegg.routinlog.account.persistence.*
import com.bapegg.routinlog.profile.*
import com.bapegg.routinlog.workout.*
import com.bapegg.routinlog.cardio.*
import com.bapegg.routinlog.security.AuthenticatedUser
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.test.*

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class FeatureFlowTest @Autowired constructor(private val features:FeatureService,private val accounts:UserAccountRepository,
    private val profiles:ProfileService,private val workouts:WorkoutService,private val cardio:CardioService,
    private val analysis:WeeklyAnalysisService,private val mvc:MockMvc,private val photos:PrivateThumbnails,private val jdbc:org.springframework.jdbc.core.JdbcTemplate,private val entities:jakarta.persistence.EntityManager) {
    private val today get()=LocalDate.now(ZoneId.of("Asia/Seoul"))
    private fun user()=accounts.saveAndFlush(UserAccountEntity()).id.also { profiles.save(it,ProfileDto(
        age=32,sex=ProfileSex.MALE,heightCm=BigDecimal("178"),initialWeightKg=BigDecimal("80"),goal=ProfileGoal.MAINTAIN,
        activityLevel=ActivityLevel.LIGHT,exerciseDays=listOf(1,4),exerciseMinutes=40,experience=ExerciseExperience.BEGINNER,
        units=DisplayUnits.METRIC,nutritionMode=NutritionMode.MANUAL,dailyCalories=2400,carbohydrateG=BigDecimal("280"),proteinG=BigDecimal("170"),fatG=BigDecimal("66.67"),
        termsVersion=CURRENT_POLICY_VERSION,privacyVersion=CURRENT_POLICY_VERSION,healthConsentVersion=CURRENT_POLICY_VERSION,timeZone="Asia/Seoul",effectiveFrom=today)) }
    @Test fun `apply creates complete weekly plan once and preserves own exercise edits`() {
        val owner=user();val imported=workouts.importCatalog(owner,"goblet-squat")
        workouts.putExercise(owner,UUID.fromString(imported.id),ExerciseWrite("내 스쿼트","덤벨","하체",RecordType.REPS,LoadConvention.BODYWEIGHT,imported.version))
        val request=ProgramApply(UUID.randomUUID(),"full-body-2",listOf(1,4))
        val result=features.apply(owner,request)
        assertEquals(result,features.apply(owner,request))
        assertEquals(2,workouts.routines(owner).items.size)
        assertEquals(listOf(1,4),result.plan.slots.filter { it.routineId!=null }.map { it.dayOfWeek })
        assertEquals("내 스쿼트",workouts.exercises(owner).items.find { it.id==imported.id }!!.name)
        assertTrue(workouts.routines(owner).items.flatMap { it.entries }.flatMap { it.sets }.all { it.weightKg==null })
    }
    @Test fun `stale plan and invalid days cannot create routines`() {
        val owner=user()
        assertFailsWith<FeatureException>{features.apply(owner,ProgramApply(UUID.randomUUID(),"full-body-2",listOf(1,1)))}
        assertTrue(workouts.routines(owner).items.isEmpty())
        val request=ProgramApply(UUID.randomUUID(),"full-body-2",listOf(1,4));features.apply(owner,request)
        assertFailsWith<FeatureException>{features.apply(owner,request.copy(requestId=UUID.randomUUID()))}
        assertEquals(2,workouts.routines(owner).items.size)
    }
    @Test fun `preparation version and account separation`() {
        val a=user();val b=user()
        val saved=features.preparation(a,PreparationDto(listOf(PreparationItem(UUID.randomUUID(),"발목 가동",30))))
        assertEquals(0,saved.version)
        assertTrue(features.preparation(b).items.isEmpty())
        assertFailsWith<FeatureException>{features.preparation(a,PreparationDto())}
        assertTrue(features.preparation(a,PreparationDto(emptyList(),saved.version)).items.isEmpty())
    }
    @Test fun `export excludes identity secrets and other account data over HTTP`() {
        val a=user();val b=user()
        features.preparation(a,PreparationDto(listOf(PreparationItem(UUID.randomUUID(),"MY_PREPARATION"))))
        features.preparation(b,PreparationDto(listOf(PreparationItem(UUID.randomUUID(),"OTHER_PRIVATE"))))
        mvc.perform(get("/api/v1/features/export")).andExpect(status().isUnauthorized)
        val result=mvc.perform(get("/api/v1/features/export").with(authentication(UsernamePasswordAuthenticationToken(AuthenticatedUser(a),null,emptyList()))))
            .andExpect(status().isOk).andExpect(header().string("Cache-Control","no-store")).andReturn().response.contentAsString
        assertTrue(result.contains("MY_PREPARATION"));assertFalse(result.contains("OTHER_PRIVATE"));assertFalse(result.contains("auth_sessions"));assertFalse(result.contains("provider_subject"))
    }
    @Test fun `analysis requires explicit AI consent and missing data stays unknown`() {
        val owner=user();val week=today.with(java.time.DayOfWeek.MONDAY)
        val report=analysis.analyze(owner,AnalysisWrite(week))
        assertEquals(4,report.trends.size);assertTrue(report.trends.all { it.kcal==null&&it.weightKg==null })
        assertEquals("RULES",report.mode)
        assertFailsWith<FeatureException>{analysis.analyze(owner,AnalysisWrite(week,true))}
        assertEquals("RULES",analysis.analyze(owner,AnalysisWrite(week,true,"ai-summary-v1")).mode)
    }
    @Test fun `MET estimate is reproducible and does not masquerade as device reading`() {
        val owner=user();val value=cardio.save(owner,UUID.randomUUID(),CardioWrite(today,CardioValues("걷기",30,metCode="17355")))
        assertEquals(BigDecimal("152"),value.estimate!!.totalKcal)
        assertEquals(BigDecimal("112"),value.estimate!!.activeKcal)
        assertNull(value.values.deviceKcal)
        assertFailsWith<CardioException>{cardio.save(owner,UUID.randomUUID(),CardioWrite(today,CardioValues("걷기",30,inclinePercent=BigDecimal("5"),metCode="17355")))}
    }
    @Test fun `all program exercise definitions exist and quantities are valid`() {
        ProgramCatalog.programs.flatMap { it.sessions }.flatMap { it.moves }.forEach { move->
            assertTrue(ExerciseCatalog.items.any { it.key==move.key });assertTrue(move.sets>0);assertTrue(move.reps>0)
        }
    }
    @Test fun `photo is private sanitized bounded and cascades when exercise is deleted`() {
        val a=user();val b=user();val exercise=workouts.importCatalog(a,"push-up");val id=UUID.fromString(exercise.id)
        val image=java.awt.image.BufferedImage(512,300,java.awt.image.BufferedImage.TYPE_INT_RGB)
        val bytes=java.io.ByteArrayOutputStream().use { javax.imageio.ImageIO.write(image,"jpeg",it);it.toByteArray() }
        val photo=photos.put(a,"exercise",id,ThumbnailDto(java.util.Base64.getEncoder().encodeToString(bytes)))
        val decoded=javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(java.util.Base64.getDecoder().decode(photo.jpegBase64)))
        assertEquals(256,decoded.width)
        assertFailsWith<FeatureException>{photos.get(b,"exercise",id)}
        assertFailsWith<FeatureException>{photos.put(a,"exercise",id,ThumbnailDto("not a jpeg",photo.version))}
        workouts.deleteExercise(a,id,exercise.version)
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM exercise_thumbnails WHERE user_id=?",Int::class.java,a))
    }
    @Test fun `account deletion also removes program applications and preparation`() {
        val a=user();features.apply(a,ProgramApply(UUID.randomUUID(),"full-body-2",listOf(1,4)))
        features.preparation(a,PreparationDto(listOf(PreparationItem(UUID.randomUUID(),"warmup"))))
        entities.flush();entities.clear();accounts.deleteById(a);accounts.flush()
        listOf("program_applications","workout_preparation").forEach { assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM $it WHERE user_id=?",Int::class.java,a)) }
    }
}
