package com.bapegg.routinlog.food

import com.bapegg.routinlog.account.persistence.AccountStatus
import com.bapegg.routinlog.account.persistence.UserAccountEntity
import com.bapegg.routinlog.profile.CURRENT_POLICY_VERSION
import com.bapegg.routinlog.profile.NutritionMode
import com.bapegg.routinlog.profile.persistence.UserProfileRepository
import jakarta.persistence.EntityManager
import jakarta.persistence.LockModeType
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.sql.ResultSet
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import tools.jackson.databind.ObjectMapper

/** User-row locking serializes reference changes and first-create races, including profile/account edits. */
@Service
@Transactional
class MealService(private val jdbc: JdbcTemplate, private val entities: EntityManager, private val profiles: UserProfileRepository,private val json:ObjectMapper) {
    fun foods(userId: UUID): FoodListDto {
        account(userId)
        return FoodListDto(jdbc.query("SELECT * FROM foods WHERE user_id=? ORDER BY name,id", foodMapper, userId))
    }

    fun putFood(userId: UUID, id: UUID, request: FoodWrite): FoodDto {
        account(userId, writing = true)
        val name = label(request.name); val brand = optionalText(request.brand, 80); val sourceNote = optionalText(request.sourceNote, 500)
        grams(request.basisGrams); nutrients(request.nutrition)
        val old = food(userId, id)
        version(old?.version, request.version)
        val n = request.nutrition
        if (old == null) jdbc.update(
            """INSERT INTO foods(id,user_id,name,brand,basis_grams,kcal,carbs_g,protein_g,fat_g,fiber_g,preparation,source_note)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?)""", id,userId,name,brand,request.basisGrams,n.kcal,n.carbsG,n.proteinG,n.fatG,n.fiberG,request.preparation.name,sourceNote,
        ) else changed(jdbc.update(
            """UPDATE foods SET name=?,brand=?,basis_grams=?,kcal=?,carbs_g=?,protein_g=?,fat_g=?,fiber_g=?,preparation=?,source_note=?,version=version+1,updated_at=CURRENT_TIMESTAMP
                WHERE id=? AND user_id=? AND version=?""", name,brand,request.basisGrams,n.kcal,n.carbsG,n.proteinG,n.fatG,n.fiberG,request.preparation.name,sourceNote,id,userId,old.version,
        ))
        return food(userId,id)!!
    }

    fun deleteFood(userId: UUID, id: UUID, expectedVersion: Long) {
        account(userId, writing = true)
        val old = food(userId,id) ?: missing("FOOD_NOT_FOUND")
        version(old.version,expectedVersion)
        if (count("SELECT COUNT(*) FROM meal_template_items WHERE user_id=? AND food_id=?",userId,id) > 0) inUse("저장한 식사에서 먼저 이 음식을 빼주세요.")
        changed(jdbc.update("DELETE FROM foods WHERE user_id=? AND id=? AND version=?",userId,id,expectedVersion))
    }

    fun templates(userId: UUID): MealTemplateListDto {
        account(userId)
        return MealTemplateListDto(jdbc.query("SELECT * FROM meal_templates WHERE user_id=? ORDER BY name,id", templateMapper(userId), userId))
    }

    fun putTemplate(userId: UUID, id: UUID, request: MealTemplateWrite): MealTemplateDto {
        account(userId,writing=true)
        val name=label(request.name); val memo=optionalText(request.memo,1000)
        if (request.items.size !in 1..50) invalid("음식은 1개부터 50개까지 담을 수 있어요.")
        val items=request.items.map { TemplateItem(uuid(it.foodId).toString(),it.grams.also(::grams)) }
        if (items.map { it.foodId }.distinct().size != items.size) invalid("같은 음식은 양을 합쳐서 담아주세요.")
        items.forEach { if (food(userId,uuid(it.foodId)) == null) missing("FOOD_NOT_FOUND") }
        val old=template(userId,id); version(old?.version,request.version)
        if (old==null) jdbc.update("INSERT INTO meal_templates(id,user_id,name,memo) VALUES(?,?,?,?)",id,userId,name,memo)
        else changed(jdbc.update("UPDATE meal_templates SET name=?,memo=?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE user_id=? AND id=? AND version=?",name,memo,userId,id,old.version))
        jdbc.update("DELETE FROM meal_template_items WHERE user_id=? AND template_id=?",userId,id)
        items.forEachIndexed { position,item -> jdbc.update("INSERT INTO meal_template_items(user_id,template_id,food_id,position,grams) VALUES(?,?,?,?,?)",userId,id,uuid(item.foodId),position,item.grams) }
        return template(userId,id)!!
    }

