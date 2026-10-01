package com.bapegg.routinlog.workout

import com.bapegg.routinlog.account.persistence.AccountStatus
import com.bapegg.routinlog.account.persistence.UserAccountEntity
import com.bapegg.routinlog.profile.CURRENT_POLICY_VERSION
import com.bapegg.routinlog.profile.persistence.UserProfileRepository
import jakarta.persistence.EntityManager
import jakarta.persistence.LockModeType
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

/** A shared account-row lock serializes first creates, references, profile changes and deletion. */
@Service
@Transactional
class WorkoutService(private val jdbc:JdbcTemplate,private val entities:EntityManager,private val profiles:UserProfileRepository,private val json:ObjectMapper) {
    fun exercises(userId:UUID):ExerciseListDto {
        account(userId)
        return ExerciseListDto(jdbc.query("SELECT * FROM workout_exercises WHERE user_id=? ORDER BY name,id",exerciseMapper,userId))
    }
    fun putExercise(userId:UUID,id:UUID,request:ExerciseWrite):ExerciseDto {
        account(userId,true)
        val name=label(request.name); val equipment=text(request.equipment,80).orEmpty(); val target=text(request.target,80).orEmpty()
        val old=exercise(userId,id); version(old?.version,request.version)
        if(old==null) jdbc.update("INSERT INTO workout_exercises(user_id,id,name,equipment,target,record_type,load_convention) VALUES(?,?,?,?,?,?,?)",userId,id,name,equipment,target,request.recordType.name,request.loadConvention.name)
        else changed(jdbc.update("UPDATE workout_exercises SET name=?,equipment=?,target=?,record_type=?,load_convention=?,version=version+1 WHERE user_id=? AND id=? AND version=?",name,equipment,target,request.recordType.name,request.loadConvention.name,userId,id,old.version))
        return exercise(userId,id)!!
    }
    fun deleteExercise(userId:UUID,id:UUID,expectedVersion:Long) {
        account(userId,true); val old=exercise(userId,id) ?: missing("EXERCISE_NOT_FOUND"); version(old.version,expectedVersion)
        if(count("SELECT COUNT(*) FROM workout_routine_exercises WHERE user_id=? AND exercise_id=?",userId,id)>0) fail(HttpStatus.CONFLICT,"RESOURCE_IN_USE","저장한 루틴에서 먼저 이 운동을 빼주세요.")
        changed(jdbc.update("DELETE FROM workout_exercises WHERE user_id=? AND id=? AND version=?",userId,id,expectedVersion))
    }
    fun routines(userId:UUID):RoutineListDto {
        account(userId)
        return RoutineListDto(jdbc.query("SELECT payload FROM workout_routines WHERE user_id=? ORDER BY name,id",payload(RoutineDto::class.java),userId))
    }
    fun putRoutine(userId:UUID,id:UUID,request:RoutineWrite):RoutineDto {
        account(userId,true); val old=routine(userId,id); version(old?.version,request.version)
        if(request.entries.size !in 1..40) invalid()
        val entries=request.entries.map { entry ->
            val definition=exercise(userId,workoutUuid(entry.exerciseId)) ?: missing("EXERCISE_NOT_FOUND")
            if(entry.sets.size !in 1..30) invalid()
            val sets=entry.sets.map { set ->
                metrics(definition.recordType,set.weightKg,set.reps,set.durationSeconds,required=false)
                set.copy(id=workoutUuid(set.id).toString())
            }
            RoutineEntry(workoutUuid(entry.id).toString(),definition.id,sets,rest(entry.restSeconds),text(entry.note))
        }
        unique(entries.map { it.id }); unique(entries.flatMap { it.sets }.map { it.id })
        val result=RoutineDto(id.toString(),label(request.name),entries,text(request.note),(old?.version?.plus(1)) ?: 0)
        if(old==null) jdbc.update("INSERT INTO workout_routines(user_id,id,name,version,payload) VALUES(?,?,?,?,?)",userId,id,result.name,result.version,encode(result))
        else changed(jdbc.update("UPDATE workout_routines SET name=?,version=?,payload=? WHERE user_id=? AND id=? AND version=?",result.name,result.version,encode(result),userId,id,old.version))
        jdbc.update("DELETE FROM workout_routine_exercises WHERE user_id=? AND routine_id=?",userId,id)
        entries.map { it.exerciseId }.distinct().forEach { jdbc.update("INSERT INTO workout_routine_exercises(user_id,routine_id,exercise_id) VALUES(?,?,?)",userId,id,workoutUuid(it)) }
        return result
    }
    fun deleteRoutine(userId:UUID,id:UUID,expectedVersion:Long) {
        account(userId,true); val old=routine(userId,id) ?: missing("ROUTINE_NOT_FOUND"); version(old.version,expectedVersion)
        if(latestPlan(userId)?.slots?.any { it.workout?.routineId==id.toString() }==true) fail(HttpStatus.CONFLICT,"RESOURCE_IN_USE","요일 계획에서 먼저 이 루틴을 해제해주세요.")
        changed(jdbc.update("DELETE FROM workout_routines WHERE user_id=? AND id=? AND version=?",userId,id,expectedVersion))
    }
    fun plan(userId:UUID):WorkoutPlanDto { account(userId); return latestPlan(userId)?.dto() ?: WorkoutPlanDto((1..7).map { WorkoutPlanSlot(it) }) }
    fun putPlan(userId:UUID,request:WorkoutPlanWrite):WorkoutPlanDto {
        val user=account(userId,true); val old=latestPlan(userId); version(old?.version,request.version)
        if(request.slots.map { it.dayOfWeek }.sorted()!=(1..7).toList()) invalid()
        val slots=request.slots.sortedBy { it.dayOfWeek }.map { ResolvedPlanSlot(it.dayOfWeek,it.routineId?.let { id -> snapshot(userId,workoutUuid(id)) }) }
        val revision=PlanRevision(slots,old?.version?.plus(1) ?: 0,today(user))
        jdbc.update("INSERT INTO workout_plan_revisions(user_id,version,effective_from,payload) VALUES(?,?,?,?)",userId,revision.version,revision.effectiveFrom,encode(revision))
        return revision.dto()
    }
    fun putOverride(userId:UUID,date:LocalDate,request:WorkoutOverrideWrite):WorkoutOverrideDto {
        val user=account(userId,true); date(user,date,366)
        val old=override(userId,date); version(old?.version,request.version)
        val planned=request.routineId?.let { snapshot(userId,workoutUuid(it)) }
        val result=WorkoutOverrideDto(date,planned?.routineId,planned,text(request.note),old?.version?.plus(1) ?: 0)
        if(old==null) jdbc.update("INSERT INTO workout_overrides(user_id,workout_date,version,payload) VALUES(?,?,?,?)",userId,date,result.version,encode(result))
        else changed(jdbc.update("UPDATE workout_overrides SET version=?,payload=? WHERE user_id=? AND workout_date=? AND version=?",result.version,encode(result),userId,date,old.version))
        return result
    }
    fun deleteOverride(userId:UUID,date:LocalDate,expectedVersion:Long) {
        val user=account(userId,true); date(user,date,366)
        val old=override(userId,date) ?: missing("OVERRIDE_NOT_FOUND"); version(old.version,expectedVersion)
        changed(jdbc.update("DELETE FROM workout_overrides WHERE user_id=? AND workout_date=? AND version=?",userId,date,expectedVersion))
    }
    /** Only the review service supplies the trusted server snapshot; clients cannot replace exercise definitions. */
    internal fun applyReview(userId:UUID,expected:WorkoutDayDto,entryId:String,targets:List<PlannedSet>):WorkoutOverrideDto {
        val user=account(userId,true)
        if(expected.date<today(user))fail(HttpStatus.CONFLICT,"REVIEW_STALE","지난 날짜에는 초안을 적용할 수 없어요.")
        val current=days(userId,expected.date,expected.date).items.single()
        if(current!=expected || current.session!=null)fail(HttpStatus.CONFLICT,"REVIEW_STALE","운동 계획이나 기록이 바뀌었어요. 초안을 다시 확인해주세요.")
        val planned=current.planned ?: invalid()
        val entry=planned.entries.singleOrNull { it.id==entryId } ?: invalid()
        val editable=entry.sets.filterNot { it.warmup }
        if(targets.map { it.id }.sorted()!=editable.map { it.id }.sorted())invalid()
        targets.forEach { metrics(entry.exercise.recordType,it.weightKg,it.reps,it.durationSeconds,true) }
        val byId=targets.associateBy { it.id }
        val updated=planned.copy(entries=planned.entries.map { original->if(original.id!=entryId)original else original.copy(sets=original.sets.map { old->
            byId[old.id]?.let { old.copy(weightKg=it.weightKg,reps=it.reps,durationSeconds=it.durationSeconds) } ?: old
        }) })
        val old=current.override
        val result=WorkoutOverrideDto(current.date,updated.routineId,updated,old?.note,old?.version?.plus(1) ?: 0)
        if(old==null)jdbc.update("INSERT INTO workout_overrides(user_id,workout_date,version,payload) VALUES(?,?,?,?)",userId,current.date,result.version,encode(result))
        else changed(jdbc.update("UPDATE workout_overrides SET version=?,payload=? WHERE user_id=? AND workout_date=? AND version=?",result.version,encode(result),userId,current.date,old.version))
        return result
    }
    fun start(userId:UUID,id:UUID,request:WorkoutStartWrite):WorkoutSessionDto {
        val user=account(userId,true); date(user,request.date)
        session(userId,id)?.let { if(it.date!=request.date) invalid(); return it }
        if(count("SELECT COUNT(*) FROM workout_sessions WHERE user_id=? AND workout_date=?",userId,request.date)>0) fail(HttpStatus.CONFLICT,"SESSION_EXISTS","이 날짜에 시작한 운동이 있어요. 기존 기록을 열어주세요.")
        val planned=projection(userId,request.date,override(userId,request.date))
        val entries=planned?.entries.orEmpty().map { entry ->
            WorkoutEntryDto(UUID.randomUUID().toString(),entry.id,entry.exercise,entry.sets.map { ActualSet(UUID.randomUUID().toString(),it.id) },entry.restSeconds,entry.note)
        }
        val result=WorkoutSessionDto(id.toString(),request.date,planned,entries,WorkoutStatus.IN_PROGRESS,version=0,startedAt=Instant.now())
        jdbc.update("INSERT INTO workout_sessions(user_id,id,workout_date,version,payload) VALUES(?,?,?,?,?)",userId,id,result.date,result.version,encode(result))
        return result
    }
    fun putSession(userId:UUID,id:UUID,request:WorkoutSessionWrite):WorkoutSessionDto {
        account(userId,true); val old=session(userId,id) ?: missing("SESSION_NOT_FOUND"); version(old.version,request.version)
        if(request.entries.size>40) invalid()
        val previous=old.entries.associateBy { it.id }; val baseline=old.planned?.entries.orEmpty().associateBy { it.id }
        val entries=request.entries.map { entry ->
            val entryId=workoutUuid(entry.id).toString(); val exerciseId=workoutUuid(entry.exerciseId).toString(); val previousEntry=previous[entryId]
            val plannedId=entry.plannedEntryId?.let { workoutUuid(it).toString() }
            if(previousEntry!=null && previousEntry.plannedEntryId!=plannedId) invalid()
            val planned=plannedId?.let { baseline[it] ?: invalid() }
            if(previousEntry!=null && previousEntry.exercise.id!=exerciseId && previousEntry.sets.any { it.status==SetStatus.DONE }) fail(HttpStatus.CONFLICT,"REPLACEMENT_REQUIRES_NEW_ENTRY","완료한 세트는 원래 운동에 남기고, 대체 운동을 새 항목으로 추가해주세요.")
            val definition=if(previousEntry?.exercise?.id==exerciseId) previousEntry.exercise else (exercise(userId,workoutUuid(exerciseId)) ?: missing("EXERCISE_NOT_FOUND")).snapshot()
            val reason=text(entry.replacementReason)
            if(planned!=null && planned.exercise.id!=definition.id && reason==null) invalid()
            if(entry.sets.size !in 1..30) invalid()
            val sets=entry.sets.map { set ->
                val planSetId=set.planSetId?.let { workoutUuid(it).toString() }
                if(planSetId!=null && planned?.sets?.none { it.id==planSetId }!=false) invalid()
                if(set.status!=SetStatus.DONE && (set.weightKg!=null || set.reps!=null || set.durationSeconds!=null)) invalid()
                metrics(definition.recordType,set.weightKg,set.reps,set.durationSeconds,required=set.status==SetStatus.DONE)
                set.copy(id=workoutUuid(set.id).toString(),planSetId=planSetId,note=text(set.note))
            }
            WorkoutEntryDto(entryId,plannedId,definition,sets,rest(entry.restSeconds),text(entry.note),reason)
        }
        unique(entries.map { it.id }); unique(entries.flatMap { it.sets }.map { it.id })
        val finished=if(request.status==WorkoutStatus.COMPLETED) old.finishedAt ?: Instant.now() else null
        val result=old.copy(entries=entries,status=request.status,note=text(request.note),version=old.version+1,finishedAt=finished)
        changed(jdbc.update("UPDATE workout_sessions SET version=?,payload=? WHERE user_id=? AND id=? AND version=?",result.version,encode(result),userId,id,old.version))
        return result
    }
    fun deleteSession(userId:UUID,id:UUID,expectedVersion:Long) {
        account(userId,true); val old=session(userId,id) ?: missing("SESSION_NOT_FOUND"); version(old.version,expectedVersion)
        changed(jdbc.update("DELETE FROM workout_sessions WHERE user_id=? AND id=? AND version=?",userId,id,expectedVersion))
    }
    fun days(userId:UUID,from:LocalDate,to:LocalDate):WorkoutDaysDto {
        val user=account(userId); date(user,from,366); date(user,to,366)
        if(ChronoUnit.DAYS.between(from,to) !in 0..30) invalid()
        val sessions=jdbc.query("SELECT payload FROM workout_sessions WHERE user_id=? AND workout_date BETWEEN ? AND ?",payload(WorkoutSessionDto::class.java),userId,from,to).associateBy { it.date }
        return WorkoutDaysDto(from.datesUntil(to.plusDays(1)).map { day ->
            val override=override(userId,day)
            WorkoutDayDto(day,projection(userId,day,override),override,sessions[day])
        }.toList())
    }
    fun history(userId:UUID,exerciseId:UUID,before:LocalDate):WorkoutHistoryDto {
        val user=account(userId); date(user,before,366)
        val found=mutableListOf<WorkoutHistoryItem>(); var cursor=before
        // Keyset paging avoids loading years of unrelated sessions into memory.
        while(found.size<10) {
            val batch=jdbc.query("SELECT payload FROM workout_sessions WHERE user_id=? AND workout_date<? ORDER BY workout_date DESC LIMIT 100",payload(WorkoutSessionDto::class.java),userId,cursor)
            if(batch.isEmpty()) break
            batch.forEach { session -> session.entries.filter { it.exercise.id==exerciseId.toString() && it.sets.any { set -> set.status==SetStatus.DONE } }.forEach { found.add(WorkoutHistoryItem(session.date,session.id,it)) } }
            cursor=batch.last().date
        }
        return WorkoutHistoryDto(found.take(10))
    }
    private fun snapshot(userId:UUID,id:UUID):PlannedWorkout {
        val routine=routine(userId,id) ?: missing("ROUTINE_NOT_FOUND")
        val entries=routine.entries.map { entry ->
            val definition=exercise(userId,workoutUuid(entry.exerciseId)) ?: missing("EXERCISE_NOT_FOUND")
            // A library record-type edit cannot silently reinterpret incompatible saved targets.
            entry.sets.forEach { metrics(definition.recordType,it.weightKg,it.reps,it.durationSeconds,false) }
            PlannedExercise(entry.id,definition.snapshot(),entry.sets,entry.restSeconds,entry.note)
        }
        return PlannedWorkout(routine.id,routine.name,routine.version,entries)
    }
    private fun projection(userId:UUID,date:LocalDate,override:WorkoutOverrideDto?):PlannedWorkout? {
        if(override!=null) return override.workout
        val plan=jdbc.query("SELECT payload FROM workout_plan_revisions WHERE user_id=? AND effective_from<=? ORDER BY effective_from DESC,version DESC LIMIT 1",payload(PlanRevision::class.java),userId,date).firstOrNull()
        return plan?.slots?.firstOrNull { it.dayOfWeek==date.dayOfWeek.value }?.workout
    }
    private fun account(userId:UUID,writing:Boolean=false):UserAccountEntity {
        val user=entities.find(UserAccountEntity::class.java,userId,LockModeType.PESSIMISTIC_WRITE)
        if(user==null || user.status!=AccountStatus.ACTIVE) fail(HttpStatus.UNAUTHORIZED,"AUTHENTICATION_REQUIRED","다시 로그인해주세요.")
        if(writing && profiles.findFirstByUserIdOrderByRevisionDesc(userId)?.healthConsentVersion!=CURRENT_POLICY_VERSION) fail(HttpStatus.FORBIDDEN,"PROFILE_REQUIRED","시작 정보와 건강정보 처리 동의를 먼저 확인해주세요.")
        return user
    }
    private fun exercise(userId:UUID,id:UUID)=jdbc.query("SELECT * FROM workout_exercises WHERE user_id=? AND id=?",exerciseMapper,userId,id).firstOrNull()
    private fun routine(userId:UUID,id:UUID)=jdbc.query("SELECT payload FROM workout_routines WHERE user_id=? AND id=?",payload(RoutineDto::class.java),userId,id).firstOrNull()
    private fun latestPlan(userId:UUID)=jdbc.query("SELECT payload FROM workout_plan_revisions WHERE user_id=? ORDER BY version DESC LIMIT 1",payload(PlanRevision::class.java),userId).firstOrNull()
    private fun override(userId:UUID,date:LocalDate)=jdbc.query("SELECT payload FROM workout_overrides WHERE user_id=? AND workout_date=?",payload(WorkoutOverrideDto::class.java),userId,date).firstOrNull()
    private fun session(userId:UUID,id:UUID)=jdbc.query("SELECT payload FROM workout_sessions WHERE user_id=? AND id=?",payload(WorkoutSessionDto::class.java),userId,id).firstOrNull()
    private fun <T:Any> payload(type:Class<T>)=RowMapper { rs,_ -> json.readValue(rs.getString("payload"),type) }
    private fun encode(value:Any)=json.writeValueAsString(value)
    private fun count(sql:String,vararg args:Any)=jdbc.queryForObject(sql,Long::class.java,*args)!!
    private fun today(user:UserAccountEntity)=LocalDate.now(ZoneId.of(user.timeZone))
    private fun date(user:UserAccountEntity,value:LocalDate,futureDays:Long=0) { if(value<LocalDate.of(1900,1,1) || value>today(user).plusDays(futureDays)) invalid() }
    private fun version(old:Long?,request:Long?) { if(request!=null && request<0) invalid(); if(old!=request) fail(HttpStatus.CONFLICT,"VERSION_CONFLICT","기록이 변경됐어요. 최신 내용을 확인한 뒤 다시 시도해주세요.") }
    private fun changed(rows:Int) { if(rows!=1) fail(HttpStatus.CONFLICT,"VERSION_CONFLICT","기록이 변경됐어요. 새로 확인해주세요.") }
    private fun rest(seconds:Int)=seconds.also { if(it !in 0..1800) invalid() }
    private fun unique(ids:List<String>) { if(ids.distinct().size!=ids.size) invalid() }
    private fun label(value:String)=text(value,80)?.takeIf { it.isNotEmpty() } ?: invalid()
    private fun text(value:String?,max:Int=1000):String? { if(value!=null && (value.length>max || value.contains('\u0000'))) invalid(); return value?.trim()?.ifEmpty { null } }
    private fun metrics(type:RecordType,kg:BigDecimal?,reps:Int?,seconds:Int?,required:Boolean) {
        if(kg!=null && (kg.scale()>3 || kg.signum()<0 || kg>BigDecimal("2000"))) invalid()
        if(reps!=null && reps !in 1..1000) invalid()
        if(seconds!=null && seconds !in 1..86400) invalid()
        when(type) {
            RecordType.WEIGHT_REPS -> if(seconds!=null || (required && (kg==null || reps==null))) invalid()
            RecordType.REPS -> if(kg!=null || seconds!=null || (required && reps==null)) invalid()
            RecordType.DURATION -> if(kg!=null || reps!=null || (required && seconds==null)) invalid()
        }
    }
    private fun missing(code:String):Nothing=fail(HttpStatus.NOT_FOUND,code,"운동이나 기록을 찾을 수 없어요. 목록을 새로 확인해주세요.")
    private fun invalid():Nothing=fail(HttpStatus.BAD_REQUEST,"INVALID_REQUEST","운동 항목과 날짜, 입력한 값의 범위를 확인해주세요.")
    private fun fail(status:HttpStatus,code:String,message:String):Nothing=throw WorkoutApiException(status,code,message)
    private val exerciseMapper=RowMapper { rs,_ -> ExerciseDto(rs.getString("id"),rs.getString("name"),rs.getString("equipment"),rs.getString("target"),RecordType.valueOf(rs.getString("record_type")),LoadConvention.valueOf(rs.getString("load_convention")),rs.getLong("version")) }
}

internal fun workoutUuid(value:String):UUID=try {
    UUID.fromString(value).also { if(!it.toString().equals(value,ignoreCase=true)) throw IllegalArgumentException() }
} catch(_:IllegalArgumentException) { throw WorkoutApiException(HttpStatus.BAD_REQUEST,"INVALID_REQUEST","항목의 식별자를 확인해주세요.") }
