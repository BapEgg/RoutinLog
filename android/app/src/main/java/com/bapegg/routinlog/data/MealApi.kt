package com.bapegg.routinlog.data

import retrofit2.Response
import retrofit2.http.*

internal interface MealApi {
    @GET("api/v1/foods") suspend fun listFoods(@Header("Authorization") auth: String): Response<FoodListDto>
    @PUT("api/v1/foods/{id}") suspend fun saveFood(@Header("Authorization") auth: String, @Path("id") id: String, @Body food: FoodWrite): Response<FoodDto>
    @DELETE("api/v1/foods/{id}") suspend fun deleteFood(@Header("Authorization") auth: String, @Path("id") id: String, @Query("version") version: Long): Response<Unit>
    @GET("api/v1/meal-templates") suspend fun listMealTemplates(@Header("Authorization") auth: String): Response<MealTemplateListDto>
    @PUT("api/v1/meal-templates/{id}") suspend fun saveMealTemplate(@Header("Authorization") auth: String, @Path("id") id: String, @Body template: MealTemplateWrite): Response<MealTemplateDto>
    @DELETE("api/v1/meal-templates/{id}") suspend fun deleteMealTemplate(@Header("Authorization") auth: String, @Path("id") id: String, @Query("version") version: Long): Response<Unit>
    @GET("api/v1/meal-plan") suspend fun getMealPlan(@Header("Authorization") auth: String): Response<MealPlanDto>
    @PUT("api/v1/meal-plan") suspend fun saveMealPlan(@Header("Authorization") auth: String, @Body plan: MealPlanWrite): Response<MealPlanDto>
    @GET("api/v1/meal-records") suspend fun getMealDay(@Header("Authorization") auth: String, @Query("date") date: String): Response<MealDayDto>
    @PUT("api/v1/meal-records/{id}") suspend fun saveMeal(@Header("Authorization") auth: String, @Path("id") id: String, @Body meal: MealWrite): Response<MealDto>
    @DELETE("api/v1/meal-records/{id}") suspend fun deleteMeal(@Header("Authorization") auth: String, @Path("id") id: String, @Query("version") version: Long): Response<Unit>
    @DELETE("api/v1/meal-day-plans/{date}/{slotId}") suspend fun deleteMealDayPlan(@Header("Authorization") auth:String,@Path("date") date:String,@Path("slotId") slotId:String,@Query("version") version:Long):Response<Unit>
}
