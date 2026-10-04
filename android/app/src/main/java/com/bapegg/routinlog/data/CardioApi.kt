package com.bapegg.routinlog.data

import androidx.annotation.Keep
import java.math.BigDecimal
import retrofit2.Response
import retrofit2.http.*

@Keep data class CardioValues(val activity:String,val minutes:Int,val deviceKcal:BigDecimal?=null,
    val energyKind:String?=null,val deviceName:String?=null,val speedKmh:BigDecimal?=null,
    val inclinePercent:BigDecimal?=null,val distanceKm:BigDecimal?=null,val effort:Int?=null,
    val fatigue:String?=null,val memo:String?=null,val metCode:String?=null) { override fun toString()="CardioValues(redacted)" }
@Keep data class CardioWrite(val date:String,val values:CardioValues,val version:Long?=null)
@Keep data class CardioEstimate(val code:String,val label:String,val met:BigDecimal,val weightKg:BigDecimal,val weightSource:String,val totalKcal:BigDecimal,val activeKcal:BigDecimal,val method:String,val sourceUrl:String)
@Keep data class CardioDto(val id:String,val date:String,val values:CardioValues,val version:Long,val estimate:CardioEstimate?=null)
@Keep data class CardioList(val items:List<CardioDto>)
interface CardioDataSource {
    suspend fun listCardio(owner:String,from:String,to:String):List<CardioDto>
    suspend fun saveCardio(owner:String,id:String,write:CardioWrite):CardioDto
    suspend fun deleteCardio(owner:String,id:String,version:Long)
}
internal interface CardioApi {
    @GET("api/v1/cardio") suspend fun listCardio(@Header("Authorization") auth:String,@Query("from") from:String,@Query("to") to:String):Response<CardioList>
    @PUT("api/v1/cardio/{id}") suspend fun saveCardio(@Header("Authorization") auth:String,@Path("id") id:String,@Body write:CardioWrite):Response<CardioDto>
    @DELETE("api/v1/cardio/{id}") suspend fun deleteCardio(@Header("Authorization") auth:String,@Path("id") id:String,@Query("version") version:Long):Response<Unit>
}
