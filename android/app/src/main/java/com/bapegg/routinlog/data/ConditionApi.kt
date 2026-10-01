package com.bapegg.routinlog.data

import androidx.annotation.Keep
import retrofit2.Response
import retrofit2.http.*

@Keep data class ConditionValues(val sleepMinutes:Int?=null,val fatigue:String?=null,val soreness:String?=null,
    val sorenessArea:String?=null,val stress:String?=null,val activity:String?=null,val memo:String?=null) {
    override fun toString()="ConditionValues(redacted)"
}
@Keep data class ConditionWrite(val values:ConditionValues,val version:Long?=null)
@Keep data class ConditionDto(val date:String,val values:ConditionValues,val version:Long)
@Keep data class ConditionList(val items:List<ConditionDto>)
interface ConditionDataSource {
    suspend fun listConditions(from:String,to:String):List<ConditionDto>
    suspend fun saveCondition(date:String,write:ConditionWrite):ConditionDto
    suspend fun deleteCondition(date:String,version:Long)
}
internal interface ConditionApi {
    @GET("api/v1/conditions") suspend fun listConditions(@Header("Authorization") auth:String,@Query("from") from:String,@Query("to") to:String):Response<ConditionList>
    @PUT("api/v1/conditions/{date}") suspend fun saveCondition(@Header("Authorization") auth:String,@Path("date") date:String,@Body write:ConditionWrite):Response<ConditionDto>
    @DELETE("api/v1/conditions/{date}") suspend fun deleteCondition(@Header("Authorization") auth:String,@Path("date") date:String,@Query("version") version:Long):Response<Unit>
}
