package com.bapegg.routinlog.data

import androidx.annotation.Keep
import java.math.BigDecimal
import retrofit2.Response
import retrofit2.http.*

@Keep data class ReportNutrient(val key:String,val target:BigDecimal?,val targetDays:Int,val recorded:BigDecimal?,val knownItems:Int,val missingItems:Int)
@Keep data class ReportAverage(val value:BigDecimal?,val count:Int,val previous:BigDecimal?,val previousCount:Int,val change:BigDecimal?)
@Keep data class WeeklyReport(val from:String,val to:String,val weekEnd:String,val latestWeek:String,val timeZone:String,val generatedAt:String,
    val nutrition:List<ReportNutrient>,val mealDays:List<MealDayDto>,val workouts:List<WorkoutDayDto>,val body:List<BodyMeasurementDto>,
    val weight:ReportAverage,val waist:ReportAverage,val conditions:List<ConditionDto>,val steps:List<StepDay>) {
    override fun toString()="WeeklyReport(redacted)"
}
interface ReportDataSource { suspend fun weeklyReport(owner:String,week:String?):WeeklyReport }
internal interface ReportApi {
    @GET("api/v1/reports/weekly") suspend fun weeklyReport(@Header("Authorization") auth:String,@Query("week") week:String?):Response<WeeklyReport>
}
