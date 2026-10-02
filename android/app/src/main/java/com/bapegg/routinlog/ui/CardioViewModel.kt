package com.bapegg.routinlog.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bapegg.routinlog.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.math.BigDecimal
import java.time.*
import java.util.UUID

val LocalCardio=staticCompositionLocalOf<CardioViewModel?> { null }
data class CardioDraft(val id:String=UUID.randomUUID().toString(),val version:Long?=null,
    val activity:String="",val minutes:String="",val device:Boolean=false,val kcal:String="",
    val energyKind:String="UNKNOWN",val deviceName:String="",val speed:String="",val incline:String="",
    val distance:String="",val effort:Int?=null,val fatigue:String?=null,val memo:String="") {
    override fun toString()="CardioDraft(redacted)"
}
data class CardioUiState(val owner:String?=null,val date:String=LocalDate.now().toString(),val records:List<CardioDto> = emptyList(),
    val drafts:Map<String,CardioDraft> = emptyMap(),val loaded:Boolean=false,val loading:Boolean=false,val busy:Boolean=false,
    val error:String?=null,val notice:String?=null) { val draft get()=drafts[date] }
class CardioViewModel(private val source:CardioDataSource):ViewModel() {
    private val mutable=MutableStateFlow(CardioUiState())
    val state=mutable.asStateFlow()
    private var zone:ZoneId=ZoneId.systemDefault()
    private var epoch=0L
    private var read:Job?=null
    private var write:Job?=null
    fun bind(owner:String?,timeZone:String?) {
        val next=runCatching { ZoneId.of(timeZone ?: ZoneId.systemDefault().id) }.getOrDefault(ZoneId.systemDefault())
        if(owner==state.value.owner&&zone==next)return
        zone=next;epoch++;read?.cancel();write?.cancel()
        mutable.value=CardioUiState(owner=owner,date=LocalDate.now(zone).toString())
    }
    fun open(date:String) {
        if(state.value.owner==null||state.value.busy)return
        val parsed=runCatching { LocalDate.parse(date) }.getOrNull()
        if(parsed==null||parsed !in LocalDate.of(1900,1,1)..LocalDate.now(zone)){fail("1900년부터 오늘까지 선택해주세요.");return}
        if(date!=state.value.date)mutable.update { it.copy(date=date,records=emptyList(),loaded=false) }
        refresh()
    }
    fun refresh() {
        val s=state.value;val owner=s.owner ?: return
        if(s.busy)return
        read?.cancel();val generation=++epoch
        mutable.update { it.copy(loading=true,loaded=false,error=null,records=emptyList()) }
        read=viewModelScope.launch {
            try {
                val records=source.listCardio(owner,s.date,s.date);ensureActive()
                if(epoch==generation)mutable.update { it.copy(records=records,loaded=true) }
            }catch(e:CancellationException){throw e}catch(e:Exception){if(epoch==generation)fail(message(e))}
            finally{if(epoch==generation)mutable.update { it.copy(loading=false) }}
        }
    }
    fun begin(record:CardioDto?=null) {
        if(!editable()||state.value.draft!=null)return
        val draft=record?.let { r->val v=r.values;CardioDraft(r.id,r.version,v.activity,v.minutes.toString(),v.deviceKcal!=null,
            v.deviceKcal?.toPlainString().orEmpty(),v.energyKind ?: "UNKNOWN",v.deviceName.orEmpty(),v.speedKmh?.toPlainString().orEmpty(),
            v.inclinePercent?.toPlainString().orEmpty(),v.distanceKm?.toPlainString().orEmpty(),v.effort,v.fatigue,v.memo.orEmpty()) } ?: CardioDraft()
        mutable.update { it.copy(drafts=it.drafts+(it.date to draft),error=null) }
    }
    fun edit(change:(CardioDraft)->CardioDraft) { if(editable())mutable.update { s->s.draft?.let { s.copy(drafts=s.drafts+(s.date to change(it))) } ?: s } }
    fun discard() { if(!state.value.busy)mutable.update { it.copy(drafts=it.drafts-it.date,error=null) } }
    fun save() {
        if(!editable())return
        val s=state.value;val draft=s.draft ?: return
        val values=try{validate(draft)}catch(e:IllegalArgumentException){fail(e.message ?: "입력 내용을 확인해주세요.");return}
        mutate {
            val result=source.saveCardio(s.owner!!,draft.id,CardioWrite(s.date,values,draft.version));ensureActive()
            mutable.update { it.copy(records=it.records.filterNot { r->r.id==result.id }+result,drafts=it.drafts-s.date,notice="유산소 기록을 저장했어요.") }
        }
    }
    fun delete(record:CardioDto) {
        if(!editable())return
        val owner=state.value.owner!!
        mutate {
            source.deleteCardio(owner,record.id,record.version);ensureActive()
            mutable.update { it.copy(records=it.records.filterNot { r->r.id==record.id },notice="유산소 기록을 삭제했어요.") }
        }
    }
    private fun mutate(block:suspend CoroutineScope.()->Unit) {
        val generation=epoch;mutable.update { it.copy(busy=true,error=null) }
        write=viewModelScope.launch {
            try{block()}catch(e:CancellationException){throw e}catch(e:Exception){if(epoch==generation)fail(message(e))}
            finally{if(epoch==generation)mutable.update { it.copy(busy=false) }}
        }
    }
    fun clearNotice()=mutable.update { it.copy(notice=null) }
    private fun editable()=state.value.owner!=null&&state.value.loaded&&!state.value.loading&&!state.value.busy
    private fun fail(text:String)=mutable.update { it.copy(error=text) }
    private fun message(e:Exception)=if(e is AccountException)e.userMessage else "연결을 확인하고 다시 시도해주세요. 작성한 내용은 그대로 있어요."
    internal fun validate(d:CardioDraft):CardioValues {
        fun text(v:String,max:Int):String? {require(v.length<=max&&'\u0000' !in v){"이름은 80자, 메모는 1,000자까지 입력해주세요."};return v.trim().ifBlank { null }}
        fun number(v:String,max:String):BigDecimal? {
            if(v.isBlank())return null
            val n=v.toBigDecimalOrNull();require(n!=null&&n.signum()>=0&&n<=BigDecimal(max)&&n.scale()<=3){"수치는 0 이상, 소수점 세 자리까지 입력해주세요. 입력 범위도 확인해주세요."};return n
        }
        val name=text(d.activity,80);require(name!=null){"어떤 유산소를 했는지 입력해주세요."}
        val minutes=d.minutes.toIntOrNull();require(minutes!=null&&minutes in 1..1440){"운동 시간은 1~1,440분으로 입력해주세요."}
        val kcal=if(d.device)number(d.kcal,"100000") else null
        val device=if(d.device)text(d.deviceName,80) else null
        if(d.device)require(kcal!=null&&device!=null&&d.energyKind in setOf("ACTIVE","TOTAL","UNKNOWN")){"기기 이름과 표시된 칼로리를 입력해주세요."}
        require(d.effort==null||d.effort in 1..10)
        require(d.fatigue==null||d.fatigue in setOf("LOW","MODERATE","HIGH"))
        return CardioValues(name,minutes,kcal,if(d.device)d.energyKind else null,device,number(d.speed,"150"),number(d.incline,"100"),number(d.distance,"1500"),d.effort,d.fatigue,text(d.memo,1000))
    }
}
