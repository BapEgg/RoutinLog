package com.bapegg.routinlog.features

import com.bapegg.routinlog.account.persistence.*
import com.bapegg.routinlog.profile.CURRENT_POLICY_VERSION
import com.bapegg.routinlog.profile.persistence.UserProfileRepository
import com.bapegg.routinlog.workout.*
import jakarta.persistence.EntityManager
import jakarta.persistence.LockModeType
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID

data class ProgramApply(val requestId:UUID,val programId:String,val days:List<Int>,val expectedPlanVersion:Long?=null)
data class ProgramApplied(val programId:String,val routineIds:List<String>,val plan:WorkoutPlanDto)
data class PreparationItem(val id:UUID,val name:String,val seconds:Int?=null,val note:String?=null)
data class PreparationDto(val items:List<PreparationItem> = emptyList(),val version:Long?=null)
class FeatureException(val status:HttpStatus,val code:String,message:String):RuntimeException(message)

@Service @Transactional
class FeatureService(private val jdbc:JdbcTemplate,private val entities:EntityManager,private val profiles:UserProfileRepository,
    private val workouts:WorkoutService,private val json:ObjectMapper) {
    fun account(id:UUID,write:Boolean=false):UserAccountEntity {
        val user=entities.find(UserAccountEntity::class.java,id,LockModeType.PESSIMISTIC_WRITE)
        if(user==null||user.status!=AccountStatus.ACTIVE)throw FeatureException(HttpStatus.UNAUTHORIZED,"AUTH_REQUIRED","다시 로그인해주세요.")
        if(write&&profiles.findFirstByUserIdOrderByRevisionDesc(id)?.healthConsentVersion!=CURRENT_POLICY_VERSION)
            throw FeatureException(HttpStatus.FORBIDDEN,"PROFILE_REQUIRED","시작 설정과 건강정보 동의를 먼저 확인해주세요.")
        return user
    }
    fun catalog(owner:UUID):ProgramCatalogDto { account(owner);return ProgramCatalog.dto() }
    fun apply(owner:UUID,request:ProgramApply):ProgramApplied {
        account(owner,true)
        val serialized=json.writeValueAsString(request)
        val old=jdbc.query("SELECT request_payload,result_payload FROM program_applications WHERE user_id=? AND request_id=?",
            {rs,_->rs.getString(1) to rs.getString(2)},owner,request.requestId).firstOrNull()
        if(old!=null) {
            if(old.first!=serialized)conflict()
            return json.readValue(old.second,ProgramApplied::class.java)
        }
        val program=ProgramCatalog.programs.find { it.id==request.programId } ?: invalid()
        if(request.days.size!=program.sessions.size||request.days.distinct().size!=request.days.size||request.days.any { it !in 1..7 })invalid()
        if(workouts.plan(owner).version!=request.expectedPlanVersion)conflict()
        val routines=program.sessions.map { session->
            val entries=session.moves.map { move->
                val exercise=workouts.importCatalog(owner,move.key)
                // Imported copies may have been edited; never reinterpret their record type.
                RoutineEntry(UUID.randomUUID().toString(),exercise.id,List(move.sets) {
                    PlannedSet(UUID.randomUUID().toString(),reps=move.reps.takeUnless { exercise.recordType==RecordType.DURATION },
                        durationSeconds=20.takeIf { exercise.recordType==RecordType.DURATION })
                },move.restSeconds,"시작용 횟수예요. 오늘 가능한 무게와 횟수로 수정하세요.")
            }
            workouts.putRoutine(owner,UUID.randomUUID(),RoutineWrite("${program.name} · ${session.name}",entries,
                "${program.author} · ${ProgramCatalog.dto().revision}. 중량은 직접 정해요. 피로·불편함에 따라 휴식과 일정을 조정할 수 있어요."))
        }
        val mapping=request.days.sorted().zip(routines).associate { it.first to it.second.id }
        val plan=workouts.putPlan(owner,WorkoutPlanWrite((1..7).map { WorkoutPlanSlot(it,mapping[it]) },request.expectedPlanVersion))
        val result=ProgramApplied(program.id,routines.map { it.id },plan)
        jdbc.update("INSERT INTO program_applications(user_id,request_id,request_payload,result_payload) VALUES(?,?,?,?)",owner,request.requestId,serialized,json.writeValueAsString(result))
        return result
    }
    fun preparation(owner:UUID):PreparationDto {
        account(owner)
        return jdbc.query("SELECT payload FROM workout_preparation WHERE user_id=?",{rs,_->json.readValue(rs.getString(1),PreparationDto::class.java)},owner).firstOrNull() ?: PreparationDto()
    }
    fun preparation(owner:UUID,write:PreparationDto):PreparationDto {
        account(owner,true)
        if(write.items.size>30||write.items.map { it.id }.distinct().size!=write.items.size||write.items.any {
            it.name.isBlank()||it.name.length>100||'\u0000' in it.name||it.seconds!=null&&it.seconds !in 1..3600||(it.note?.length ?: 0)>1000||it.note?.contains('\u0000')==true
        })invalid()
        val old=preparation(owner)
        if(old.version!=write.version)conflict()
        val result=PreparationDto(write.items.map { it.copy(name=it.name.trim(),note=it.note?.trim()?.ifBlank { null }) },(old.version ?: -1)+1)
        if(old.version==null)jdbc.update("INSERT INTO workout_preparation(user_id,version,payload) VALUES(?,?,?)",owner,result.version,json.writeValueAsString(result))
        else jdbc.update("UPDATE workout_preparation SET version=?,payload=? WHERE user_id=?",result.version,json.writeValueAsString(result),owner)
        return result
    }
    /** Full self-service snapshot, never includes credentials, external provider IDs or other users' rows. */
    fun export(owner:UUID):Map<String,Any> {
        account(owner)
        val tables=listOf("body_measurements","user_profile_revisions","foods","meal_templates","meal_template_items","meal_plans","meal_plan_slots","meal_records","meal_record_items",
            "workout_exercises","workout_routines","workout_plan_revisions","workout_overrides","workout_sessions","daily_conditions",
            "step_connections","cardio_records","workout_reviews","workout_review_events","meal_reviews","meal_review_events","meal_day_plans","workout_preparation","program_applications")
        val data=linkedMapOf<String,Any>()
        // Table names are a constant allow-list, never request input. JDBC rows preserve unknown nutrients as null.
        tables.forEach { table->
            if(jdbc.queryForObject("SELECT COUNT(*) FROM $table WHERE user_id=?",Long::class.java,owner)!!>50000)
                throw FeatureException(HttpStatus.PAYLOAD_TOO_LARGE,"EXPORT_LIMIT","기록이 많아 한 번에 내보낼 수 없어요. 기간별 내보내기를 지원팀에 요청해주세요.")
            data[table]=jdbc.queryForList("SELECT * FROM $table WHERE user_id=?",owner).map { row->row.filterKeys { it!="user_id" } }
        }
        data["step_observations"]=jdbc.queryForList("SELECT o.* FROM step_observations o JOIN step_connections c ON o.connection_id=c.id WHERE c.user_id=?",owner)
        listOf("food_thumbnails","exercise_thumbnails").forEach { table->
            data[table]=jdbc.query("SELECT resource_id,version,jpeg FROM $table WHERE user_id=?",{rs,_->mapOf("resourceId" to rs.getString(1),"version" to rs.getLong(2),"jpegBase64" to java.util.Base64.getEncoder().encodeToString(rs.getBytes(3)))},owner)
        }
        return mapOf("schemaVersion" to 1,"exportedAt" to Instant.now().toString(),"records" to data)
    }
    private fun invalid():Nothing=throw FeatureException(HttpStatus.BAD_REQUEST,"INVALID_REQUEST","프로그램, 요일과 입력 내용을 확인해주세요.")
    private fun conflict():Nothing=throw FeatureException(HttpStatus.CONFLICT,"VERSION_CONFLICT","다른 곳에서 계획이 바뀌었어요. 최신 내용을 확인하고 다시 적용해주세요.")
}
