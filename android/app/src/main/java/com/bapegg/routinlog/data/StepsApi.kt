package com.bapegg.routinlog.data

import androidx.annotation.Keep
import retrofit2.Response
import retrofit2.http.*

@Keep data class StepConnection(val id:String,val startedAt:String,val timeZone:String)
@Keep data class StepConnectionState(val active:StepConnection?,val serverTime:String)
@Keep data class ConnectSteps(val id:String)
@Keep data class StepObservation(val date:String,val from:String,val through:String,val steps:Long)
@Keep data class StepBatch(val items:List<StepObservation>)
@Keep data class StepDay(val date:String,val steps:Long,val from:String,val through:String,val timeZones:List<String>,val segments:Int)
@Keep data class StepDays(val items:List<StepDay>)
interface StepDataSource {
    suspend fun stepConnection(owner:String):StepConnectionState
    suspend fun connectSteps(owner:String,id:String):StepConnectionState
    suspend fun disconnectSteps(owner:String,id:String)
    suspend fun saveSteps(owner:String,id:String,batch:StepBatch)
    suspend fun listSteps(owner:String,from:String,to:String):List<StepDay>
}
internal interface StepsApi {
    @GET("api/v1/step-connection") suspend fun stepConnection(@Header("Authorization") auth:String):Response<StepConnectionState>
    @PUT("api/v1/step-connection") suspend fun connectSteps(@Header("Authorization") auth:String,@Body value:ConnectSteps):Response<StepConnectionState>
    @DELETE("api/v1/step-connections/{id}") suspend fun disconnectSteps(@Header("Authorization") auth:String,@Path("id") id:String):Response<Unit>
    @PUT("api/v1/step-connections/{id}/days") suspend fun saveSteps(@Header("Authorization") auth:String,@Path("id") id:String,@Body value:StepBatch):Response<Unit>
    @GET("api/v1/steps") suspend fun listSteps(@Header("Authorization") auth:String,@Query("from") from:String,@Query("to") to:String):Response<StepDays>
}
