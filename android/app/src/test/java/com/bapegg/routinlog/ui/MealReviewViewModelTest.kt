package com.bapegg.routinlog.ui

import androidx.lifecycle.viewModelScope
import com.bapegg.routinlog.data.*
import com.bapegg.routinlog.domain.NutritionMath
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class MealReviewViewModelTest {
    private val dispatcher=StandardTestDispatcher();private val source=Fake();private lateinit var model:MealReviewViewModel
    @Before fun setup(){Dispatchers.setMain(dispatcher);model=MealReviewViewModel(source)}
    @After fun cleanup(){model.viewModelScope.cancel();Dispatchers.resetMain()}
    private fun TestScope.open(){model.bind("a");model.open("2026-09-21");advanceUntilIdle();model.prepare();advanceUntilIdle()}
    @Test fun `editing grams recalculates original basis while missing nutrients stay unknown`()=runTest(dispatcher){
        open();model.grams("food","120")
        val total=model.totals(model.selectedItems());assertEquals(0,BigDecimal("240").compareTo(total.kcal.knownAmount));assertEquals(1,total.fiberG.missingItems)
        assertEquals(BigDecimal("120"),model.command("APPLY").amounts.single().grams)
    }
    @Test fun `invalid amount or reason does not write`()=runTest(dispatcher){
        open();model.grams("food","0");model.decide("APPLY");advanceUntilIdle();assertNull(source.command)
        model.grams("food","80.001");assertFalse(model.validate());model.grams("food","80");model.reason("x".repeat(1001));assertFalse(model.validate())
    }
    @Test fun `selection restores saved quantity and holds chosen meal with reason`()=runTest(dispatcher){
        open();model.grams("food","120");model.choose("KEEP");assertEquals("80",model.state.value.grams["food"])
        model.reason("재료 확인 후 선택");model.decide("HOLD");advanceUntilIdle();assertEquals("HELD",model.state.value.review?.status);assertEquals("재료 확인 후 선택",source.command?.reason)
    }
    @Test fun `unknown response freezes edits and reload confirms without duplicate write`()=runTest(dispatcher){
        open();source.failAfter=true;model.decide("APPLY");advanceUntilIdle();assertTrue(model.state.value.needsReload)
        model.grams("food","200");assertEquals("80",model.state.value.grams["food"])
        model.reload();advanceUntilIdle();assertEquals("APPLIED",model.state.value.review?.status);model.decide("APPLY");advanceUntilIdle();assertEquals(1,source.writes)
    }
    @Test fun `uncommitted failure and reload preserve quantity and reason`()=runTest(dispatcher){
        open();model.grams("food","120");model.reason("수정 이유");source.failBefore=true;model.decide("APPLY");advanceUntilIdle()
        model.reload();advanceUntilIdle();assertEquals("120",model.state.value.grams["food"]);assertEquals("수정 이유",model.state.value.reason)
    }
    @Test fun `late response after sign out cannot restore health data or navigate`()=runTest(dispatcher){
        open();source.gate=CompletableDeferred();var called=false;model.decide("APPLY"){called=true};runCurrent()
        model.bind(null);source.gate!!.complete(Unit);advanceUntilIdle();assertNull(model.state.value.review);assertTrue(model.state.value.grams.isEmpty());assertFalse(called)
    }
    @Test fun `double tap writes once`()=runTest(dispatcher){
        open();source.gate=CompletableDeferred();model.decide("APPLY");model.decide("APPLY");runCurrent();assertEquals(1,source.writes);source.gate!!.complete(Unit);advanceUntilIdle()
    }
    private class Fake:MealReviewDataSource {
        var saved:MealReviewDto?=null;var command:MealReviewDecision?=null;var writes=0;var failBefore=false;var failAfter=false;var gate:CompletableDeferred<Unit>?=null
        override suspend fun getMealReview(owner:String,week:String)=saved
        override suspend fun prepareMealReview(owner:String,week:String,write:ReviewPrepare):MealReviewDto {
            val item=LoggedMealItem("item","food","식품",basisGrams=BigDecimal("80"),nutrition=NutritionValues(kcal=BigDecimal("160")),preparation="AS_SOLD",grams=BigDecimal("80"))
            val option=MealReviewOption("KEEP","기본 식단",listOf(item),NutritionMath.totals(NutritionMath.loggedItems(listOf(item))))
            return MealReviewDto(week,"2026-09-30","slot","점심",NutritionValues(kcal=BigDecimal("2000")),emptyList(),true,listOf(option),"KEEP",emptyList(),emptyList(),"기록 비교",emptyList(),emptyList(),"meal-review-1","DRAFT",0).also { saved=it }
        }
        override suspend fun decideMealReview(owner:String,week:String,write:MealReviewDecision):MealReviewDto {
            writes++;command=write;gate?.let { withContext(NonCancellable){it.await()} };if(failBefore)throw AccountException(AccountErrorKind.NETWORK,"offline")
            val r=saved!!;val selected=r.options.single().items.map { it.copy(grams=write.amounts.single().grams) }
            val result=r.copy(status=if(write.decision=="HOLD")"HELD" else "APPLIED",version=1,chosen=PlannedMeal(r.slotId!!,r.slotLabel!!,"선택 식단",selected),decisionReason=write.reason).also { saved=it }
            if(failAfter)throw AccountException(AccountErrorKind.NETWORK,"offline");return result
        }
        override suspend fun mealReviewHistory(owner:String)=listOfNotNull(saved)
    }
}
