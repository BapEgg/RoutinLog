package com.bapegg.routinlog.ui

import androidx.lifecycle.viewModelScope
import com.bapegg.routinlog.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutReviewViewModelTest {
    private val dispatcher=StandardTestDispatcher()
    private val source=Fake()
    private lateinit var model:WorkoutReviewViewModel
    @Before fun setup(){Dispatchers.setMain(dispatcher);model=WorkoutReviewViewModel(source)}
    @After fun cleanup(){model.viewModelScope.cancel();Dispatchers.resetMain()}
    private fun TestScope.open(units:String="METRIC") { model.bind("a",units);model.open("2026-09-21");advanceUntilIdle();model.prepare();advanceUntilIdle() }
    @Test fun `prepare preserves server alternative and selecting keep restores original plan`()=runTest(dispatcher) {
        open();assertEquals("LAST_PERFORMANCE",model.state.value.choice);assertEquals("80",model.state.value.sets.single().weight)
        model.choose("KEEP");model.decide("APPLY");advanceUntilIdle()
        assertEquals(BigDecimal("100"),source.command!!.targets.single().weightKg);assertEquals("APPLIED",model.state.value.review?.status)
    }
    @Test fun `imperial untouched weights keep original precision while edited pounds convert to kilograms`()=runTest(dispatcher) {
        open("IMPERIAL");model.edit(0,"reps","9")
        assertEquals(BigDecimal("80"),model.command("APPLY").targets.single().weightKg)
        model.edit(0,"weight","100");assertEquals(BigDecimal("45.359"),model.command("APPLY").targets.single().weightKg)
    }
    @Test fun `invalid custom values cannot reach decision endpoint`()=runTest(dispatcher) {
        open();model.edit(0,"reps","0");model.decide("APPLY");advanceUntilIdle();assertNull(source.command);assertNotNull(model.state.value.error)
        model.edit(0,"reps","8");model.edit(0,"weight","-1");assertFalse(model.validate())
        model.edit(0,"weight","80");model.reason("x".repeat(1001));assertFalse(model.validate())
    }
    @Test fun `uncertain write freezes changes until reload confirms the decision`()=runTest(dispatcher) {
        open();source.failAfterCommit=true;model.decide("APPLY");advanceUntilIdle()
        assertTrue(model.state.value.needsReload);val before=model.state.value.sets;model.edit(0,"weight","70");assertEquals(before,model.state.value.sets)
        model.reload();advanceUntilIdle();assertEquals("APPLIED",model.state.value.review?.status);assertFalse(model.state.value.needsReload)
        model.decide("APPLY");advanceUntilIdle();assertEquals(1,source.writes)
    }
    @Test fun `failed uncommitted write and reload retain custom edits and reason`()=runTest(dispatcher) {
        open();model.edit(0,"weight","75");model.reason("내 선택");source.failBeforeCommit=true
        model.decide("HOLD");advanceUntilIdle();model.reload();advanceUntilIdle()
        assertEquals("75",model.state.value.sets.single().weight);assertEquals("내 선택",model.state.value.reason)
        source.failBeforeCommit=false;model.decide("HOLD");advanceUntilIdle();assertEquals("HELD",model.state.value.review?.status)
    }
    @Test fun `sign out drops late writes and never navigates another account`()=runTest(dispatcher) {
        open();source.gate=CompletableDeferred();var called=false
        model.decide("APPLY"){called=true};runCurrent();model.bind(null,"METRIC");source.gate!!.complete(Unit);advanceUntilIdle()
        assertNull(model.state.value.review);assertTrue(model.state.value.sets.isEmpty());assertFalse(called);assertTrue(model.state.value.history.isEmpty())
    }
    @Test fun `double tap sends one write`()=runTest(dispatcher) {
        open();source.gate=CompletableDeferred();model.decide("APPLY");model.decide("APPLY");runCurrent()
        assertEquals(1,source.writes);source.gate!!.complete(Unit);advanceUntilIdle();assertEquals("APPLIED",model.state.value.review?.status)
    }
    private class Fake:WorkoutReviewDataSource {
        var stored:WorkoutReviewDto?=null;var command:ReviewDecision?=null;var writes=0
        var failAfterCommit=false;var failBeforeCommit=false;var gate:CompletableDeferred<Unit>?=null
        override suspend fun getReview(owner:String,week:String)=stored
        override suspend fun prepareReview(owner:String,week:String,write:ReviewPrepare):WorkoutReviewDto {
            val planned=listOf(ReviewTarget("set",BigDecimal("100"),10))
            return WorkoutReviewDto(week,"2026-09-28","2026-09-30","하체",ExerciseSnapshot("exercise","스쿼트","스미스","하체","WEIGHT_REPS","MACHINE"),
                planned,planned.map { it.copy(weightKg=BigDecimal("80"),reps=8) },"LAST_PERFORMANCE","GAIN","reason",emptyList(),emptyList(),emptyList(),"workout-review-1","DRAFT",0).also { stored=it }
        }
        override suspend fun decideReview(owner:String,week:String,write:ReviewDecision):WorkoutReviewDto {
            writes++;command=write;gate?.let { withContext(NonCancellable){it.await()} }
            if(failBeforeCommit)throw AccountException(AccountErrorKind.NETWORK,"offline")
            val result=stored!!.copy(status=if(write.decision=="HOLD")"HELD" else "APPLIED",version=1,choice=write.choice,chosen=write.targets,decisionReason=write.reason).also { stored=it }
            if(failAfterCommit)throw AccountException(AccountErrorKind.NETWORK,"offline")
            return result
        }
        override suspend fun reviewHistory(owner:String)=listOfNotNull(stored)
    }
}
