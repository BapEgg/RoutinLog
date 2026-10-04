package com.bapegg.routinlog.ui

import androidx.lifecycle.viewModelScope
import com.bapegg.routinlog.data.*
import com.bapegg.routinlog.notifications.*
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import java.time.*

@OptIn(ExperimentalCoroutinesApi::class)
class FeatureViewModelTest {
    private val dispatcher=StandardTestDispatcher()
    private val source=Fake()
    private lateinit var model:FeatureViewModel
    @Before fun setup(){Dispatchers.setMain(dispatcher);model=FeatureViewModel(source)}
    @After fun cleanup(){model.viewModelScope.cancel();Dispatchers.resetMain()}
    @Test fun `late private preparation response cannot appear after account switch`()=runTest(dispatcher) {
        source.gate=CompletableDeferred();model.bind("a");model.preparation();runCurrent()
        model.bind("b");source.gate!!.complete(Unit);advanceUntilIdle()
        assertEquals("b",model.state.value.owner);assertNull(model.state.value.preparation)
    }
    @Test fun `analysis sends consent only after explicit ai request`()=runTest(dispatcher) {
        model.bind("a");model.analyze("2026-09-28");advanceUntilIdle()
        assertFalse(source.request!!.useAi);assertNull(source.request!!.consentVersion)
        model.analyze("2026-09-28",true);advanceUntilIdle()
        assertEquals("ai-summary-v1",source.request!!.consentVersion)
    }
    @Test fun `duplicate apply taps send only one mutation and callback`()=runTest(dispatcher) {
        model.bind("a");var applied=0
        val write=ProgramApply("retry-id","full-body-2",listOf(1,4))
        model.apply(write){applied++};model.apply(write){applied++};advanceUntilIdle()
        assertEquals(1,source.applies);assertEquals(1,applied)
    }
    @Test fun `daily and monday reminders do not duplicate or fire before selected time`() {
        val monday=ZonedDateTime.of(2026,10,5,9,0,0,0,ZoneId.of("Asia/Seoul"))
        val settings=ReminderSettings(true,true,21)
        assertEquals(listOf("weekly"),ReminderDue.kinds(settings,monday,null,null))
        assertTrue(ReminderDue.kinds(settings,monday,null,"2026-10-05").isEmpty())
        assertEquals(listOf("daily"),ReminderDue.kinds(settings,monday.withHour(21),null,"2026-10-05"))
        assertTrue(ReminderDue.kinds(settings,monday.withHour(21),"2026-10-05","2026-10-05").isEmpty())
        assertTrue(ReminderDue.kinds(ReminderSettings(),monday.withHour(23),null,null).isEmpty())
    }
    private class Fake:FeatureDataSource {
        var gate:CompletableDeferred<Unit>?=null
        var request:AnalysisWrite?=null
        var applies=0
        override suspend fun programs(owner:String)=ProgramCatalogDto("test",emptyList(),emptyList())
        override suspend fun applyProgram(owner:String,write:ProgramApply):ProgramApplied { applies++;return ProgramApplied(write.programId,emptyList(),WorkoutPlanDto(emptyList())) }
        override suspend fun preparation(owner:String):PreparationDto { gate?.let { withContext(NonCancellable){it.await()} };return PreparationDto(listOf(PreparationItem("id","private"))) }
        override suspend fun savePreparation(owner:String,write:PreparationDto)=write.copy(version=0)
        override suspend fun analyze(owner:String,write:AnalysisWrite):WeeklyAnalysis { request=write;return WeeklyAnalysis(write.week,"RULES","",emptyList(),emptyList(),null,emptyList()) }
        override suspend fun exportRecords(owner:String)=JsonObject()
        override suspend fun photo(owner:String,kind:String,id:String)=ThumbnailDto()
        override suspend fun savePhoto(owner:String,kind:String,id:String,write:ThumbnailDto)=write.copy(version=0)
    }
}
