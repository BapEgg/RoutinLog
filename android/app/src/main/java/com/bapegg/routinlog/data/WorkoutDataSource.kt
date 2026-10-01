package com.bapegg.routinlog.data

/** Authenticated online workout records; existing AccountDataSource implementations stay unchanged. */
interface WorkoutDataSource {
    suspend fun listExercises(): List<ExerciseDto>
    suspend fun saveExercise(id: String, exercise: ExerciseWrite): ExerciseDto
    suspend fun deleteExercise(id: String, version: Long)
    suspend fun listRoutines(): List<RoutineDto>
    suspend fun saveRoutine(id: String, routine: RoutineWrite): RoutineDto
    suspend fun deleteRoutine(id: String, version: Long)
    suspend fun getWorkoutPlan(): WorkoutPlanDto
    suspend fun saveWorkoutPlan(plan: WorkoutPlanWrite): WorkoutPlanDto
    suspend fun saveWorkoutOverride(date: String, override: WorkoutOverrideWrite): WorkoutOverrideDto
    suspend fun deleteWorkoutOverride(date: String, version: Long)
    suspend fun getWorkoutDays(from: String, to: String): List<WorkoutDayDto>
    suspend fun startWorkoutSession(id: String, start: WorkoutStartWrite): WorkoutSessionDto
    suspend fun saveWorkoutSession(id: String, session: WorkoutSessionWrite): WorkoutSessionDto
    suspend fun deleteWorkoutSession(id: String, version: Long)
    suspend fun getWorkoutHistory(exerciseId: String, before: String): List<WorkoutHistoryItem>
}
