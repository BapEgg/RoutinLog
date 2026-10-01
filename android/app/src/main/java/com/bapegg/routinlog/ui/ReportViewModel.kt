package com.bapegg.routinlog.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bapegg.routinlog.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.LocalDate

val LocalReports=staticCompositionLocalOf<ReportViewModel?> { null }
data class ReportUiState(val owner:String?=null,val week:String?=null,val report:WeeklyReport?=null,val loading:Boolean=false,val error:String?=null)
class ReportViewModel(private val source:ReportDataSource):ViewModel() {
    private val mutable=MutableStateFlow(ReportUiState())
    val state=mutable.asStateFlow()
    private var job:Job?=null
    private var generation=0L
    private var zone:String?=null
    fun bind(owner:String?,timeZone:String?) {
        if(state.value.owner==owner&&zone==timeZone)return
        zone=timeZone;generation++;job?.cancel();mutable.value=ReportUiState(owner=owner)
    }
    fun refresh()=load(state.value.week)
    fun select(week:String?)=load(week)
    fun move(weeks:Long) {
        val report=state.value.report ?: return
        val next=LocalDate.parse(report.from).plusWeeks(weeks)
        if(next<LocalDate.of(1900,1,8)||next>LocalDate.parse(report.latestWeek))return
        load(next.toString())
    }
    private fun load(week:String?) {
        val owner=state.value.owner ?: return
        job?.cancel();val epoch=++generation
        // Remove the old report immediately; failed requests must not label an old week as the newly selected one.
        mutable.value=ReportUiState(owner,week,loading=true)
        job=viewModelScope.launch {
            try {
                val report=source.weeklyReport(owner,week);currentCoroutineContext().ensureActive()
                if(epoch==generation)mutable.value=ReportUiState(owner,report.from,report)
            }catch(cancelled:CancellationException){throw cancelled}
            catch(error:Exception){if(epoch==generation)mutable.update { it.copy(loading=false,error=if(error is AccountException)error.userMessage else "리포트를 불러오지 못했어요. 연결을 확인해주세요.") }}
        }
    }
}
