package com.bapegg.routinlog.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bapegg.routinlog.data.*
import com.bapegg.routinlog.steps.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.*

val LocalSteps=staticCompositionLocalOf<StepsViewModel?> { null }
data class StepsUiState(val owner:String?=null,val date:String=LocalDate.now().toString(),val days:List<StepDay> = emptyList(),
    val connected:Boolean=false,val availability:StepAvailability=StepAvailability.READY,val busy:Boolean=false,val error:String?=null,val loaded:Boolean=false)
class StepsViewModel(private val api:StepDataSource,private val engine:StepEngine):ViewModel() {
    private val mutable=MutableStateFlow(StepsUiState());val state=mutable.asStateFlow()
    private var job:Job?=null;private var generation=0L;private var zone=ZoneId.systemDefault()
    fun bind(owner:String?,timeZone:String?) {
        zone=timeZone?.let { ZoneId.of(it) } ?: ZoneId.systemDefault()
        if(state.value.owner==owner&&state.value.loaded)return
        job?.cancel();generation++
        mutable.value=StepsUiState(owner=owner,date=LocalDate.now(zone).toString())
        run { engine.bind(owner);if(owner!=null)syncAndLoad(owner) }
    }
    fun refresh(sync:Boolean=true) {
        val owner=state.value.owner ?: return
        run {
            if(sync)syncAndLoad(owner)else load(owner)
        }
    }
    fun connect() { val owner=state.value.owner ?: return;run { engine.connect(owner);syncAndLoad(owner) } }
    fun disconnect() { val owner=state.value.owner ?: return;run { engine.disconnect(owner);load(owner) } }
    fun permissionDenied(){mutable.update { it.copy(availability=engine.availability(),error="권한을 허용하지 않았어요. 식단과 운동 기록은 그대로 사용할 수 있어요.") }}
    fun selectDate(value:String) {
        val date=runCatching { LocalDate.parse(value) }.getOrNull() ?: return
        if(date !in LocalDate.of(1900,1,1)..LocalDate.now(zone)||state.value.date==value)return
        if(state.value.busy)return
        mutable.update { it.copy(date=value,days=emptyList(),loaded=false) };refresh(false)
    }
    private suspend fun syncAndLoad(owner:String) {
        var failure:Exception?=null
        try { engine.sync(owner) }catch(cancelled:CancellationException){throw cancelled}catch(e:Exception){failure=e}
        load(owner)
        failure?.let { throw it }
    }
    private suspend fun load(owner:String) {
        val date=LocalDate.parse(state.value.date)
        val first=maxOf(LocalDate.of(1900,1,1),date.minusDays(29))
        val days=api.listSteps(owner,first.toString(),date.toString())
        currentCoroutineContext().ensureActive()
        mutable.update { it.copy(days=days,loaded=true) }
    }
    private fun run(block:suspend()->Unit) {
        if(state.value.busy)return
        val epoch=generation
        mutable.update { it.copy(busy=true,error=null) }
        job=viewModelScope.launch {
            try{block()}
            catch(cancelled:CancellationException){throw cancelled}
            catch(error:Exception){if(epoch==generation)mutable.update { it.copy(error=when(error){is AccountException->error.userMessage;is IllegalStateException->error.message;else->"걸음 기록을 가져오지 못했어요. 연결과 권한을 확인한 후 다시 시도해주세요."}) }}
            finally{if(epoch==generation)mutable.update { it.copy(busy=false,connected=it.owner?.let(engine::connected)==true,availability=engine.availability()) }}
        }
    }
}
