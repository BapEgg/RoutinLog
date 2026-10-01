package com.bapegg.routinlog.condition

import com.bapegg.routinlog.account.persistence.AccountStatus
import com.bapegg.routinlog.account.persistence.UserAccountEntity
import com.bapegg.routinlog.profile.CURRENT_POLICY_VERSION
import com.bapegg.routinlog.profile.persistence.UserProfileRepository
import com.bapegg.routinlog.security.AuthenticatedUser
import jakarta.persistence.EntityManager
import jakarta.persistence.LockModeType
import org.springframework.http.*
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.*
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import tools.jackson.databind.ObjectMapper
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

/** Self-reported observations, never a measured recovery score or energy estimate. */
data class ConditionValues(val sleepMinutes:Int?=null,val fatigue:String?=null,val soreness:String?=null,
    val sorenessArea:String?=null,val stress:String?=null,val activity:String?=null,val memo:String?=null) {
    override fun toString()="ConditionValues(redacted)"
}
data class ConditionWrite(val values:ConditionValues,val version:Long?=null)
data class ConditionDto(val date:LocalDate,val values:ConditionValues,val version:Long)
data class ConditionList(val items:List<ConditionDto>)
class ConditionException(val status:HttpStatus,val code:String,message:String):RuntimeException(message)

@Service
@Transactional
class ConditionService(private val jdbc:JdbcTemplate,private val entities:EntityManager,
    private val profiles:UserProfileRepository,private val json:ObjectMapper) {
    fun list(userId:UUID,from:LocalDate,to:LocalDate):ConditionList {
        val user=account(userId); date(user,from); date(user,to)
        if(ChronoUnit.DAYS.between(from,to) !in 0..365) invalid()
        return ConditionList(jdbc.query("SELECT payload FROM daily_conditions WHERE user_id=? AND recorded_on BETWEEN ? AND ? ORDER BY recorded_on DESC",
            { rs,_ -> json.readValue(rs.getString("payload"),ConditionDto::class.java) },userId,from,to))
    }
    fun save(userId:UUID,on:LocalDate,write:ConditionWrite):ConditionDto {
        val user=account(userId,true); date(user,on)
        val values=write.values
        if(values.sleepMinutes!=null && values.sleepMinutes !in 0..1440) invalid()
        if(values.fatigue!=null && values.fatigue !in setOf("LOW","MODERATE","HIGH")) invalid()
        if(values.stress!=null && values.stress !in setOf("LOW","MODERATE","HIGH")) invalid()
        if(values.soreness!=null && values.soreness !in setOf("NONE","MILD","HIGH")) invalid()
        if(values.activity!=null && values.activity !in setOf("LIGHT","MODERATE","HIGH")) invalid()
        val cleaned=values.copy(sorenessArea=text(values.sorenessArea,80),memo=text(values.memo,1000))
        if(cleaned.sorenessArea!=null && cleaned.soreness !in setOf("MILD","HIGH")) invalid()
        if(cleaned==ConditionValues()) invalid()
        val old=find(userId,on); version(old?.version,write.version)
        val saved=ConditionDto(on,cleaned,(old?.version ?: -1)+1)
        if(old==null) jdbc.update("INSERT INTO daily_conditions(user_id,recorded_on,version,payload) VALUES(?,?,?,?)",userId,on,saved.version,json.writeValueAsString(saved))
        else jdbc.update("UPDATE daily_conditions SET version=?,payload=? WHERE user_id=? AND recorded_on=?",saved.version,json.writeValueAsString(saved),userId,on)
        return saved
    }
    fun delete(userId:UUID,on:LocalDate,expected:Long) {
        val user=account(userId,true); date(user,on)
        val old=find(userId,on) ?: throw ConditionException(HttpStatus.NOT_FOUND,"CONDITION_NOT_FOUND","이 날짜의 컨디션 기록이 없어요.")
        version(old.version,expected)
        jdbc.update("DELETE FROM daily_conditions WHERE user_id=? AND recorded_on=?",userId,on)
    }
    private fun find(userId:UUID,on:LocalDate)=jdbc.query("SELECT payload FROM daily_conditions WHERE user_id=? AND recorded_on=?",
        {rs,_ -> json.readValue(rs.getString("payload"),ConditionDto::class.java)},userId,on).firstOrNull()
    private fun account(id:UUID,write:Boolean=false):UserAccountEntity {
        val user=entities.find(UserAccountEntity::class.java,id,LockModeType.PESSIMISTIC_WRITE)
        if(user==null || user.status!=AccountStatus.ACTIVE) throw ConditionException(HttpStatus.UNAUTHORIZED,"AUTH_REQUIRED","다시 로그인해주세요.")
        if(write && profiles.findFirstByUserIdOrderByRevisionDesc(id)?.healthConsentVersion!=CURRENT_POLICY_VERSION)
            throw ConditionException(HttpStatus.FORBIDDEN,"PROFILE_REQUIRED","시작 정보와 건강정보 처리 동의를 먼저 확인해주세요.")
        return user
    }
    private fun version(old:Long?,expected:Long?) {
        if(expected!=null && expected<0) invalid()
        if(old!=expected) throw ConditionException(HttpStatus.CONFLICT,"VERSION_CONFLICT","컨디션 기록이 변경됐어요. 최신 기록을 확인해주세요.")
    }
    private fun date(user:UserAccountEntity,date:LocalDate) { if(date<LocalDate.of(1900,1,1)||date>LocalDate.now(ZoneId.of(user.timeZone))) invalid() }
    private fun text(value:String?,max:Int):String? { if(value!=null&&(value.length>max||'\u0000' in value)) invalid(); return value?.trim()?.ifBlank { null } }
    private fun invalid():Nothing=throw ConditionException(HttpStatus.BAD_REQUEST,"INVALID_REQUEST","날짜와 입력 범위를 확인하고, 한 항목 이상 남겨주세요.")
}

@RestController
@RequestMapping("/api/v1/conditions")
class ConditionController(private val service:ConditionService) {
    @GetMapping fun list(@AuthenticationPrincipal user:AuthenticatedUser,@RequestParam from:LocalDate,@RequestParam to:LocalDate)=
        ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.list(user.userId,from,to))
    @PutMapping("/{date}") fun save(@AuthenticationPrincipal user:AuthenticatedUser,@PathVariable date:LocalDate,@RequestBody write:ConditionWrite)=
        ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.save(user.userId,date,write))
    @DeleteMapping("/{date}") fun delete(@AuthenticationPrincipal user:AuthenticatedUser,@PathVariable date:LocalDate,@RequestParam version:Long):ResponseEntity<Void> {
        service.delete(user.userId,date,version); return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build()
    }
}
@RestControllerAdvice(assignableTypes=[ConditionController::class])
class ConditionErrors {
    @ExceptionHandler(ConditionException::class) fun expected(error:ConditionException)=ResponseEntity.status(error.status).cacheControl(CacheControl.noStore()).body(mapOf("code" to error.code,"message" to error.message))
    @ExceptionHandler(HttpMessageNotReadableException::class,MethodArgumentTypeMismatchException::class,MissingServletRequestParameterException::class)
    fun malformed()=ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(mapOf("code" to "INVALID_REQUEST","message" to "요청 형식과 필수 항목을 확인해주세요."))
}
