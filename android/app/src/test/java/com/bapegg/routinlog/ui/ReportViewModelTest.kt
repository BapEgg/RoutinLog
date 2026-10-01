package com.bapegg.routinlog.ui

import androidx.lifecycle.viewModelScope
import com.bapegg.routinlog.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class ReportViewModelTest {
    private val dispatcher=StandardTestDispatcher()
    private val source=Fake()
    private lateinit var model:ReportViewModel
    @Before fun setup(){Dispatchers.setMain(dispatcher);model=ReportViewModel(source)}
    @After fun cleanup(){model.viewModelScope.cancel();Dispatchers.resetMain()}
    @Test fun `binding does not fetch until report is opened and default week belongs to server`()=runTest(dispatcher) {
        model.bind("a","Asia/Seoul");advanceUntilIdle();assertTrue(source.calls.isEmpty())
        model.refresh();advanceUntilIdle();assertEquals(listOf("a" to null),source.calls);assertEquals("2026-09-21",model.state.value.week)
    }
    @Test fun `failed next week does not display previous week under a new date and retry preserves selection`()=runTest(dispatcher) {
        model.bind("a","UTC");model.refresh();advanceUntilIdle()
        source.failure=true;model.move(1);advanceUntilIdle()
        assertNull(model.state.value.report);assertEquals("2026-09-28",model.state.value.week);assertNotNull(model.state.value.error)
        source.failure=false;model.refresh();advanceUntilIdle();assertEquals("2026-09-28",model.state.value.report?.from)
    }
    @Test fun `sign out discards even an uncancellable late response`()=runTest(dispatcher) {
        model.bind("a","UTC");source.gate=CompletableDeferred();model.refresh();runCurrent()
        model.bind(null,null);source.gate!!.complete(Unit);advanceUntilIdle()
        assertNull(model.state.value.owner);assertNull(model.state.value.report);assertFalse(model.state.value.loading)
    }
    @Test fun `account or time zone changes clear private report and selected week`()=runTest(dispatcher) {
        model.bind("a","UTC");model.refresh();advanceUntilIdle();model.bind("b","UTC")
        assertNull(model.state.value.report);assertNull(model.state.value.week)
        model.refresh();advanceUntilIdle();assertEquals("b",source.calls.last().first)
        model.bind("b","Asia/Seoul");assertNull(model.state.value.report)
    }
    @Test fun `future weeks cannot be requested through navigation`()=runTest(dispatcher) {
        model.bind("a","UTC");model.select("2026-09-28");advanceUntilIdle()
        val calls=source.calls.size;model.move(1);advanceUntilIdle();assertEquals(calls,source.calls.size)
    }
    private class Fake:ReportDataSource {
        val calls=mutableListOf<Pair<String,String?>>()
        var failure=false;var gate:CompletableDeferred<Unit>?=null
        override suspend fun weeklyReport(owner:String,week:String?):WeeklyReport {
            calls+=owner to week;gate?.let { withContext(NonCancellable){it.await()} }
            if(failure)throw AccountException(AccountErrorKind.NETWORK,"offline")
            val average=ReportAverage(null,0,null,0,null)
            return WeeklyReport(week ?: "2026-09-21","2026-09-27","2026-09-27","2026-09-28","UTC","2026-09-30T00:00:00Z",
                emptyList(),emptyList(),emptyList(),emptyList(),average,average,emptyList(),emptyList())
        }
    }
}
