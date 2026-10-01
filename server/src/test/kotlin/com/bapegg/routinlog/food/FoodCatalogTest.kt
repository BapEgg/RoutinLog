package com.bapegg.routinlog.food

import com.bapegg.routinlog.account.persistence.*
import com.bapegg.routinlog.profile.*
import com.bapegg.routinlog.security.AuthenticatedUser
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
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
import java.nio.file.*
import java.time.LocalDate
import java.util.UUID
import kotlin.test.*

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class FoodCatalogTest @Autowired constructor(private val catalog:FoodCatalog,private val importer:FoodCatalogImport,
    private val meals:MealService,private val profiles:ProfileService,private val accounts:UserAccountRepository,
    private val jdbc:JdbcTemplate,private val json:ObjectMapper,private val mvc:MockMvc) {
    @TempDir lateinit var temp:Path
    private fun fixture(id:String="TEST-001")=CatalogFood(id,"테스트 닭가슴살","테스트 업체","가공식품","80g",BigDecimal("80.000"),"g",
        NutritionValues(BigDecimal("160.000"),fatG=BigDecimal.ZERO),"식품의약품안전처","2026-08-28")
    private fun load(vararg rows:CatalogFood) { val file=temp.resolve("catalog.jsonl");Files.writeString(file,rows.joinToString("\n"){json.writeValueAsString(it)});importer.importFile(file) }
    private fun user(consent:Boolean=true)=accounts.saveAndFlush(UserAccountEntity()).id.also { if(consent)profiles.save(it,ProfileDto(
        age=32,sex=ProfileSex.MALE,heightCm=BigDecimal("178"),initialWeightKg=BigDecimal("83.2"),goal=ProfileGoal.MAINTAIN,
        activityLevel=ActivityLevel.LIGHT,exerciseDays=listOf(1,4),exerciseMinutes=40,experience=ExerciseExperience.BEGINNER,
        units=DisplayUnits.METRIC,nutritionMode=NutritionMode.MANUAL,dailyCalories=2400,carbohydrateG=BigDecimal("280"),proteinG=BigDecimal("170"),fatG=BigDecimal("66.67"),
        termsVersion=CURRENT_POLICY_VERSION,privacyVersion=CURRENT_POLICY_VERSION,healthConsentVersion=CURRENT_POLICY_VERSION,timeZone="Asia/Seoul",effectiveFrom=LocalDate.now(),version=null)) }
    private fun auth(id:UUID)=authentication(UsernamePasswordAuthenticationToken(AuthenticatedUser(id),null,emptyList()))
    private fun save(id:UUID,code:String="TEST-001")=meals.saveCatalogFood(id,code,CatalogSave(catalog.get(code).revision,FoodPreparation.AS_SOLD))

    @Test fun `catalog requires authentication and saving requires health consent`() {
        load(fixture())
        mvc.perform(get("/api/v1/food-catalog").param("q","닭가슴살")).andExpect(status().isUnauthorized)
        val owner=user(false)
        mvc.perform(get("/api/v1/food-catalog").with(auth(owner)).param("q","닭가슴살"))
            .andExpect(status().isOk).andExpect(jsonPath("$.items[0].nutrition.fiberG").isEmpty).andExpect(header().string("Cache-Control","no-store"))
        mvc.perform(post("/api/v1/food-catalog/TEST-001/save").with(auth(owner)).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(CatalogSave(catalog.get("TEST-001").revision)))).andExpect(status().isForbidden)
    }
    @Test fun `multi word search pagination and literal wildcards work`() {
        load(*(1..25).map { fixture("TEST-${it.toString().padStart(3,'0')}") }.toTypedArray())
        assertEquals(20,catalog.search("테스트 닭가슴살",0).items.size)
        assertEquals(20,catalog.search("닭",0).items.size)
        assertTrue(catalog.search("테스트 업체",0).hasMore)
        assertEquals(5,catalog.search("닭가슴살",1).items.size)
        assertFalse(catalog.search("닭가슴살",1).hasMore)
        assertTrue(catalog.search("%_",0).items.isEmpty())
        assertFailsWith<MealApiException>{catalog.search(" ",0)}
        assertFailsWith<MealApiException>{catalog.search("닭가슴살",101)}
    }
    @Test fun `server copies trusted nutrition and repeated save is idempotent and private`() {
        load(fixture());val a=user();val b=user();val first=save(a)
        assertEquals(first,save(a));assertTrue(meals.foods(b).items.isEmpty());assertEquals("PUBLIC_DB",first.source)
        assertEquals(0,first.nutrition.kcal!!.compareTo(BigDecimal("160")));assertNull(first.nutrition.fiberG)
        assertNotEquals(first.id,save(b).id)
        assertEquals(0,meals.day(a,LocalDate.now()).items.size)
    }
    @Test fun `source updates do not rewrite personal foods or historical meal snapshots`() {
        load(fixture());val owner=user();val original=save(owner)
        val recorded=meals.putMeal(owner,UUID.randomUUID(),MealWrite(LocalDate.now(),UUID.randomUUID().toString(),"점심",MealStatus.EATEN,
            listOf(MealItemWrite(UUID.randomUUID().toString(),original.id,BigDecimal("120")))))
        load(fixture().copy(nutrition=NutritionValues(kcal=BigDecimal("200"))))
        assertFailsWith<MealApiException>{meals.saveCatalogFood(owner,"TEST-001",CatalogSave(catalog.get("TEST-001").revision.reversed()))}
        assertEquals(original,meals.foods(owner).items.single())
        val newer=save(owner);assertNotEquals(original.id,newer.id)
        val old=meals.day(owner,LocalDate.now()).items.single()
        assertEquals(recorded,old);assertEquals("PUBLIC_DB",old.items.single().source)
        assertEquals(0,old.totals.kcal.knownAmount.compareTo(BigDecimal("240")))
    }
    @Test fun `volume and excessive source precision cannot silently become gram values`() {
        load(fixture().copy(basisLabel="80ml",basisUnit="ml"),fixture("TEST-002").copy(nutrition=NutritionValues(kcal=BigDecimal("160.123"))))
        val owner=user();assertNotNull(catalog.get("TEST-001").importBlockReason)
        assertFailsWith<MealApiException>{save(owner)};assertFailsWith<MealApiException>{save(owner,"TEST-002")}
        assertTrue(meals.foods(owner).items.isEmpty())
    }
    @Test fun `editing a copied public food changes its provenance without modifying catalog`() {
        load(fixture());val owner=user();val saved=save(owner)
        val edited=meals.putFood(owner,UUID.fromString(saved.id),FoodWrite("내 제품",saved.brand,saved.basisGrams,NutritionValues(kcal=BigDecimal("170")),saved.preparation,saved.sourceNote,saved.version))
        assertEquals("PUBLIC_EDITED",edited.source);assertEquals("테스트 닭가슴살",catalog.get("TEST-001").name)
        assertEquals(edited,save(owner))
    }
    @Test fun `unchanged import preserves revision and rejects duplicate codes`() {
        load(fixture());val before=catalog.get("TEST-001");load(fixture());assertEquals(before,catalog.get("TEST-001"))
        assertFailsWith<IllegalArgumentException>{load(fixture(),fixture())}
    }
    @Test fun `account deletion leaves shared catalog and erases only personal copies`() {
        load(fixture());val owner=user();save(owner)
        jdbc.update("DELETE FROM user_accounts WHERE id=?",owner)
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM foods WHERE user_id=?",Int::class.java,owner))
        assertEquals("TEST-001",catalog.get("TEST-001").id)
    }
    @Test fun `empty catalog is distinguishable from no match and invalid identifiers return client errors`() {
        assertFalse(catalog.search("닭가슴살",0).available)
        load(fixture());assertTrue(catalog.search("없는음식",0).available)
        mvc.perform(get("/api/v1/food-catalog/invalid!").with(auth(user()))).andExpect(status().isBadRequest)
    }
    @Test
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    fun `invalid row after a flushed batch rolls back the whole import`() {
        val rows=(1..501).map { fixture("ROLLBACK-$it") }+fixture("ROLLBACK-BAD").copy(nutrition=NutritionValues(kcal=BigDecimal("-1")))
        assertFailsWith<IllegalArgumentException>{load(*rows.toTypedArray())}
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM food_catalog WHERE id LIKE 'ROLLBACK-%'",Int::class.java))
    }
}
