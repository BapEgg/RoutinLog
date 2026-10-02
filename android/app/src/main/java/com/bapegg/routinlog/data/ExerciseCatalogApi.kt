package com.bapegg.routinlog.data

import androidx.annotation.Keep
import retrofit2.Response
import retrofit2.http.*

@Keep data class CatalogExercise(val key:String,val name:String,val equipment:String,val target:String,val group:String,
    val recordType:String,val loadConvention:String,val aliases:List<String>,val recordingHint:String)
@Keep data class ExerciseCatalogDto(val revision:String,val sourceName:String,val sourceUrl:String,val items:List<CatalogExercise>)
interface ExerciseCatalogDataSource {
    suspend fun exerciseCatalog(owner:String):ExerciseCatalogDto
    suspend fun importCatalogExercise(owner:String,key:String):ExerciseDto
}
internal interface ExerciseCatalogApi {
    @GET("api/v1/workout-catalog") suspend fun exerciseCatalog(@Header("Authorization") auth:String):Response<ExerciseCatalogDto>
    @POST("api/v1/workout-catalog/{key}/save") suspend fun importCatalogExercise(@Header("Authorization") auth:String,@Path("key") key:String):Response<ExerciseDto>
}

/** Spacing and case do not force a user to know the catalog's exact spelling. */
fun CatalogExercise.matches(query:String,selectedGroup:String):Boolean {
    fun normalized(value:String)=value.filterNot(Char::isWhitespace).lowercase(java.util.Locale.ROOT)
    val needle=normalized(query)
    return (selectedGroup=="전체"||group==selectedGroup) &&
        (needle.isEmpty()||(listOf(name,equipment,target)+aliases).any { normalized(it).contains(needle) })
}
