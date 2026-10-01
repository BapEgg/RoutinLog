package com.bapegg.routinlog.workout

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

enum class RecordType { WEIGHT_REPS, REPS, DURATION }
enum class LoadConvention { TOTAL, PER_HAND, MACHINE, EXTERNAL, BODYWEIGHT, UNSPECIFIED }
enum class SetStatus { PENDING, DONE, SKIPPED }
enum class WorkoutStatus { IN_PROGRESS, COMPLETED }
data class ExerciseWrite(val name:String,val equipment:String,val target:String,val recordType:RecordType,val loadConvention:LoadConvention,val version:Long?=null)
data class ExerciseDto(val id:String,val name:String,val equipment:String,val target:String,val recordType:RecordType,val loadConvention:LoadConvention,val version:Long) {
    fun snapshot()=ExerciseSnapshot(id,name,equipment,target,recordType,loadConvention)
}
data class ExerciseSnapshot(val id:String,val name:String,val equipment:String,val target:String,val recordType:RecordType,val loadConvention:LoadConvention)
data class ExerciseListDto(val items:List<ExerciseDto>)
data class PlannedSet(val id:String,val weightKg:BigDecimal?=null,val reps:Int?=null,val durationSeconds:Int?=null,val warmup:Boolean=false)
data class RoutineEntry(val id:String,val exerciseId:String,val sets:List<PlannedSet>,val restSeconds:Int=90,val note:String?=null)
data class RoutineWrite(val name:String,val entries:List<RoutineEntry>,val note:String?=null,val version:Long?=null)
data class RoutineDto(val id:String,val name:String,val entries:List<RoutineEntry>,val note:String?=null,val version:Long)
data class RoutineListDto(val items:List<RoutineDto>)
data class PlannedExercise(val id:String,val exercise:ExerciseSnapshot,val sets:List<PlannedSet>,val restSeconds:Int,val note:String?=null)
data class PlannedWorkout(val routineId:String,val routineName:String,val routineVersion:Long,val entries:List<PlannedExercise>)
data class WorkoutPlanSlot(val dayOfWeek:Int,val routineId:String?=null)
data class WorkoutPlanWrite(val slots:List<WorkoutPlanSlot>,val version:Long?=null)
data class WorkoutPlanDto(val slots:List<WorkoutPlanSlot>,val version:Long?=null,val effectiveFrom:LocalDate?=null)
data class WorkoutOverrideWrite(val routineId:String?=null,val note:String?=null,val version:Long?=null)
data class WorkoutOverrideDto(val date:LocalDate,val routineId:String?=null,val workout:PlannedWorkout?=null,val note:String?=null,val version:Long)
data class ActualSet(val id:String,val planSetId:String?=null,val status:SetStatus=SetStatus.PENDING,val weightKg:BigDecimal?=null,val reps:Int?=null,val durationSeconds:Int?=null,val note:String?=null)
data class WorkoutEntryWrite(val id:String,val plannedEntryId:String?=null,val exerciseId:String,val sets:List<ActualSet>,val restSeconds:Int=90,val note:String?=null,val replacementReason:String?=null)
data class WorkoutEntryDto(val id:String,val plannedEntryId:String?=null,val exercise:ExerciseSnapshot,val sets:List<ActualSet>,val restSeconds:Int=90,val note:String?=null,val replacementReason:String?=null)
data class WorkoutStartWrite(val date:LocalDate)
data class WorkoutSessionWrite(val entries:List<WorkoutEntryWrite>,val status:WorkoutStatus=WorkoutStatus.IN_PROGRESS,val note:String?=null,val version:Long)
data class WorkoutSessionDto(val id:String,val date:LocalDate,val planned:PlannedWorkout?=null,val entries:List<WorkoutEntryDto>,val status:WorkoutStatus,val note:String?=null,val version:Long,val startedAt:Instant,val finishedAt:Instant?=null)
data class WorkoutDayDto(val date:LocalDate,val planned:PlannedWorkout?=null,val override:WorkoutOverrideDto?=null,val session:WorkoutSessionDto?=null)
data class WorkoutDaysDto(val items:List<WorkoutDayDto>)
data class WorkoutHistoryItem(val date:LocalDate,val sessionId:String,val entry:WorkoutEntryDto)
data class WorkoutHistoryDto(val items:List<WorkoutHistoryItem>)

/** Persistence-only payloads; all nested definitions are captured by the server. */
internal data class ResolvedPlanSlot(val dayOfWeek:Int,val workout:PlannedWorkout?=null)
internal data class PlanRevision(val slots:List<ResolvedPlanSlot>,val version:Long,val effectiveFrom:LocalDate) {
    fun dto()=WorkoutPlanDto(slots.map { WorkoutPlanSlot(it.dayOfWeek,it.workout?.routineId) },version,effectiveFrom)
}