    fun deleteTemplate(userId: UUID,id: UUID,expectedVersion: Long) {
        account(userId,writing=true)
        val old=template(userId,id) ?: missing("TEMPLATE_NOT_FOUND"); version(old.version,expectedVersion)
        if (count("SELECT COUNT(*) FROM meal_plan_slots WHERE user_id=? AND template_id=?",userId,id)>0) inUse("식사 계획에서 먼저 이 식사를 해제해주세요.")
        changed(jdbc.update("DELETE FROM meal_templates WHERE user_id=? AND id=? AND version=?",userId,id,expectedVersion))
    }

    fun plan(userId: UUID): MealPlanDto { account(userId); return loadPlan(userId) }

    fun putPlan(userId: UUID,request: MealPlanWrite): MealPlanDto {
        account(userId,writing=true)
        if (request.slots.size !in 1..10) invalid("식사 시간은 1개부터 10개까지 만들 수 있어요.")
        val slots=request.slots.map { MealSlot(uuid(it.id).toString(),label(it.label),it.templateId?.let { value -> uuid(value).toString() }) }
        if (slots.map { it.id }.distinct().size != slots.size || slots.map { it.label }.distinct().size != slots.size) invalid("식사 시간의 이름과 항목은 겹치지 않게 정해주세요.")
        slots.mapNotNull { it.templateId }.forEach { if (template(userId,uuid(it)) == null) missing("TEMPLATE_NOT_FOUND") }
        val old=loadPlan(userId); version(old.version,request.version)
        if (old.version==null) jdbc.update("INSERT INTO meal_plans(user_id) VALUES(?)",userId)
        else changed(jdbc.update("UPDATE meal_plans SET version=version+1,updated_at=CURRENT_TIMESTAMP WHERE user_id=? AND version=?",userId,old.version))
        jdbc.update("DELETE FROM meal_plan_slots WHERE user_id=?",userId)
        slots.forEachIndexed { position,slot -> jdbc.update("INSERT INTO meal_plan_slots(user_id,id,label,template_id,position) VALUES(?,?,?,?,?)",userId,uuid(slot.id),slot.label,slot.templateId?.let(::uuid),position) }
        return loadPlan(userId)
    }

    fun day(userId: UUID,date: LocalDate): MealDayDto {
        val user=account(userId); date(user,date,14)
        val meals=jdbc.query("SELECT * FROM meal_records WHERE user_id=? AND meal_date=? ORDER BY created_at,id",mealMapper(userId),userId,date)
        val target=profiles.findFirstByUserIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDescRevisionDesc(userId,date)?.let {
            if (it.nutritionMode == NutritionMode.NONE) null else NutritionValues(it.dailyCalories?.toBigDecimal(),it.carbohydrateG,it.proteinG,it.fatG,it.fiberG)
        }
        // Sum the unrounded source items across meals, never rounded meal totals.
        val planned=jdbc.query("SELECT payload FROM meal_day_plans WHERE user_id=? AND meal_date=? ORDER BY slot_id",RowMapper { rs,_->json.readValue(rs.getString("payload"),PlannedMeal::class.java) },userId,date)
        return MealDayDto(date,meals,MealNutrition.totals(meals.filter { it.status==MealStatus.EATEN }.flatMap { it.items }),target,planned)
    }

