package com.bapegg.routinlog.data

import androidx.annotation.Keep
import retrofit2.Response
import retrofit2.http.*
import java.math.BigDecimal

@Keep data class ReviewEvidence(val title:String,val url:String,val population:String,val finding:String,val limitation:String)
@Keep data class ReviewTarget(val setId:String,val weightKg:BigDecimal?=null,val reps:Int?=null,val durationSeconds:Int?=null)
@Keep data class WorkoutReviewDto(val week:String,val targetWeek:String,val targetDate:String?,val routineName:String?,val exercise:ExerciseSnapshot?,
    val planned:List<ReviewTarget>,val alternative:List<ReviewTarget>?,val suggestedChoice:String,val goal:String,val reason:String,
    val observations:List<String>,val limitations:List<String>,val evidence:List<ReviewEvidence>,val policyVersion:String,val status:String,val version:Long,
    val choice:String?=null,val chosen:List<ReviewTarget>?=null,val decisionReason:String?=null,val decidedAt:String?=null) {
    override fun toString()="WorkoutReviewDto(redacted)"
}
@Keep data class ReviewPrepare(val version:Long?=null)
@Keep data class ReviewDecision(val version:Long,val decision:String,val choice:String,val targets:List<ReviewTarget>,val reason:String?=null) {
    override fun toString()="ReviewDecision(redacted)"
}
@Keep data class ReviewHistory(val items:List<WorkoutReviewDto>)
interface WorkoutReviewDataSource {
    suspend fun getReview(owner:String,week:String):WorkoutReviewDto?
    suspend fun prepareReview(owner:String,week:String,write:ReviewPrepare):WorkoutReviewDto
    suspend fun decideReview(owner:String,week:String,write:ReviewDecision):WorkoutReviewDto
    suspend fun reviewHistory(owner:String):List<WorkoutReviewDto>
}
internal interface WorkoutReviewApi {
    @GET("api/v1/workout-reviews/{week}") suspend fun getReview(@Header("Authorization") auth:String,@Path("week") week:String):Response<WorkoutReviewDto>
    @POST("api/v1/workout-reviews/{week}/prepare") suspend fun prepareReview(@Header("Authorization") auth:String,@Path("week") week:String,@Body write:ReviewPrepare):Response<WorkoutReviewDto>
    @POST("api/v1/workout-reviews/{week}/decision") suspend fun decideReview(@Header("Authorization") auth:String,@Path("week") week:String,@Body write:ReviewDecision):Response<WorkoutReviewDto>
    @GET("api/v1/workout-reviews") suspend fun reviewHistory(@Header("Authorization") auth:String):Response<ReviewHistory>
}
