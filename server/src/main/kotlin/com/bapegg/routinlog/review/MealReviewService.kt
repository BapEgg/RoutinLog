package com.bapegg.routinlog.review

import com.bapegg.routinlog.account.persistence.*
import com.bapegg.routinlog.food.*
import com.bapegg.routinlog.profile.CURRENT_POLICY_VERSION
import com.bapegg.routinlog.profile.persistence.UserProfileRepository
import com.bapegg.routinlog.report.*
import jakarta.persistence.*
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.*
import java.time.temporal.TemporalAdjusters
import java.util.UUID

internal data class MealReviewSource(val profileRevision:Long,val timeZone:String,val days:List<MealDayDto>,val plan:MealPlanDto,val templates:List<MealTemplateDto>,val foods:List<FoodDto>)
@Service @Transactional
class MealReviewService(private val entities:EntityManager,private val profiles:UserProfileRepository,private val meals:MealService,
    private val reports:WeeklyReportService,private val jdbc:JdbcTemplate,private val json:ObjectMapper) {
    fun get(id:UUID,week:LocalDate):MealReviewDto { account(id);return stored(id,week)?.view ?: fail(HttpStatus.NOT_FOUND,"REVIEW_NOT_FOUND","아직 만든 식단 초안이 없어요.") }
    fun history(id:UUID):MealReviewHistory {
        account(id);return MealReviewHistory(jdbc.query("SELECT payload FROM meal_review_events WHERE user_id=? ORDER BY source_week DESC,version DESC LIMIT 20",{rs,_->json.readValue(rs.getString("payload"),StoredMealReview::class.java).view},id))
    }
    fun prepare(id:UUID,week:LocalDate,request:ReviewPrepare):MealReviewDto {
        val user=account(id,true);val old=stored(id,week)
        if(old!=null&&request.version==null)return old.view
        if(old?.view?.version!=request.version||old?.view?.status=="APPLIED")conflict()
        validWeek(user,week)
        val report=reports.get(id,week);val source=source(id,user,report)
        val from=maxOf(week.plusWeeks(1),today(user));val to=week.plusDays(13)
        val upcoming=if(from>to)emptyList() else from.datesUntil(to.plusDays(1)).map { meals.day(id,it) }.toList()
        val view=MealReviewRules.create(week,report,source.plan,source.templates,source.foods,upcoming).copy(version=old?.view?.version?.plus(1) ?: 0)
        val saved=StoredMealReview(view,hash(source),hash(view.targetDate?.let { meals.day(id,it) }))
        save(id,saved,old==null);return view
    }
    fun decide(id:UUID,week:LocalDate,request:MealReviewDecision):MealReviewDto {
        val user=account(id,true);val old=stored(id,week) ?: fail(HttpStatus.NOT_FOUND,"REVIEW_NOT_FOUND","초안을 먼저 확인해주세요.")
        val reason=request.reason?.trim()?.ifBlank { null }
        if(request.version<0||request.decision !in setOf("APPLY","HOLD")||reason!=null&&(reason.length>1000||'\u0000' in reason))invalid()
        val normalized=request.copy(amounts=request.amounts.map { it.copy(grams=it.grams.stripTrailingZeros()) }.sortedBy { it.foodId },reason=reason)
        val fingerprint=hash(normalized)
        if(old.view.status!="DRAFT") { if(old.fingerprint==fingerprint)return old.view;conflict() }
        if(old.view.version!=request.version)conflict()
        val option=old.view.options.firstOrNull { it.id==request.optionId }
        if(option==null&&(old.view.options.isNotEmpty()||request.optionId!=null||request.amounts.isNotEmpty()))invalid()
        if(request.amounts.map { it.foodId }.sorted()!=option?.items.orEmpty().map { it.foodId }.sorted())invalid()
        if(request.amounts.any { it.grams.signum()<=0||it.grams>BigDecimal("100000")||it.grams.stripTrailingZeros().scale()>2 })invalid()
        val amounts=normalized.amounts.associateBy { it.foodId }
        val chosen=option?.let { PlannedMeal(old.view.slotId ?: invalid(),old.view.slotLabel ?: invalid(),it.name,it.items.map { item->item.copy(grams=amounts.getValue(item.foodId).grams) }) }
        if(request.decision=="APPLY") {
            validWeek(user,week)
            val date=old.view.targetDate ?: fail(HttpStatus.CONFLICT,"REVIEW_NO_PLAN","기본 식단과 끼니를 먼저 정해주세요.")
            if(date<today(user)||hash(source(id,user,reports.get(id,week)))!=old.signature||hash(meals.day(id,date))!=old.targetSignature)stale()
            meals.applyReview(id,date,chosen ?: invalid())
        }
        val saved=old.copy(view=old.view.copy(status=if(request.decision=="HOLD")"HELD" else "APPLIED",version=old.view.version+1,chosen=chosen,decisionReason=reason,decidedAt=Instant.now()),fingerprint=fingerprint)
        save(id,saved,false);return saved.view
    }
    private fun source(id:UUID,user:UserAccountEntity,report:WeeklyReport)=MealReviewSource(profiles.findFirstByUserIdOrderByRevisionDesc(id)?.revision ?: -1,user.timeZone,report.mealDays,meals.plan(id),meals.templates(id).items,meals.foods(id).items)
    private fun save(id:UUID,value:StoredMealReview,create:Boolean) {
        val r=value.view;val payload=json.writeValueAsString(value)
        if(create)jdbc.update("INSERT INTO meal_reviews(user_id,source_week,version,payload) VALUES(?,?,?,?)",id,r.week,r.version,payload)
        else jdbc.update("UPDATE meal_reviews SET version=?,payload=? WHERE user_id=? AND source_week=?",r.version,payload,id,r.week)
        jdbc.update("INSERT INTO meal_review_events(user_id,source_week,version,payload) VALUES(?,?,?,?)",id,r.week,r.version,payload)
    }
    private fun stored(id:UUID,week:LocalDate)=jdbc.query("SELECT payload FROM meal_reviews WHERE user_id=? AND source_week=?",{rs,_->json.readValue(rs.getString("payload"),StoredMealReview::class.java)},id,week).firstOrNull()
    private fun hash(value:Any?)=MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(value)).joinToString(""){"%02x".format(it)}
    private fun account(id:UUID,write:Boolean=false):UserAccountEntity {
        val user=entities.find(UserAccountEntity::class.java,id,LockModeType.PESSIMISTIC_WRITE)
        if(user==null||user.status!=AccountStatus.ACTIVE)fail(HttpStatus.UNAUTHORIZED,"AUTHENTICATION_REQUIRED","다시 로그인해주세요.")
        if(write&&profiles.findFirstByUserIdOrderByRevisionDesc(id)?.healthConsentVersion!=CURRENT_POLICY_VERSION)fail(HttpStatus.FORBIDDEN,"PROFILE_REQUIRED","시작 정보와 건강정보 동의를 먼저 확인해주세요.")
        return user
    }
    private fun today(user:UserAccountEntity)=LocalDate.now(ZoneId.of(user.timeZone))
    private fun validWeek(user:UserAccountEntity,week:LocalDate) {
        val current=today(user).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        if(week!=current&&week!=current.minusWeeks(1))stale()
    }
    private fun invalid():Nothing=fail(HttpStatus.BAD_REQUEST,"INVALID_REQUEST","선택한 식단과 음식량을 확인해주세요.")
    private fun conflict():Nothing=fail(HttpStatus.CONFLICT,"VERSION_CONFLICT","초안이나 내 선택이 변경됐어요. 다시 불러와주세요.")
    private fun stale():Nothing=fail(HttpStatus.CONFLICT,"REVIEW_STALE","기록·음식 정보·계획이나 적용 날짜가 달라졌어요. 초안을 다시 만들어주세요.")
    private fun fail(status:HttpStatus,code:String,message:String):Nothing=throw ReviewException(status,code,message)
}
