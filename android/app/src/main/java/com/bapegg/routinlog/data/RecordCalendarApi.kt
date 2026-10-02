package com.bapegg.routinlog.data

import androidx.annotation.Keep
import java.math.BigDecimal
import retrofit2.Response
import retrofit2.http.*

@Keep data class RecordDay(val date: String, val mealsEaten: Int, val mealsSkipped: Int,
    val weightKg: BigDecimal?, val waistCm: BigDecimal?, val workoutStatus: String?, val workoutName: String?,
    val doneSets: Int, val skippedSets: Int, val pendingSets: Int, val workoutRecorded: Boolean,
    val conditionRecorded: Boolean, val steps: Long?) {
    val categories: Int get() = listOf(mealsEaten + mealsSkipped > 0, weightKg != null || waistCm != null,
        workoutRecorded, conditionRecorded, steps != null).count { it }
    override fun toString() = "RecordDay(redacted)"
}
@Keep data class RecordCalendar(val month: String, val today: String, val timeZone: String, val days: List<RecordDay>) {
    override fun toString() = "RecordCalendar(redacted)"
}
interface RecordCalendarDataSource { suspend fun recordCalendar(owner: String, month: String): RecordCalendar }
internal interface RecordCalendarApi {
    @GET("api/v1/record-calendar") suspend fun recordCalendar(@Header("Authorization") auth: String, @Query("month") month: String): Response<RecordCalendar>
}
