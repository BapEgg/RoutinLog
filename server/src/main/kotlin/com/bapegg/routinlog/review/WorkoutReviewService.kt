package com.bapegg.routinlog.review

import com.bapegg.routinlog.account.persistence.*
import com.bapegg.routinlog.condition.*
import com.bapegg.routinlog.profile.*
import com.bapegg.routinlog.profile.persistence.UserProfileRepository
import com.bapegg.routinlog.workout.*
import jakarta.persistence.EntityManager
import jakarta.persistence.LockModeType
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

class ReviewException(val status:HttpStatus,val code:String,message:String):RuntimeException(message)
internal data class ReviewSource(val profileRevision:Long,val goal:String,val timeZone:String,val workouts:List<WorkoutDayDto>,val conditions:List<ConditionDto>)

@Service @Transactional
class WorkoutReviewService(private val entities:EntityManager,private val profiles:UserProfileRepository,
    private val workouts:WorkoutService,private val conditions:ConditionService,private val jdbc:JdbcTemplate,private val json:ObjectMapper) {
    fun get(id:UUID,week:LocalDate):WorkoutReviewDto { account(id);return stored(id,week)?.view ?: fail(HttpStatus.NOT_FOUND,"REVIEW_NOT_FOUND","아직 만든 초안이 없어요.") }
    fun history(id:UUID):ReviewHistory {
        account(id)
        return ReviewHistory(jdbc.query("SELECT payload FROM workout_review_events WHERE user_id=? ORDER BY source_week DESC,version DESC LIMIT 20",
            {rs,_->json.readValue(rs.getString("payload"),StoredReview::class.java).view},id))
    }
    fun prepare(id:UUID,week:LocalDate,request:ReviewPrepare):WorkoutReviewDto {
        val user=account(id,true);val old=stored(id,week)
        if(old!=null&&request.version==null)return old.view // Lost prepare response is safe to retry without regenerating choices.
        if(old?.view?.version!=request.version)conflict()
        if(old?.view?.status=="APPLIED")conflict()
        validWeek(user,week)
        val source=source(id,week,user)
        val from=maxOf(week.plusWeeks(1),today(user));val to=week.plusDays(13)
        val upcoming=if(from>to)emptyList() else workouts.days(id,from,to).items
        val result=WorkoutReviewRules.create(week,source.goal,source.workouts,source.conditions,upcoming)
        val record=StoredReview(result.view.copy(version=old?.view?.version?.plus(1) ?: 0),hash(source),result.day,result.entry?.id)
        save(id,record,old==null)
        return record.view
    }
    fun decide(id:UUID,week:LocalDate,request:ReviewDecision):WorkoutReviewDto {
        val user=account(id,true);val old=stored(id,week) ?: fail(HttpStatus.NOT_FOUND,"REVIEW_NOT_FOUND","초안을 먼저 확인해주세요.")
        val reason=request.reason?.trim()?.ifBlank { null }
        if(request.version<0||request.decision !in setOf("APPLY","HOLD")||request.choice !in setOf("KEEP","LAST_PERFORMANCE","CUSTOM")||
            reason!=null&&(reason.length>1000||'\u0000' in reason))invalid()
        val targets=request.targets.map { it.copy(weightKg=it.weightKg?.stripTrailingZeros()) }.sortedBy { it.setId }
        val command=request.copy(targets=targets,reason=reason)
        val fingerprint=hash(command)
        if(old.view.status!="DRAFT") {
            if(old.decisionFingerprint==fingerprint)return old.view
            conflict()
        }
        if(request.version!=old.view.version)conflict()
        validateTargets(old.view,targets,request.choice)
        if(request.decision=="APPLY") {
            validWeek(user,week)
            if(hash(source(id,week,user))!=old.signature)stale()
            val day=old.targetDay ?: fail(HttpStatus.CONFLICT,"REVIEW_NO_PLAN","날짜별 운동 계획과 세트 목표를 먼저 정해주세요.")
            workouts.applyReview(id,day,old.entryId ?: invalid(),targets.map { PlannedSet(it.setId,it.weightKg,it.reps,it.durationSeconds) })
        }
        val changed=old.copy(view=old.view.copy(status=if(request.decision=="APPLY")"APPLIED" else "HELD",version=old.view.version+1,
            choice=request.choice,chosen=old.view.planned.map { p->targets.single { it.setId==p.setId } },decisionReason=reason,decidedAt=Instant.now()),decisionFingerprint=fingerprint)
        save(id,changed,false)
        return changed.view
    }
    private fun validateTargets(view:WorkoutReviewDto,targets:List<ReviewTarget>,choice:String) {
        if(targets.map { it.setId }.sorted()!=view.planned.map { it.setId }.sorted())invalid()
        if(targets.isNotEmpty()) {
            val type=view.exercise?.recordType ?: invalid()
            targets.forEach { s->
                if(s.weightKg!=null&&(s.weightKg.signum()<0||s.weightKg>BigDecimal("2000")||s.weightKg.scale()>3)||
                    s.reps!=null&&s.reps !in 1..1000||s.durationSeconds!=null&&s.durationSeconds !in 1..86400)invalid()
                when(type){
                    RecordType.WEIGHT_REPS->if(s.weightKg==null||s.reps==null||s.durationSeconds!=null)invalid()
                    RecordType.REPS->if(s.weightKg!=null||s.reps==null||s.durationSeconds!=null)invalid()
                    RecordType.DURATION->if(s.weightKg!=null||s.reps!=null||s.durationSeconds==null)invalid()
                }
            }
        }
        val expected=when(choice){"KEEP"->view.planned;"LAST_PERFORMANCE"->view.alternative ?: invalid();else->null}
        if(expected!=null&&targets!=expected.map { it.copy(weightKg=it.weightKg?.stripTrailingZeros()) }.sortedBy { it.setId })invalid()
    }
    private fun source(id:UUID,week:LocalDate,user:UserAccountEntity):ReviewSource {
        val profile=profiles.findFirstByUserIdOrderByRevisionDesc(id) ?: invalid()
        val to=minOf(week.plusDays(6),today(user))
        return ReviewSource(profile.revision,profile.goal.name,user.timeZone,workouts.days(id,week,to).items,conditions.list(id,week,to).items)
    }
    private fun save(id:UUID,value:StoredReview,create:Boolean) {
        val view=value.view;val payload=json.writeValueAsString(value)
        if(create)jdbc.update("INSERT INTO workout_reviews(user_id,source_week,version,payload) VALUES(?,?,?,?)",id,view.week,view.version,payload)
        else jdbc.update("UPDATE workout_reviews SET version=?,payload=? WHERE user_id=? AND source_week=?",view.version,payload,id,view.week)
        jdbc.update("INSERT INTO workout_review_events(user_id,source_week,version,payload) VALUES(?,?,?,?)",id,view.week,view.version,payload)
    }
    private fun stored(id:UUID,week:LocalDate)=jdbc.query("SELECT payload FROM workout_reviews WHERE user_id=? AND source_week=?",
        {rs,_->json.readValue(rs.getString("payload"),StoredReview::class.java)},id,week).firstOrNull()
    private fun hash(value:Any)=MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(value)).joinToString("") { "%02x".format(it) }
    private fun account(id:UUID,write:Boolean=false):UserAccountEntity {
        val user=entities.find(UserAccountEntity::class.java,id,LockModeType.PESSIMISTIC_WRITE)
        if(user==null||user.status!=AccountStatus.ACTIVE)fail(HttpStatus.UNAUTHORIZED,"AUTHENTICATION_REQUIRED","다시 로그인해주세요.")
        if(write&&profiles.findFirstByUserIdOrderByRevisionDesc(id)?.healthConsentVersion!=CURRENT_POLICY_VERSION)
            fail(HttpStatus.FORBIDDEN,"PROFILE_REQUIRED","시작 정보와 건강정보 동의를 먼저 확인해주세요.")
        return user
    }
    private fun today(user:UserAccountEntity)=LocalDate.now(ZoneId.of(user.timeZone))
    private fun validWeek(user:UserAccountEntity,week:LocalDate) {
        val current=today(user).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        if(week!=current&&week!=current.minusWeeks(1))fail(HttpStatus.CONFLICT,"REVIEW_STALE","지난주 또는 이번 주 리포트에서 새 초안을 만들어주세요.")
    }
    private fun invalid():Nothing=fail(HttpStatus.BAD_REQUEST,"INVALID_REQUEST","선택한 세트와 입력한 값을 확인해주세요.")
    private fun conflict():Nothing=fail(HttpStatus.CONFLICT,"VERSION_CONFLICT","초안이나 내 선택이 변경됐어요. 다시 불러와주세요.")
    private fun stale():Nothing=fail(HttpStatus.CONFLICT,"REVIEW_STALE","분석에 사용한 기록이 바뀌었어요. 새 기록으로 초안을 다시 만들어주세요.")
    private fun fail(status:HttpStatus,code:String,message:String):Nothing=throw ReviewException(status,code,message)
}
