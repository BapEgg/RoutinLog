package com.bapegg.routinlog.review

import com.bapegg.routinlog.food.*
import com.bapegg.routinlog.report.ReportNutrient
import java.math.BigDecimal
import java.time.*

data class MealReviewOption(val id:String,val name:String,val items:List<LoggedMealItem>,val totals:NutritionTotals)
data class MealReviewAmount(val foodId:String,val grams:BigDecimal)
data class MealReviewDecision(val version:Long,val decision:String,val optionId:String?,val amounts:List<MealReviewAmount>,val reason:String?=null) {
    override fun toString()="MealReviewDecision(redacted)"
}
data class MealReviewDto(val week:LocalDate,val targetDate:LocalDate?,val slotId:String?,val slotLabel:String?,
    val target:NutritionValues?,val otherItems:List<LoggedMealItem>,val dayPlanComplete:Boolean,val options:List<MealReviewOption>,val suggestedOptionId:String?,
    val nutrition:List<ReportNutrient>,val observations:List<String>,val reason:String,val limitations:List<String>,val evidence:List<ReviewEvidence>,
    val policyVersion:String="meal-review-1",val status:String="DRAFT",val version:Long=0,val chosen:PlannedMeal?=null,
    val decisionReason:String?=null,val decidedAt:Instant?=null) { override fun toString()="MealReviewDto(redacted)" }
data class MealReviewHistory(val items:List<MealReviewDto>)
internal data class StoredMealReview(val view:MealReviewDto,val signature:String,val targetSignature:String,val fingerprint:String?=null)
