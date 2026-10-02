package com.bapegg.routinlog.ui

import androidx.lifecycle.viewModelScope
import com.bapegg.routinlog.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class RecordCalendarViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val source = Fake()
    private lateinit var model: RecordCalendarViewModel
    @Before fun setup() { Dispatchers.setMain(dispatcher); model = RecordCalendarViewModel(source) }
    @After fun cleanup() { model.viewModelScope.cancel(); Dispatchers.resetMain() }
    @Test fun `fetching is lazy and month changes never relabel stale data`() = runTest(dispatcher) {
        model.bind("a","UTC"); advanceUntilIdle(); assertTrue(source.calls.isEmpty())
        model.select("2026-08-01"); advanceUntilIdle()
        model.select("2026-08-01"); advanceUntilIdle(); assertEquals(1,source.calls.size)
        source.failure = true; model.select("2026-09-01"); assertNull(model.state.value.calendar)
        advanceUntilIdle(); assertNotNull(model.state.value.error); assertEquals("2026-09-01",model.state.value.month)
        source.failure = false; model.refresh(); advanceUntilIdle(); assertEquals("2026-09-01",model.state.value.calendar?.month)
    }
    @Test fun `late response after logout or account switch is discarded`() = runTest(dispatcher) {
        model.bind("a","UTC"); source.gate = CompletableDeferred(); model.select("2026-08-01"); runCurrent()
        model.bind("b","UTC"); source.gate!!.complete(Unit); advanceUntilIdle()
        assertEquals("b",model.state.value.owner); assertNull(model.state.value.calendar)
        model.bind(null,null); assertNull(model.state.value.owner); assertFalse(model.state.value.loading)
    }
    @Test fun `timezone change clears records and invalid month never sends a request`() = runTest(dispatcher) {
        model.bind("a","UTC"); model.select("2026-08-01"); advanceUntilIdle()
        model.bind("a","Asia/Seoul"); assertNull(model.state.value.calendar)
        listOf("bad","2026-08-02","1899-12-01","9999-01-01").forEach { model.select(it) }
        advanceUntilIdle(); assertEquals(1,source.calls.size)
    }
    @Test fun `categories count recorded kinds not successes and zero steps are still known`() {
        val empty = RecordDay("2026-08-01",0,0,null,null,"IN_PROGRESS",null,0,0,3,false,false,null)
        assertEquals(0,empty.categories)
        assertEquals(3,empty.copy(mealsSkipped=1,workoutRecorded=true,steps=0L).categories)
    }
    private class Fake: RecordCalendarDataSource {
        val calls = mutableListOf<Pair<String,String>>()
        var failure = false; var gate: CompletableDeferred<Unit>? = null
        override suspend fun recordCalendar(owner: String, month: String): RecordCalendar {
            calls += owner to month; gate?.let { withContext(NonCancellable) { it.await() } }
            if (failure) throw AccountException(AccountErrorKind.NETWORK,"연결 실패")
            return RecordCalendar(month,"2026-10-02","UTC",emptyList())
        }
    }
}
