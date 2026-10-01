package com.bapegg.routinlog.steps

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
import java.sql.ResultSet
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.UUID

data class StepConnection(val id:UUID,val startedAt:Instant,val timeZone:String)
data class StepConnectionState(val active:StepConnection?,val serverTime:Instant)
data class ConnectSteps(val id:UUID)
data class StepObservation(val date:LocalDate,val from:Instant,val through:Instant,val steps:Long)
data class StepBatch(val items:List<StepObservation>)
data class StepDay(val date:LocalDate,val steps:Long,val from:Instant,val through:Instant,val timeZones:List<String>,val segments:Int)
data class StepDays(val items:List<StepDay>)
class StepException(val status:HttpStatus,val code:String,message:String):RuntimeException(message)

/** One active phone subscription per account. Each subscription covers disjoint time intervals.
 * Uploads replace a daily cumulative snapshot; retries never add the same steps twice.
 * An absent observation stays absent, and coverage is not a claim of all-day wear/completeness. */
@Service @Transactional
class StepsService(private val jdbc:JdbcTemplate,private val entities:EntityManager,private val profiles:UserProfileRepository) {
    private fun now()=Instant.now().truncatedTo(ChronoUnit.MILLIS)
    fun connection(userId:UUID):StepConnectionState { account(userId);return StepConnectionState(active(userId),now()) }
    fun connect(userId:UUID,id:UUID):StepConnectionState {
        val user=account(userId,true)
        val existing=active(userId)
        if(existing?.id==id)return StepConnectionState(existing,now())
        if(jdbc.queryForObject("SELECT count(*) FROM step_connections WHERE id=?",Long::class.java,id)!=0L) conflict()
        val time=now()
        jdbc.update("UPDATE step_connections SET ended_at=? WHERE user_id=? AND ended_at IS NULL",time.atOffset(ZoneOffset.UTC),userId)
        jdbc.update("INSERT INTO step_connections(id,user_id,started_at,time_zone) VALUES(?,?,?,?)",id,userId,time.atOffset(ZoneOffset.UTC),user.timeZone)
        return StepConnectionState(StepConnection(id,time,user.timeZone),time)
    }
    fun disconnect(userId:UUID,id:UUID) {
        account(userId)
        // Idempotent and owner-scoped: stopping an old device cannot disconnect a newer one.
        jdbc.update("UPDATE step_connections SET ended_at=? WHERE user_id=? AND id=? AND ended_at IS NULL",now().atOffset(ZoneOffset.UTC),userId,id)
    }
    fun save(userId:UUID,id:UUID,batch:StepBatch) {
        account(userId,true)
        val connection=active(userId)?.takeIf { it.id==id } ?: conflict()
        if(batch.items.size !in 1..10 || batch.items.map { it.date }.distinct().size!=batch.items.size)invalid()
        val current=now();val zone=ZoneId.of(connection.timeZone)
        for(item in batch.items) {
            if(item.date !in LocalDate.of(1900,1,1)..current.atZone(zone).toLocalDate())invalid()
            val dayStart=item.date.atStartOfDay(zone).toInstant()
            val dayEnd=item.date.plusDays(1).atStartOfDay(zone).toInstant()
            val expectedStart=maxOf(connection.startedAt,dayStart)
            if(item.steps !in 0..300000 || item.date<LocalDate.of(1900,1,1) || item.from!=expectedStart ||
                item.through<=item.from || item.through>minOf(current,dayEnd))invalid()
            val old=jdbc.query("SELECT * FROM step_observations WHERE connection_id=? AND recorded_on=?",{rs,_->observation(rs)},id,item.date).firstOrNull()
            if(old!=null && item.through<old.through)continue // delayed worker cannot replace newer data
            if(old!=null && item.through==old.through) { if(old!=item)conflict();continue }
            if(old==null) jdbc.update("INSERT INTO step_observations(connection_id,recorded_on,from_time,through_time,steps) VALUES(?,?,?,?,?)",id,item.date,item.from.atOffset(ZoneOffset.UTC),item.through.atOffset(ZoneOffset.UTC),item.steps)
            else jdbc.update("UPDATE step_observations SET through_time=?,steps=? WHERE connection_id=? AND recorded_on=?",item.through.atOffset(ZoneOffset.UTC),item.steps,id,item.date)
        }
    }
    fun days(userId:UUID,from:LocalDate,to:LocalDate):StepDays {
        val user=account(userId)
        if(from<LocalDate.of(1900,1,1)||to>LocalDate.now(ZoneId.of(user.timeZone)).plusDays(1)||ChronoUnit.DAYS.between(from,to) !in 0..365)invalid()
        val rows=jdbc.query("SELECT o.*,c.time_zone FROM step_observations o JOIN step_connections c ON o.connection_id=c.id WHERE c.user_id=? AND o.recorded_on BETWEEN ? AND ? ORDER BY o.recorded_on DESC",
            {rs,_->observation(rs) to rs.getString("time_zone")},userId,from,to)
        return StepDays(rows.groupBy { it.first.date }.map { (date,values)->StepDay(date,values.sumOf { it.first.steps },values.minOf { it.first.from },values.maxOf { it.first.through },values.map { it.second }.distinct(),values.size) })
    }
    private fun observation(rs:ResultSet)=StepObservation(rs.getDate("recorded_on").toLocalDate(),rs.getTimestamp("from_time").toInstant(),rs.getTimestamp("through_time").toInstant(),rs.getLong("steps"))
    private fun active(userId:UUID)=jdbc.query("SELECT * FROM step_connections WHERE user_id=? AND ended_at IS NULL",{rs,_->StepConnection(rs.getObject("id",UUID::class.java),rs.getTimestamp("started_at").toInstant(),rs.getString("time_zone"))},userId).singleOrNull()
    private fun account(id:UUID,write:Boolean=false):UserAccountEntity {
        val user=entities.find(UserAccountEntity::class.java,id,LockModeType.PESSIMISTIC_WRITE)
        if(user==null||user.status!=AccountStatus.ACTIVE)throw StepException(HttpStatus.UNAUTHORIZED,"AUTH_REQUIRED","다시 로그인해주세요.")
        if(write&&profiles.findFirstByUserIdOrderByRevisionDesc(id)?.healthConsentVersion!=CURRENT_POLICY_VERSION)
            throw StepException(HttpStatus.FORBIDDEN,"PROFILE_REQUIRED","시작 정보와 건강정보 처리 동의를 먼저 확인해주세요.")
        return user
    }
    private fun invalid():Nothing=throw StepException(HttpStatus.BAD_REQUEST,"INVALID_REQUEST","걸음 기록의 날짜와 수집 구간을 확인해주세요.")
    private fun conflict():Nothing=throw StepException(HttpStatus.CONFLICT,"STEP_CONNECTION_CHANGED","걸음 연결이 바뀌었어요. 이 휴대폰에서 다시 연결해주세요.")
}

