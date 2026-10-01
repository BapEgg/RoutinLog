package com.bapegg.routinlog.review

import com.bapegg.routinlog.workout.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

data class ReviewEvidence(val title:String,val url:String,val population:String,val finding:String,val limitation:String)
data class ReviewTarget(val setId:String,val weightKg:BigDecimal?=null,val reps:Int?=null,val durationSeconds:Int?=null)
data class WorkoutReviewDto(
    val week:LocalDate,val targetWeek:LocalDate,val targetDate:LocalDate?,val routineName:String?,val exercise:ExerciseSnapshot?,
    val planned:List<ReviewTarget>,val alternative:List<ReviewTarget>?,val suggestedChoice:String,
    val goal:String,val reason:String,val observations:List<String>,val limitations:List<String>,val evidence:List<ReviewEvidence>,
    val policyVersion:String="workout-review-1",val status:String="DRAFT",val version:Long=0,
    val choice:String?=null,val chosen:List<ReviewTarget>?=null,val decisionReason:String?=null,val decidedAt:Instant?=null,
) { override fun toString()="WorkoutReviewDto(redacted)" }
data class ReviewPrepare(val version:Long?=null)
data class ReviewDecision(val version:Long,val decision:String,val choice:String,val targets:List<ReviewTarget>,val reason:String?=null) {
    override fun toString()="ReviewDecision(redacted)"
}
data class ReviewHistory(val items:List<WorkoutReviewDto>)
internal data class StoredReview(val view:WorkoutReviewDto,val signature:String,val targetDay:WorkoutDayDto?,val entryId:String?,val decisionFingerprint:String?=null)