    internal fun applyReview(userId:UUID,date:LocalDate,meal:PlannedMeal) {
        val user=account(userId,true);date(user,date,14)
        if(date<LocalDate.now(ZoneId.of(user.timeZone))||count("SELECT COUNT(*) FROM meal_records WHERE user_id=? AND meal_date=? AND slot_id=?",userId,date,uuid(meal.slotId))>0||
            count("SELECT COUNT(*) FROM meal_day_plans WHERE user_id=? AND meal_date=? AND slot_id=?",userId,date,uuid(meal.slotId))>0)
            throw MealApiException(HttpStatus.CONFLICT,"REVIEW_STALE","이미 계획이나 기록이 있는 끼니예요. 초안을 다시 확인해주세요.")
        jdbc.update("INSERT INTO meal_day_plans(user_id,meal_date,slot_id,payload) VALUES(?,?,?,?)",userId,date,uuid(meal.slotId),json.writeValueAsString(meal))
    }
    fun deleteDayPlan(userId:UUID,date:LocalDate,slotId:UUID,expectedVersion:Long) {
        val user=account(userId,true);date(user,date,14)
        val old=day(userId,date).plannedMeals.firstOrNull { it.slotId==slotId.toString() } ?: return
        version(old.version,expectedVersion)
        if(date<LocalDate.now(ZoneId.of(user.timeZone))||count("SELECT COUNT(*) FROM meal_records WHERE user_id=? AND meal_date=? AND slot_id=?",userId,date,slotId)>0)
            throw MealApiException(HttpStatus.CONFLICT,"REVIEW_STALE","이미 기록한 끼니는 실제 식사 기록에서 수정해주세요.")
        changed(jdbc.update("DELETE FROM meal_day_plans WHERE user_id=? AND meal_date=? AND slot_id=?",userId,date,slotId))
    }

