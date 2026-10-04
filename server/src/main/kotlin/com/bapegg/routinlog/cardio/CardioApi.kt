package com.bapegg.routinlog.cardio

import com.bapegg.routinlog.account.persistence.*
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
import java.math.BigDecimal
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.UUID

enum class CardioEnergyKind { ACTIVE, TOTAL, UNKNOWN }
data class CardioValues(val activity:String,val minutes:Int,val deviceKcal:BigDecimal?=null,
    val energyKind:CardioEnergyKind?=null,val deviceName:String?=null,val speedKmh:BigDecimal?=null,
    val inclinePercent:BigDecimal?=null,val distanceKm:BigDecimal?=null,val effort:Int?=null,
    val fatigue:String?=null,val memo:String?=null,val metCode:String?=null) {
    override fun toString()="CardioValues(redacted)"
}
data class CardioWrite(val date:LocalDate,val values:CardioValues,val version:Long?=null)
data class CardioDto(val id:UUID,val date:LocalDate,val values:CardioValues,val version:Long,val estimate:CardioEstimate?=null)
data class CardioList(val items:List<CardioDto>)
class CardioException(val status:HttpStatus,val code:String,message:String):RuntimeException(message)

/** Recorded device readings are observations, not TDEE, prescriptions or calculated active calories. */
@Service @Transactional
class CardioService(private val jdbc:JdbcTemplate,private val entities:EntityManager,
    private val profiles:UserProfileRepository,private val json:ObjectMapper) {
    fun list(owner:UUID,from:LocalDate,to:LocalDate):CardioList {
        val user=account(owner);date(user,from);date(user,to)
        if(ChronoUnit.DAYS.between(from,to) !in 0..30) invalid()
        return CardioList(range(owner,from,to))
    }
    fun save(owner:UUID,id:UUID,write:CardioWrite):CardioDto {
        val user=account(owner,true);date(user,write.date)
        if(write.version!=null&&write.version<0)invalid()
        val v=write.values
        val cleaned=v.copy(activity=text(v.activity,80) ?: invalid(),deviceName=text(v.deviceName,80),memo=text(v.memo,1000))
        if(v.minutes !in 1..1440 || v.effort!=null&&v.effort !in 1..10 || v.fatigue!=null&&v.fatigue !in setOf("LOW","MODERATE","HIGH"))invalid()
        number(v.deviceKcal,"100000");number(v.speedKmh,"150");number(v.inclinePercent,"100");number(v.distanceKm,"1500")
        if(v.deviceKcal==null) { if(v.energyKind!=null||cleaned.deviceName!=null)invalid() }
        else if(v.energyKind==null||cleaned.deviceName==null)invalid()
        val old=find(owner,id)
        // A lost response can be retried with the same ID/version without duplicating a workout.
        if(old!=null&&old.version==(write.version ?: -1)+1&&old.date==write.date&&old.values==cleaned)return old
        version(old?.version,write.version)
        val others=range(owner,write.date,write.date).filterNot { it.id==id }
        if(others.size>=24||others.sumOf { it.values.minutes }+v.minutes>1440)
            throw CardioException(HttpStatus.BAD_REQUEST,"DAILY_LIMIT","하루 기록은 24개, 총 운동 시간은 24시간 이내로 입력해주세요.")
        val estimate=v.metCode?.let { code->
            if(CardioEstimation.activities.none { it.code==code })invalid()
            if(code in setOf("17355","17358")) {
                if(v.inclinePercent!=null&&v.inclinePercent.signum()!=0)invalid()
                val range=if(code=="17355")BigDecimal("4.8")..BigDecimal("5.5") else BigDecimal("5.6")..BigDecimal("6.3")
                if(v.speedKmh!=null&&v.speedKmh !in range)invalid()
            }
            val profile=jdbc.query("SELECT age,initial_weight_kg,effective_from FROM user_profile_revisions WHERE user_id=? AND effective_from<=? ORDER BY effective_from DESC,revision DESC LIMIT 1",
                {rs,_->Triple(rs.getInt(1),rs.getBigDecimal(2),rs.getDate(3).toLocalDate())},owner,write.date).firstOrNull()
            if(profile==null||profile.first !in 19..59)throw CardioException(HttpStatus.BAD_REQUEST,"ESTIMATE_UNAVAILABLE","이 추정은 19~59세 성인 기준이에요. 시간이나 기기 값으로 기록해주세요.")
            val weight=jdbc.query("SELECT weight_kg,measured_on FROM body_measurements WHERE user_id=? AND measured_on BETWEEN ? AND ? AND weight_kg IS NOT NULL ORDER BY measured_on DESC LIMIT 1",
                {rs,_->rs.getBigDecimal(1) to rs.getDate(2).toLocalDate()},owner,write.date.minusDays(30),write.date).firstOrNull()
            CardioEstimation.calculate(code,weight?.first ?: profile.second,v.minutes,if(weight!=null)"${weight.second} 측정"else"${profile.third} 시작 설정")
        }
        val saved=CardioDto(id,write.date,cleaned,(old?.version ?: -1)+1,estimate)
        if(old==null)jdbc.update("INSERT INTO cardio_records(user_id,id,recorded_on,version,payload) VALUES(?,?,?,?,?)",owner,id,saved.date,saved.version,json.writeValueAsString(saved))
        else jdbc.update("UPDATE cardio_records SET recorded_on=?,version=?,payload=? WHERE user_id=? AND id=?",saved.date,saved.version,json.writeValueAsString(saved),owner,id)
        return saved
    }
    fun delete(owner:UUID,id:UUID,expected:Long) {
        account(owner,true)
        if(expected<0)invalid()
        val old=find(owner,id) ?: throw CardioException(HttpStatus.NOT_FOUND,"CARDIO_NOT_FOUND","이 유산소 기록이 없어요.")
        version(old.version,expected)
        jdbc.update("DELETE FROM cardio_records WHERE user_id=? AND id=?",owner,id)
    }
    private fun range(owner:UUID,from:LocalDate,to:LocalDate)=jdbc.query("SELECT payload FROM cardio_records WHERE user_id=? AND recorded_on BETWEEN ? AND ? ORDER BY recorded_on,id",
        {rs,_->json.readValue(rs.getString("payload"),CardioDto::class.java)},owner,from,to)
    private fun find(owner:UUID,id:UUID)=jdbc.query("SELECT payload FROM cardio_records WHERE user_id=? AND id=?",
        {rs,_->json.readValue(rs.getString("payload"),CardioDto::class.java)},owner,id).firstOrNull()
    private fun account(id:UUID,write:Boolean=false):UserAccountEntity {
        val user=entities.find(UserAccountEntity::class.java,id,LockModeType.PESSIMISTIC_WRITE)
        if(user==null||user.status!=AccountStatus.ACTIVE)throw CardioException(HttpStatus.UNAUTHORIZED,"AUTH_REQUIRED","다시 로그인해주세요.")
        if(write&&profiles.findFirstByUserIdOrderByRevisionDesc(id)?.healthConsentVersion!=CURRENT_POLICY_VERSION)
            throw CardioException(HttpStatus.FORBIDDEN,"PROFILE_REQUIRED","시작 정보와 건강정보 처리 동의를 먼저 확인해주세요.")
        return user
    }
    private fun version(old:Long?,expected:Long?) {
        if(old!=expected)throw CardioException(HttpStatus.CONFLICT,"VERSION_CONFLICT","다른 곳에서 기록이 바뀌었어요. 최신 기록을 불러와 확인해주세요.")
    }
    private fun date(user:UserAccountEntity,on:LocalDate){if(on<LocalDate.of(1900,1,1)||on>LocalDate.now(ZoneId.of(user.timeZone)))invalid()}
    private fun text(value:String?,max:Int):String? {if(value!=null&&(value.length>max||'\u0000' in value))invalid();return value?.trim()?.ifBlank { null }}
    private fun number(value:BigDecimal?,max:String) {if(value!=null&&(value.signum()<0||value>BigDecimal(max)||value.scale()>3))invalid()}
    private fun invalid():Nothing=throw CardioException(HttpStatus.BAD_REQUEST,"INVALID_REQUEST","날짜, 시간과 입력 범위를 확인해주세요. 칼로리를 적었다면 기기와 표시 기준도 선택해주세요.")
}

