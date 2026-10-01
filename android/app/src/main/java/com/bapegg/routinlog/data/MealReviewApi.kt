package com.bapegg.routinlog.data

import androidx.annotation.Keep
import retrofit2.Response
import retrofit2.http.*
import java.math.BigDecimal

@Keep data class MealReviewOption(val id:String,val name:String,val items:List<LoggedMealItem>,val totals:NutritionTotals)
@Keep data class MealReviewAmount(val foodId:String,val grams:BigDecimal)
@Keep data class MealReviewDecision(val version:Long,val decision:String,val optionId:String?,val amounts:List<MealReviewAmount>,val reason:String?=null) {
    override fun toString()="MealReviewDecision(redacted)"
}
@Keep data class MealReviewDto(val week:String,val targetDate:String?,val slotId:String?,val slotLabel:String?,
    val target:NutritionValues?,val otherItems:List<LoggedMealItem>,val dayPlanComplete:Boolean,val options:List<MealReviewOption>,val suggestedOptionId:String?,
    val nutrition:List<ReportNutrient>,val observations:List<String>,val reason:String,val limitations:List<String>,val evidence:List<ReviewEvidence>,
    val policyVersion:String,val status:String,val version:Long,val chosen:PlannedMeal?=null,
    val decisionReason:String?=null,val decidedAt:String?=null) { override fun toString()="MealReviewDto(redacted)" }
@Keep data class MealReviewHistory(val items:List<MealReviewDto>)
interface MealReviewDataSource {
    suspend fun getMealReview(owner:String,week:String):MealReviewDto?
    suspend fun prepareMealReview(owner:String,week:String,write:ReviewPrepare):MealReviewDto
    suspend fun decideMealReview(owner:String,week:String,write:MealReviewDecision):MealReviewDto
    suspend fun mealReviewHistory(owner:String):List<MealReviewDto>
}
internal interface MealReviewApi {
    @GET("api/v1/meal-reviews/{week}") suspend fun getMealReview(@Header("Authorization") auth:String,@Path("week") week:String):Response<MealReviewDto>
    @POST("api/v1/meal-reviews/{week}/prepare") suspend fun prepareMealReview(@Header("Authorization") auth:String,@Path("week") week:String,@Body write:ReviewPrepare):Response<MealReviewDto>
    @POST("api/v1/meal-reviews/{week}/decision") suspend fun decideMealReview(@Header("Authorization") auth:String,@Path("week") week:String,@Body write:MealReviewDecision):Response<MealReviewDto>
    @GET("api/v1/meal-reviews") suspend fun mealReviewHistory(@Header("Authorization") auth:String):Response<MealReviewHistory>
}
