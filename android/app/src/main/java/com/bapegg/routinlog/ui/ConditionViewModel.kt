package com.bapegg.routinlog.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bapegg.routinlog.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import java.time.ZoneId

val LocalConditions=staticCompositionLocalOf<ConditionViewModel?> { null }
data class ConditionDraft(val hours:String="",val minutes:String="",val values:ConditionValues=ConditionValues(),val version:Long?=null)
data class ConditionUiState(val userId:String?=null,val date:String=LocalDate.now().toString(),val records:List<ConditionDto> = emptyList(),
    val drafts:Map<String,ConditionDraft> = emptyMap(),val loading:Boolean=false,val loaded:Boolean=false,val busy:Boolean=false,
    val error:String?=null,val notice:String?=null) {
    val record get()=records.firstOrNull { it.date==date }
    val draft get()=drafts[date]
}
class ConditionViewModel(private val source:ConditionDataSource):ViewModel() {
    private val mutable=MutableStateFlow(ConditionUiState())
    val state=mutable.asStateFlow()
    private var zone=ZoneId.systemDefault()
    private var generation=0L
    private var readJob:Job?=null
    private var writeJob:Job?=null
    fun bind(id:String?,timeZone:String?) {
        val newZone=timeZone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()
        val changed=zone!=newZone; zone=newZone
        if(state.value.userId==id) { if(changed && id!=null) selectDate(LocalDate.now(zone).toString()); return }
        generation++;readJob?.cancel();writeJob?.cancel()
        mutable.value=ConditionUiState(userId=id,date=LocalDate.now(zone).toString())
        if(id!=null) refresh()
    }
    fun selectDate(value:String) {
        if(state.value.busy || state.value.userId==null) return
        val date=runCatching { LocalDate.parse(value) }.getOrNull()
        if(date==null||date !in LocalDate.of(1900,1,1)..LocalDate.now(zone)) { fail("1900년부터 오늘까지의 날짜를 선택해주세요.");return }
        if(value==state.value.date&&state.value.loaded) return
        mutable.update { it.copy(date=value,loaded=false,records=emptyList()) };refresh()
    }
    fun refresh(discardDraft:Boolean=false) {
        if(state.value.userId==null||state.value.busy)return
        val date=state.value.date
        if(discardDraft)mutable.update { it.copy(drafts=it.drafts-date) }
        readJob?.cancel();val epoch=++generation
        mutable.update { it.copy(loading=true,error=null) }
        readJob=viewModelScope.launch {
            try {
                val first=maxOf(LocalDate.of(1900,1,1),LocalDate.parse(date).minusDays(29))
                val records=source.listConditions(first.toString(),date)
                currentCoroutineContext().ensureActive()
                if(epoch==generation) mutable.update { it.copy(records=records,loaded=true,
                    drafts=if(date in it.drafts)it.drafts else it.drafts+(date to toDraft(records.firstOrNull { r->r.date==date }))) }
            }catch(cancelled:CancellationException){throw cancelled}
            catch(error:Exception){if(epoch==generation)fail(message(error))}
            finally { if(epoch==generation)mutable.update { it.copy(loading=false) } }
        }
    }
    fun edit(change:(ConditionDraft)->ConditionDraft) {
        if(!editable())return
        mutable.update { it.copy(drafts=it.drafts+(it.date to change(it.draft ?: ConditionDraft()))) }
    }
    fun save(onSaved:()->Unit={}) {
        if(!editable())return
        val current=state.value;val draft=current.draft ?: return
        val values=try { validate(draft) }catch(error:IllegalArgumentException){fail(error.message.orEmpty());return}
        write {
            val saved=source.saveCondition(current.date,ConditionWrite(values,draft.version));currentCoroutineContext().ensureActive()
            mutable.update { it.copy(records=(it.records.filterNot { r->r.date==saved.date }+saved).sortedByDescending { r->r.date },
                drafts=it.drafts+(saved.date to toDraft(saved)),notice="컨디션을 저장했어요.") }
            onSaved()
        }
    }
    fun delete(onDeleted:()->Unit={}) {
        if(!editable())return
        val current=state.value;val version=current.draft?.version ?: return
        write {
            source.deleteCondition(current.date,version);currentCoroutineContext().ensureActive()
            mutable.update { it.copy(records=it.records.filterNot { r->r.date==current.date },drafts=it.drafts+(current.date to ConditionDraft()),notice="이날의 컨디션 기록을 삭제했어요.") }
            onDeleted()
        }
    }
    private fun write(block:suspend()->Unit) {
        val epoch=generation;mutable.update { it.copy(busy=true,error=null) }
        writeJob=viewModelScope.launch {
            try { block() }catch(cancelled:CancellationException){throw cancelled}
            catch(error:Exception){if(epoch==generation)fail(message(error))}
            finally { if(epoch==generation)mutable.update { it.copy(busy=false) } }
        }
    }
    fun clearNotice()=mutable.update { it.copy(notice=null) }
    private fun editable()=state.value.userId!=null&&state.value.loaded&&!state.value.loading&&!state.value.busy
    private fun fail(message:String)=mutable.update { it.copy(error=message) }
    private fun message(error:Exception)=if(error is AccountException)error.userMessage else "요청을 완료하지 못했어요. 연결을 확인하고 다시 시도해주세요."
    private fun toDraft(record:ConditionDto?):ConditionDraft {
        val minutes=record?.values?.sleepMinutes
        return ConditionDraft(minutes?.div(60)?.toString().orEmpty(),minutes?.rem(60)?.toString().orEmpty(),record?.values ?: ConditionValues(),record?.version)
    }
    internal fun validate(draft:ConditionDraft):ConditionValues {
        fun whole(value:String,max:Int):Int { val n=if(value.isBlank())0 else value.toIntOrNull(); require(n!=null&&n in 0..max){"수면 시간은 0~24시간, 분은 0~59로 입력해주세요."};return n }
        val sleep=if(draft.hours.isBlank()&&draft.minutes.isBlank())null else whole(draft.hours,24)*60+whole(draft.minutes,59)
        require(sleep==null||sleep<=1440){"수면 시간은 하루 24시간 이내로 입력해주세요."}
        val v=draft.values
        require(v.fatigue==null||v.fatigue in setOf("LOW","MODERATE","HIGH"))
        require(v.stress==null||v.stress in setOf("LOW","MODERATE","HIGH"))
        require(v.soreness==null||v.soreness in setOf("NONE","MILD","HIGH"))
        require(v.activity==null||v.activity in setOf("LIGHT","MODERATE","HIGH"))
        require(v.memo.orEmpty().length<=1000&&'\u0000' !in v.memo.orEmpty()){"메모는 1,000자까지 입력해주세요."}
        require(v.sorenessArea.orEmpty().length<=80&&'\u0000' !in v.sorenessArea.orEmpty()){"근육통 부위는 80자까지 입력해주세요."}
        val result=v.copy(sleepMinutes=sleep,memo=v.memo?.trim()?.ifBlank { null },sorenessArea=if(v.soreness in setOf("MILD","HIGH"))v.sorenessArea?.trim()?.ifBlank { null } else null)
        require(result!=ConditionValues()){"기억하고 싶은 항목을 하나 이상 남겨주세요."}
        return result
    }
}
