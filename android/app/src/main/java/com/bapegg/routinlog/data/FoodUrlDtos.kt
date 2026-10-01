package com.bapegg.routinlog.data

import androidx.annotation.Keep
import java.math.BigDecimal

@Keep data class FoodUrlRequest(val url: String)
@Keep data class FoodUrlDraft(val name: String, val brand: String?, val basisGrams: BigDecimal?, val nutrition: NutritionValues,
    val sourceUrl: String, val sourceHost: String, val checkedAt: String, val excerpt: String, val warnings: List<String>)
@Keep data class FoodUrlResult(val draft: FoodUrlDraft? = null, val reasonCode: String? = null, val message: String? = null)
