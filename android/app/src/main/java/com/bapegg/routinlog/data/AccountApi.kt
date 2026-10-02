package com.bapegg.routinlog.data

import retrofit2.Response
import retrofit2.http.*

internal interface AccountApi : MealApi, WorkoutApi, ConditionApi, StepsApi, ReportApi, WorkoutReviewApi, MealReviewApi, RecordCalendarApi {
    @POST("api/v1/auth/google/challenge") suspend fun challenge(): Response<GoogleChallengeDto>
    @POST("api/v1/auth/google") suspend fun login(@Body body: GoogleLoginDto): Response<AuthTokensDto>
    @POST("api/v1/auth/refresh") suspend fun refresh(@Body body: RefreshRequestDto): Response<AuthTokensDto>
    @POST("api/v1/auth/logout") suspend fun logout(@Header("Authorization") auth: String, @Body body: LogoutRequestDto): Response<Unit>
    @HTTP(method = "DELETE", path = "api/v1/me", hasBody = true)
    suspend fun deleteAccount(@Header("Authorization") auth: String, @Body body: GoogleLoginDto): Response<Unit>
    @GET("api/v1/me/profile") suspend fun getProfile(@Header("Authorization") auth: String): Response<ProfileDto>
    @PUT("api/v1/me/profile") suspend fun saveProfile(@Header("Authorization") auth: String, @Body body: ProfileDto): Response<ProfileDto>
    @GET("api/v1/body-measurements") suspend fun listBody(@Header("Authorization") auth: String, @Query("from") from: String?, @Query("to") to: String?): Response<BodyListDto>
    @PUT("api/v1/body-measurements/{date}") suspend fun saveBody(@Header("Authorization") auth: String, @Path("date") date: String, @Body body: BodyMeasurementWriteDto): Response<BodyMeasurementDto>
    @DELETE("api/v1/body-measurements/{date}") suspend fun deleteBody(@Header("Authorization") auth: String, @Path("date") date: String, @Query("version") version: Long): Response<Unit>
}