    fun putMeal(userId: UUID,id: UUID,request: MealWrite): MealDto {
        val user=account(userId,writing=true); date(user,request.date)
        val slotId=uuid(request.slotId); val slotLabel=label(request.slotLabel); val note=optionalText(request.note,1000)
        if ((request.status==MealStatus.EATEN && request.items.size !in 1..50) || (request.status==MealStatus.SKIPPED && request.items.isNotEmpty())) invalid("먹은 식사는 음식을 담고, 먹지 않은 식사는 비워주세요.")
        val old=meal(userId,id); version(old?.version,request.version)
        if (old!=null && (old.date != request.date || old.slotId != slotId.toString())) invalid("기록한 날짜와 식사 시간은 바꿀 수 없어요.")
        if (count("SELECT COUNT(*) FROM meal_records WHERE user_id=? AND meal_date=? AND slot_id=? AND id<>?",userId,request.date,slotId,id)>0) conflict()
        val written=request.items.map { MealItemWrite(uuid(it.id).toString(),uuid(it.foodId).toString(),it.grams.also { value -> value?.let(::grams) }) }
        if (written.map { it.id }.distinct().size != written.size) invalid("음식 항목이 중복됐어요.")
        val previous=old?.items?.associateBy { it.id }.orEmpty()
        val planned=day(userId,request.date).plannedMeals.firstOrNull { it.slotId==request.slotId }?.items?.associateBy { it.id }.orEmpty()
        val snapshots=written.map { item ->
            val before=previous[item.id]
            if (before!=null && before.foodId==item.foodId) before.copy(grams=item.grams)
            else if(planned[item.id]?.foodId==item.foodId)planned.getValue(item.id).copy(grams=item.grams)
            else {
                val food=food(userId,uuid(item.foodId)) ?: missing("FOOD_NOT_FOUND")
                LoggedMealItem(item.id,food.id,food.name,food.brand,food.basisGrams,food.nutrition,food.preparation,food.sourceNote,item.grams)
            }
        }
        if (old==null) jdbc.update("INSERT INTO meal_records(id,user_id,meal_date,slot_id,slot_label,status,note) VALUES(?,?,?,?,?,?,?)",id,userId,request.date,slotId,slotLabel,request.status.name,note)
        else changed(jdbc.update("UPDATE meal_records SET status=?,note=?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE user_id=? AND id=? AND version=?",request.status.name,note,userId,id,old.version))
        jdbc.update("DELETE FROM meal_record_items WHERE user_id=? AND meal_id=?",userId,id)
        snapshots.forEachIndexed { position,item ->
            val n=item.nutrition
            jdbc.update("""INSERT INTO meal_record_items(user_id,meal_id,id,position,food_id,name,brand,basis_grams,kcal,carbs_g,protein_g,fat_g,fiber_g,preparation,source_note,grams)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",userId,id,uuid(item.id),position,uuid(item.foodId),item.name,item.brand,item.basisGrams,n.kcal,n.carbsG,n.proteinG,n.fatG,n.fiberG,item.preparation.name,item.sourceNote,item.grams)
        }
        return meal(userId,id)!!
    }

    fun deleteMeal(userId: UUID,id: UUID,expectedVersion: Long) {
        account(userId,writing=true)
        val old=meal(userId,id) ?: missing("MEAL_NOT_FOUND"); version(old.version,expectedVersion)
        changed(jdbc.update("DELETE FROM meal_records WHERE user_id=? AND id=? AND version=?",userId,id,expectedVersion))
    }

    private fun account(userId: UUID,writing: Boolean=false): UserAccountEntity {
        val user=entities.find(UserAccountEntity::class.java,userId,LockModeType.PESSIMISTIC_WRITE)
        if (user==null || user.status!=AccountStatus.ACTIVE) throw MealApiException(HttpStatus.UNAUTHORIZED,"AUTHENTICATION_REQUIRED","다시 로그인해주세요.")
        if (writing) {
            val profile=profiles.findFirstByUserIdOrderByRevisionDesc(userId)
            if (profile==null || profile.healthConsentVersion!=CURRENT_POLICY_VERSION) throw MealApiException(HttpStatus.FORBIDDEN,"PROFILE_REQUIRED","시작 정보와 건강정보 처리 동의를 먼저 확인해주세요.")
        }
        return user
    }
    private fun food(userId: UUID,id: UUID): FoodDto? = jdbc.query("SELECT * FROM foods WHERE user_id=? AND id=?",foodMapper,userId,id).firstOrNull()
    private fun template(userId: UUID,id: UUID): MealTemplateDto? = jdbc.query("SELECT * FROM meal_templates WHERE user_id=? AND id=?",templateMapper(userId),userId,id).firstOrNull()
    private fun meal(userId: UUID,id: UUID): MealDto? = jdbc.query("SELECT * FROM meal_records WHERE user_id=? AND id=?",mealMapper(userId),userId,id).firstOrNull()
    private fun loadPlan(userId: UUID): MealPlanDto {
        val version=jdbc.query("SELECT version FROM meal_plans WHERE user_id=?",RowMapper { rs,_ -> rs.getLong("version") },userId).firstOrNull()
        if (version==null) return MealPlanDto(listOf("아침","점심","저녁").mapIndexed { index,name -> MealSlot("00000000-0000-0000-0000-00000000000${index+1}",name) })
        val slots=jdbc.query("SELECT * FROM meal_plan_slots WHERE user_id=? ORDER BY position",RowMapper { rs,_ -> MealSlot(rs.getString("id"),rs.getString("label"),rs.getString("template_id")) },userId)
        return MealPlanDto(slots,version)
    }
    private fun templateMapper(userId: UUID)=RowMapper { rs: ResultSet,_: Int ->
        val id=rs.getObject("id",UUID::class.java)
        val items=jdbc.query("SELECT food_id,grams FROM meal_template_items WHERE user_id=? AND template_id=? ORDER BY position",RowMapper { row,_ -> TemplateItem(row.getString("food_id"),row.getBigDecimal("grams")) },userId,id)
        MealTemplateDto(id.toString(),rs.getString("name"),items,rs.getString("memo"),rs.getLong("version"))
    }
    private fun mealMapper(userId: UUID)=RowMapper { rs: ResultSet,_: Int ->
        val id=rs.getObject("id",UUID::class.java)
        val items=jdbc.query("SELECT * FROM meal_record_items WHERE user_id=? AND meal_id=? ORDER BY position",RowMapper { row,_ ->
            LoggedMealItem(row.getString("id"),row.getString("food_id"),row.getString("name"),row.getString("brand"),row.getBigDecimal("basis_grams"),row.nutrition(),FoodPreparation.valueOf(row.getString("preparation")),row.getString("source_note"),row.getBigDecimal("grams"))
        },userId,id)
        val status=MealStatus.valueOf(rs.getString("status"))
        MealDto(id.toString(),rs.getObject("meal_date",LocalDate::class.java),rs.getString("slot_id"),rs.getString("slot_label"),status,items,rs.getString("note"),rs.getLong("version"),MealNutrition.totals(if(status==MealStatus.EATEN) items else emptyList()))
    }
    private fun count(sql: String,vararg args: Any): Long = jdbc.queryForObject(sql,Long::class.java,*args)!!
    private fun version(existing: Long?,requested: Long?) { if (requested!=null && requested<0) invalid("기록 버전을 확인해주세요."); if(existing!=requested) conflict() }
    private fun changed(count: Int) { if(count!=1) conflict() }
    private fun date(user: UserAccountEntity,value: LocalDate,futureDays:Long=0) { if(value<LocalDate.of(1900,1,1) || value>LocalDate.now(ZoneId.of(user.timeZone)).plusDays(futureDays)) invalid("선택 가능한 날짜 범위를 확인해주세요.") }
    private fun grams(value: BigDecimal) { decimal(value,positive=true) }
    private fun nutrients(values: NutritionValues) { listOf(values.kcal,values.carbsG,values.proteinG,values.fatG,values.fiberG).filterNotNull().forEach { decimal(it,positive=false) } }
    private fun decimal(value: BigDecimal,positive: Boolean) { if(value.scale()>2 || value>BigDecimal("100000") || (if(positive)value.signum()<=0 else value.signum()<0)) invalid("양과 영양정보의 범위·소수 자릿수를 확인해주세요.") }
    private fun label(value: String): String = value.trim().also { if(it.isEmpty() || it.length>80 || value.contains('\u0000')) invalid("이름은 1자부터 80자까지 입력해주세요.") }
    private fun optionalText(value: String?,max: Int): String? { if(value!=null && (value.length>max || value.contains('\u0000'))) invalid("입력한 내용의 길이와 문자를 확인해주세요."); return value?.trim()?.ifEmpty { null } }
    private fun missing(code: String): Nothing = throw MealApiException(HttpStatus.NOT_FOUND,code,"음식이나 식사 기록을 찾을 수 없어요. 목록을 새로 확인해주세요.")
    private fun inUse(message: String): Nothing = throw MealApiException(HttpStatus.CONFLICT,"RESOURCE_IN_USE",message)
    private fun conflict(): Nothing = throw MealApiException(HttpStatus.CONFLICT,"VERSION_CONFLICT","기록이 변경됐어요. 최신 내용을 확인한 뒤 다시 시도해주세요.")
    private fun invalid(message: String): Nothing = throw MealApiException(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR",message)
    private val foodMapper=RowMapper { rs: ResultSet,_: Int -> FoodDto(rs.getString("id"),rs.getString("name"),rs.getString("brand"),rs.getBigDecimal("basis_grams"),rs.nutrition(),FoodPreparation.valueOf(rs.getString("preparation")),rs.getString("source_note"),rs.getLong("version")) }
    private fun ResultSet.nutrition()=NutritionValues(getBigDecimal("kcal"),getBigDecimal("carbs_g"),getBigDecimal("protein_g"),getBigDecimal("fat_g"),getBigDecimal("fiber_g"))
}

internal fun uuid(value: String): UUID = try {
    UUID.fromString(value).also { if(!it.toString().equals(value,ignoreCase=true)) throw IllegalArgumentException() }
} catch (_: IllegalArgumentException) { throw MealApiException(HttpStatus.BAD_REQUEST,"INVALID_REQUEST","항목의 식별자를 확인해주세요.") }