@RestController @RequestMapping("/api/v1")
class StepsController(private val service:StepsService) {
    @GetMapping("/step-connection") fun connection(@AuthenticationPrincipal user:AuthenticatedUser)=ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.connection(user.userId))
    @PutMapping("/step-connection") fun connect(@AuthenticationPrincipal user:AuthenticatedUser,@RequestBody value:ConnectSteps)=ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.connect(user.userId,value.id))
    @DeleteMapping("/step-connections/{id}") fun disconnect(@AuthenticationPrincipal user:AuthenticatedUser,@PathVariable id:UUID):ResponseEntity<Void> {service.disconnect(user.userId,id);return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build()}
    @PutMapping("/step-connections/{id}/days") fun save(@AuthenticationPrincipal user:AuthenticatedUser,@PathVariable id:UUID,@RequestBody batch:StepBatch):ResponseEntity<Void> {service.save(user.userId,id,batch);return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build()}
    @GetMapping("/steps") fun days(@AuthenticationPrincipal user:AuthenticatedUser,@RequestParam from:LocalDate,@RequestParam to:LocalDate)=ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.days(user.userId,from,to))
}
@RestControllerAdvice(assignableTypes=[StepsController::class])
class StepsErrors {
    @ExceptionHandler(StepException::class) fun expected(e:StepException)=ResponseEntity.status(e.status).cacheControl(CacheControl.noStore()).body(mapOf("code" to e.code,"message" to e.message))
    @ExceptionHandler(HttpMessageNotReadableException::class,MethodArgumentTypeMismatchException::class,MissingServletRequestParameterException::class)
    fun malformed()=ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(mapOf("code" to "INVALID_REQUEST","message" to "요청 형식과 필수 항목을 확인해주세요."))
}
