package com.bapegg.routinlog.data

import retrofit2.Response
import retrofit2.http.*

internal interface WorkoutApi {
    @GET("api/v1/workout-exercises") suspend fun listExercises(@Header("Authorization") auth: String): Response<ExerciseListDto>
    @PUT("api/v1/workout-exercises/{id}") suspend fun saveExercise(@Header("Authorization") auth: String, @Path("id") id: String, @Body exercise: ExerciseWrite): Response<ExerciseDto>
    @DELETE("api/v1/workout-exercises/{id}") suspend fun deleteExercise(@Header("Authorization") auth: String, @Path("id") id: String, @Query("version") version: Long): Response<Unit>
    @GET("api/v1/workout-routines") suspend fun listRoutines(@Header("Authorization") auth: String): Response<RoutineListDto>
    @PUT("api/v1/workout-routines/{id}") suspend fun saveRoutine(@Header("Authorization") auth: String, @Path("id") id: String, @Body routine: RoutineWrite): Response<RoutineDto>
    @DELETE("api/v1/workout-routines/{id}") suspend fun deleteRoutine(@Header("Authorization") auth: String, @Path("id") id: String, @Query("version") version: Long): Response<Unit>
    @GET("api/v1/workout-plan") suspend fun getWorkoutPlan(@Header("Authorization") auth: String): Response<WorkoutPlanDto>
    @PUT("api/v1/workout-plan") suspend fun saveWorkoutPlan(@Header("Authorization") auth: String, @Body plan: WorkoutPlanWrite): Response<WorkoutPlanDto>
    @PUT("api/v1/workout-overrides/{date}") suspend fun saveWorkoutOverride(@Header("Authorization") auth: String, @Path("date") date: String, @Body change: WorkoutOverrideWrite): Response<WorkoutOverrideDto>
    @DELETE("api/v1/workout-overrides/{date}") suspend fun deleteWorkoutOverride(@Header("Authorization") auth: String, @Path("date") date: String, @Query("version") version: Long): Response<Unit>
    @GET("api/v1/workout-days") suspend fun getWorkoutDays(@Header("Authorization") auth: String, @Query("from") from: String, @Query("to") to: String): Response<WorkoutDaysDto>
    @PUT("api/v1/workout-sessions/{id}/start") suspend fun startWorkoutSession(@Header("Authorization") auth: String, @Path("id") id: String, @Body start: WorkoutStartWrite): Response<WorkoutSessionDto>
    @PUT("api/v1/workout-sessions/{id}") suspend fun saveWorkoutSession(@Header("Authorization") auth: String, @Path("id") id: String, @Body session: WorkoutSessionWrite): Response<WorkoutSessionDto>
    @DELETE("api/v1/workout-sessions/{id}") suspend fun deleteWorkoutSession(@Header("Authorization") auth: String, @Path("id") id: String, @Query("version") version: Long): Response<Unit>
    @GET("api/v1/workout-history") suspend fun getWorkoutHistory(@Header("Authorization") auth: String, @Query("exerciseId") exerciseId: String, @Query("before") before: String): Response<WorkoutHistoryDto>
}
