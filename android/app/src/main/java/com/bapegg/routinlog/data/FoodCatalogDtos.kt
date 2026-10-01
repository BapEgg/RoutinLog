package com.bapegg.routinlog.data

import androidx.annotation.Keep
import java.math.BigDecimal

@Keep data class CatalogFood(val id:String,val name:String,val brand:String?,val category:String,
    val basisLabel:String,val basisAmount:BigDecimal?,val basisUnit:String,val nutrition:NutritionValues,
    val sourceName:String,val sourceUpdatedAt:String,val sourceUrl:String,val revision:String,
    val importBlockReason:String?=null,val nutrientNotes:Map<String,String> = emptyMap())
@Keep data class CatalogSearch(val items:List<CatalogFood>,val hasMore:Boolean,val available:Boolean,val page:Int)
@Keep data class CatalogSave(val revision:String,val preparation:String)
