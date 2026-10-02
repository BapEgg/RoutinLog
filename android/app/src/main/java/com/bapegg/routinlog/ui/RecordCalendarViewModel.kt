package com.bapegg.routinlog.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bapegg.routinlog.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.*

val LocalRecordCalendar = staticCompositionLocalOf<RecordCalendarViewModel?> { null }
data class RecordCalendarState(val owner: String? = null, val month: String? = null,
    val calendar: RecordCalendar? = null, val loading: Boolean = false, val error: String? = null)
class RecordCalendarViewModel(private val source: RecordCalendarDataSource) : ViewModel() {
    private val mutable = MutableStateFlow(RecordCalendarState())
    val state = mutable.asStateFlow()
    private var zone = ZoneId.systemDefault()
    private var generation = 0L
    private var job: Job? = null
    fun bind(owner: String?, timeZone: String?) {
        val nextZone = timeZone?.let { ZoneId.of(it) } ?: ZoneId.systemDefault()
        if (state.value.owner == owner && zone == nextZone) return
        zone = nextZone; generation++; job?.cancel(); mutable.value = RecordCalendarState(owner = owner)
    }
    fun select(month: String, refresh: Boolean = false) {
        val owner = state.value.owner ?: return
        val date = runCatching { LocalDate.parse(month) }.getOrNull()
        if (date == null || date.dayOfMonth != 1 || date < LocalDate.of(1900, 1, 1) || date > LocalDate.now(zone)) return
        if (!refresh && state.value.month == month && (state.value.loading || state.value.calendar != null)) return
        job?.cancel(); val epoch = ++generation
        mutable.value = RecordCalendarState(owner, month, loading = true)
        job = viewModelScope.launch {
            try {
                val result = source.recordCalendar(owner, month); currentCoroutineContext().ensureActive()
                if (epoch == generation) mutable.value = RecordCalendarState(owner, month, result)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { if (epoch == generation) mutable.update { it.copy(loading = false,
                error = (error as? AccountException)?.userMessage ?: "기록을 불러오지 못했어요. 연결을 확인하고 다시 시도해주세요.") } }
        }
    }
    fun refresh() { state.value.month?.let { select(it, refresh = true) } }
}
