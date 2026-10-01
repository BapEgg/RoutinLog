package com.bapegg.routinlog.data

import androidx.annotation.Keep
import java.math.BigDecimal

/** Manually entered exercise labels; these are not reviewed coaching recommendations. */
@Keep data class ExerciseWrite(val name: String, val equipment: String, val target: String,
    val recordType: String, val loadConvention: String, val version: Long? = null)
@Keep data class ExerciseDto(val id: String, val name: String, val equipment: String, val target: String,
    val recordType: String, val loadConvention: String, val version: Long)
@Keep data class ExerciseSnapshot(val id: String, val name: String, val equipment: String, val target: String,
    val recordType: String, val loadConvention: String)
@Keep data class ExerciseListDto(val items: List<ExerciseDto>)

@Keep data class PlannedSet(val id: String, val weightKg: BigDecimal? = null, val reps: Int? = null,
    val durationSeconds: Int? = null, val warmup: Boolean = false)
@Keep data class RoutineEntry(val id: String, val exerciseId: String, val sets: List<PlannedSet>,
    val restSeconds: Int = 90, val note: String? = null)
@Keep data class RoutineWrite(val name: String, val entries: List<RoutineEntry>, val note: String? = null, val version: Long? = null) {
    override fun toString() = "RoutineWrite(redacted)"
}
@Keep data class RoutineDto(val id: String, val name: String, val entries: List<RoutineEntry>, val note: String? = null, val version: Long) {
    override fun toString() = "RoutineDto(redacted)"
}
@Keep data class RoutineListDto(val items: List<RoutineDto>)
@Keep data class PlannedExercise(val id: String, val exercise: ExerciseSnapshot, val sets: List<PlannedSet>,
    val restSeconds: Int, val note: String? = null)
/** Server-resolved historical snapshot. No write request trusts a client-supplied instance of this type. */
@Keep data class PlannedWorkout(val routineId: String, val routineName: String, val routineVersion: Long, val entries: List<PlannedExercise>)

@Keep data class WorkoutPlanSlot(val dayOfWeek: Int, val routineId: String? = null)
@Keep data class WorkoutPlanWrite(val slots: List<WorkoutPlanSlot>, val version: Long? = null)
@Keep data class WorkoutPlanDto(val slots: List<WorkoutPlanSlot>, val version: Long? = null, val effectiveFrom: String? = null)
@Keep data class WorkoutOverrideWrite(val routineId: String? = null, val note: String? = null, val version: Long? = null)
@Keep data class WorkoutOverrideDto(val date: String, val routineId: String? = null, val workout: PlannedWorkout? = null,
    val note: String? = null, val version: Long)

/** Weights are canonical kg; pending/skipped metrics stay null instead of being filled from the plan. */
@Keep data class ActualSet(val id: String, val planSetId: String? = null, val status: String = "PENDING",
    val weightKg: BigDecimal? = null, val reps: Int? = null, val durationSeconds: Int? = null, val note: String? = null)
@Keep data class WorkoutEntryWrite(val id: String, val plannedEntryId: String? = null, val exerciseId: String,
    val sets: List<ActualSet>, val restSeconds: Int = 90, val note: String? = null, val replacementReason: String? = null)
@Keep data class WorkoutEntryDto(val id: String, val plannedEntryId: String? = null, val exercise: ExerciseSnapshot,
    val sets: List<ActualSet>, val restSeconds: Int = 90, val note: String? = null, val replacementReason: String? = null)
@Keep data class WorkoutStartWrite(val date: String)
@Keep data class WorkoutSessionWrite(val entries: List<WorkoutEntryWrite>, val status: String = "IN_PROGRESS",
    val note: String? = null, val version: Long) {
    override fun toString() = "WorkoutSessionWrite(redacted)"
}
@Keep data class WorkoutSessionDto(val id: String, val date: String, val planned: PlannedWorkout? = null,
    val entries: List<WorkoutEntryDto>, val status: String, val note: String? = null, val version: Long,
    val startedAt: String, val finishedAt: String? = null) {
    override fun toString() = "WorkoutSessionDto(redacted)"
}
@Keep data class WorkoutDayDto(val date: String, val planned: PlannedWorkout? = null,
    val `override`: WorkoutOverrideDto? = null, val session: WorkoutSessionDto? = null)
@Keep data class WorkoutDaysDto(val items: List<WorkoutDayDto>)
@Keep data class WorkoutHistoryItem(val date: String, val sessionId: String, val entry: WorkoutEntryDto)
@Keep data class WorkoutHistoryDto(val items: List<WorkoutHistoryItem>)
