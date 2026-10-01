package com.bapegg.routinlog.food

import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Semaphore

data class FoodUrlRequest(val url: String)
data class FoodUrlDraft(val name: String, val brand: String?, val basisGrams: BigDecimal?, val nutrition: NutritionValues,
    val sourceUrl: String, val sourceHost: String, val checkedAt: String, val excerpt: String, val warnings: List<String>)
data class FoodUrlResult(val draft: FoodUrlDraft? = null, val reasonCode: String? = null, val message: String? = null)

@Service
class FoodUrlPreview(private val fetcher: FoodPageFetcher, private val extractor: FoodPageNutrition) {
    private val slots = Semaphore(3)
    private val requests = linkedMapOf<UUID, MutableList<Long>>()
    private fun admit(owner: UUID): Boolean = synchronized(requests) {
        val now = System.currentTimeMillis()
        requests.entries.removeIf { entry -> entry.value.removeIf { now - it >= 60_000 }; entry.value.isEmpty() }
        if (owner !in requests && requests.size >= 10_000) return@synchronized false
        val recent = requests.getOrPut(owner) { mutableListOf() }
        if (recent.size >= 5) false else { recent += now; true }
    }
    fun preview(owner: UUID, url: String): FoodUrlResult {
        try {
            PublicFoodAddress.parse(url.trim())
            if (!admit(owner) || !slots.tryAcquire()) urlFail(FoodUrlFailure.TOO_MANY_REQUESTS)
            try { return FoodUrlResult(draft = extractor.extract(fetcher.fetch(url.trim()), Instant.now())) }
            finally { slots.release() }
        } catch (e: FoodUrlProblem) { return FoodUrlResult(reasonCode = e.reason.name, message = e.reason.message) }
    }
}