@RestController @RequestMapping("/api/v1/cardio")
class CardioController(private val service:CardioService) {
    @GetMapping fun list(@AuthenticationPrincipal user:AuthenticatedUser,@RequestParam from:LocalDate,@RequestParam to:LocalDate)=
        ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.list(user.userId,from,to))
    @PutMapping("/{id}") fun save(@AuthenticationPrincipal user:AuthenticatedUser,@PathVariable id:UUID,@RequestBody write:CardioWrite)=
        ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.save(user.userId,id,write))
    @DeleteMapping("/{id}") fun delete(@AuthenticationPrincipal user:AuthenticatedUser,@PathVariable id:UUID,@RequestParam version:Long):ResponseEntity<Void> {
        service.delete(user.userId,id,version);return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build()
    }
}
@RestControllerAdvice(assignableTypes=[CardioController::class])
class CardioErrors {
    @ExceptionHandler(CardioException::class) fun expected(e:CardioException)=ResponseEntity.status(e.status).cacheControl(CacheControl.noStore()).body(mapOf("code" to e.code,"message" to e.message))
    @ExceptionHandler(HttpMessageNotReadableException::class,MethodArgumentTypeMismatchException::class,MissingServletRequestParameterException::class)
    fun malformed()=ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(mapOf("code" to "INVALID_REQUEST","message" to "요청 형식과 필수 항목을 확인해주세요."))
}
