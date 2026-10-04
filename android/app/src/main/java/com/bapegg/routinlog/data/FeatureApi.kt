package com.bapegg.routinlog.data

import androidx.annotation.Keep
import retrofit2.Response
import retrofit2.http.*
import com.google.gson.JsonObject

@Keep data class Evidence(val id:String,val title:String,val authors:String,val url:String,val scope:String,val limitation:String)
@Keep data class ProgramMove(val key:String,val sets:Int,val reps:Int,val restSeconds:Int)
@Keep data class ProgramSession(val name:String,val moves:List<ProgramMove>)
@Keep data class TrainingProgram(val id:String,val name:String,val audience:String,val purpose:String,val sessions:List<ProgramSession>,val evidenceIds:List<String>,val author:String,val limitation:String)
@Keep data class ProgramCatalogDto(val revision:String,val programs:List<TrainingProgram>,val evidence:List<Evidence>)
@Keep data class ProgramApply(val requestId:String,val programId:String,val days:List<Int>,val expectedPlanVersion:Long?=null)
@Keep data class ProgramApplied(val programId:String,val routineIds:List<String>,val plan:WorkoutPlanDto)
@Keep data class PreparationItem(val id:String,val name:String,val seconds:Int?=null,val note:String?=null)
@Keep data class PreparationDto(val items:List<PreparationItem> = emptyList(),val version:Long?=null)
@Keep data class WeekTrend(val week:String,val foodDays:Int,val kcal:Double?,val proteinG:Double?,val weightKg:Double?,val waistCm:Double?,val workoutDays:Int,val stepsDays:Int,val steps:Long,
    val targetKcal:Double?,val targetDays:Int,val missingNutrientItems:Int,val weightMeasurements:Int,val waistMeasurements:Int)
@Keep data class AnalysisCandidate(val id:String,val title:String,val explanation:String,val route:String,val evidenceIds:List<String>)
@Keep data class WeeklyAnalysis(val week:String,val mode:String,val notice:String,val trends:List<WeekTrend>,val candidates:List<AnalysisCandidate>,val selectedId:String?,val evidence:List<Evidence>)
@Keep data class AnalysisWrite(val week:String,val useAi:Boolean=false,val consentVersion:String?=null)
@Keep data class ThumbnailDto(val jpegBase64:String?=null,val version:Long?=null)

interface FeatureDataSource {
    suspend fun programs(owner:String):ProgramCatalogDto
    suspend fun applyProgram(owner:String,write:ProgramApply):ProgramApplied
    suspend fun preparation(owner:String):PreparationDto
    suspend fun savePreparation(owner:String,write:PreparationDto):PreparationDto
    suspend fun analyze(owner:String,write:AnalysisWrite):WeeklyAnalysis
    suspend fun exportRecords(owner:String):JsonObject
    suspend fun photo(owner:String,kind:String,id:String):ThumbnailDto
    suspend fun savePhoto(owner:String,kind:String,id:String,write:ThumbnailDto):ThumbnailDto
}
internal interface FeatureApi {
    @GET("api/v1/features/photos/{kind}/{id}") suspend fun photo(@Header("Authorization") auth:String,@Path("kind") kind:String,@Path("id") id:String):Response<ThumbnailDto>
    @PUT("api/v1/features/photos/{kind}/{id}") suspend fun savePhoto(@Header("Authorization") auth:String,@Path("kind") kind:String,@Path("id") id:String,@Body write:ThumbnailDto):Response<ThumbnailDto>
    @GET("api/v1/features/programs") suspend fun programs(@Header("Authorization") auth:String):Response<ProgramCatalogDto>
    @POST("api/v1/features/programs/apply") suspend fun applyProgram(@Header("Authorization") auth:String,@Body write:ProgramApply):Response<ProgramApplied>
    @GET("api/v1/features/preparation") suspend fun preparation(@Header("Authorization") auth:String):Response<PreparationDto>
    @PUT("api/v1/features/preparation") suspend fun savePreparation(@Header("Authorization") auth:String,@Body write:PreparationDto):Response<PreparationDto>
    @POST("api/v1/features/analysis") suspend fun analyze(@Header("Authorization") auth:String,@Body write:AnalysisWrite):Response<WeeklyAnalysis>
    @GET("api/v1/features/export") suspend fun exportRecords(@Header("Authorization") auth:String):Response<JsonObject>
}
