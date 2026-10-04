package com.bapegg.routinlog.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bapegg.routinlog.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

val LocalFeatures=staticCompositionLocalOf<FeatureViewModel?> { null }
data class FeatureState(val owner:String?=null,val catalog:ProgramCatalogDto?=null,val preparation:PreparationDto?=null,
    val analysis:WeeklyAnalysis?=null,val busy:Boolean=false,val error:String?=null,val notice:String?=null)
class FeatureViewModel(private val source:FeatureDataSource):ViewModel() {
    private val mutable=MutableStateFlow(FeatureState())
    val state=mutable.asStateFlow()
    private var epoch=0L
    private var job:Job?=null
    fun bind(owner:String?) {
        if(owner==state.value.owner)return
        epoch++;job?.cancel();mutable.value=FeatureState(owner=owner)
    }
    fun catalog(){if(state.value.catalog==null)run { owner->val value=source.programs(owner);ensureActive();mutable.update { it.copy(catalog=value) } }}
    fun preparation(){run { owner->val value=source.preparation(owner);ensureActive();mutable.update { it.copy(preparation=value) } }}
    fun apply(write:ProgramApply,done:()->Unit)=run { owner->source.applyProgram(owner,write);ensureActive();mutable.update { it.copy(notice="주간 루틴을 적용했어요.") };done() }
    fun save(write:PreparationDto)=run { owner->val value=source.savePreparation(owner,write);ensureActive();mutable.update { it.copy(preparation=value,notice="운동 준비 목록을 저장했어요.") } }
    fun analyze(week:String,ai:Boolean=false){
        if(state.value.busy)return
        mutable.update { it.copy(analysis=null) }
        run { owner->val value=source.analyze(owner,AnalysisWrite(week,ai,if(ai)"ai-summary-v1"else null));ensureActive();mutable.update { it.copy(analysis=value) } }
    }
    fun export(write:suspend (String)->Unit)=run { owner->
        val result=source.exportRecords(owner);ensureActive()
        withContext(Dispatchers.IO){ensureActive();write(result.toString())}
        ensureActive();mutable.update { it.copy(notice="선택한 위치에 기록 파일을 저장했어요.") }
    }
    fun clearNotice()=mutable.update { it.copy(notice=null) }
    suspend fun photo(kind:String,id:String):ThumbnailDto {
        val owner=state.value.owner ?: throw CancellationException()
        val generation=epoch
        val photo=source.photo(owner,kind,id)
        if(epoch!=generation)throw CancellationException()
        return photo
    }
    suspend fun savePhoto(kind:String,id:String,write:ThumbnailDto):ThumbnailDto {
        val owner=state.value.owner ?: throw CancellationException()
        val generation=epoch
        val photo=source.savePhoto(owner,kind,id,write)
        if(epoch!=generation)throw CancellationException()
        return photo
    }
    private fun run(action:suspend CoroutineScope.(String)->Unit) {
        val owner=state.value.owner ?: return
        if(state.value.busy)return
        val generation=epoch
        mutable.update { it.copy(busy=true,error=null) }
        job=viewModelScope.launch {
            try{action(owner)}catch(e:CancellationException){throw e}catch(e:Exception){if(generation==epoch)mutable.update { it.copy(error=if(e is AccountException)e.userMessage else "연결과 입력 내용을 확인하고 다시 시도해주세요.") }}
            finally{if(generation==epoch)mutable.update { it.copy(busy=false) }}
        }
    }
}
