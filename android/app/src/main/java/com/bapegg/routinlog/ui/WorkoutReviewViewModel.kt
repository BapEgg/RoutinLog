package com.bapegg.routinlog.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bapegg.routinlog.data.*
import com.bapegg.routinlog.domain.WorkoutNumbers
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.math.BigDecimal

val LocalWorkoutReviews=staticCompositionLocalOf<WorkoutReviewViewModel?> { null }
data class ReviewSetDraft(val original:ReviewTarget,val weight:String,val reps:String,val seconds:String)
data class WorkoutReviewState(val owner:String?=null,val week:String?=null,val review:WorkoutReviewDto?=null,
    val choice:String="KEEP",val sets:List<ReviewSetDraft> = emptyList(),val reason:String="",val units:String="METRIC",
    val history:List<WorkoutReviewDto> = emptyList(),val loading:Boolean=false,val busy:Boolean=false,val needsReload:Boolean=false,val error:String?=null)
class WorkoutReviewViewModel(private val source:WorkoutReviewDataSource):ViewModel() {
    private val mutable=MutableStateFlow(WorkoutReviewState())
    val state=mutable.asStateFlow()
    private var generation=0L
    private var job:Job?=null
    fun bind(owner:String?,units:String) {
        if(state.value.owner==owner&&state.value.units==units)return
        generation++;job?.cancel();mutable.value=WorkoutReviewState(owner=owner,units=units)
    }
    fun open(week:String) {
        if(state.value.busy||state.value.owner==null)return
        if(state.value.week==week&&state.value.review!=null)return
        mutable.update { WorkoutReviewState(owner=it.owner,units=it.units,week=week) };reload()
    }
    fun reload() {
        val s=state.value;val owner=s.owner ?: return;val week=s.week ?: return
        run(false) {
            val review=source.getReview(owner,week);ensureCurrent()
            if(review!=null&&review.status=="DRAFT"&&review.version==s.review?.version)mutable.update { it.copy(review=review,needsReload=false) }
            else install(review)
        }
    }
    fun prepare(refresh:Boolean=false) {
        val s=state.value;val owner=s.owner ?: return;val week=s.week ?: return
        if(s.needsReload)return
        run(true) { val review=source.prepareReview(owner,week,ReviewPrepare(if(refresh)s.review?.version else null));ensureCurrent();install(review) }
    }
    fun choose(choice:String) {
        if(!editable())return
        val r=state.value.review ?: return
        val targets=when(choice){"KEEP"->r.planned;"LAST_PERFORMANCE"->r.alternative ?: return;else->return}
        mutable.update { it.copy(choice=choice,sets=targets.map { t->draft(t,it.units) },error=null) }
    }
    fun edit(index:Int,field:String,value:String) {
        if(!editable()||index !in state.value.sets.indices)return
        mutable.update { s->s.copy(choice="CUSTOM",sets=s.sets.mapIndexed { i,d->if(i!=index)d else when(field){"weight"->d.copy(weight=value);"reps"->d.copy(reps=value);"seconds"->d.copy(seconds=value);else->d} },error=null) }
    }
    fun reason(value:String) { if(editable())mutable.update { it.copy(reason=value,error=null) } }
    fun validate():Boolean=try { command("APPLY");true }catch(e:IllegalArgumentException){mutable.update { it.copy(error=e.message) };false}
    fun decide(action:String,onDone:()->Unit={}) {
        if(!editable())return
        val s=state.value;val owner=s.owner ?: return;val week=s.week ?: return
        val command=try { command(action) }catch(e:IllegalArgumentException){mutable.update { it.copy(error=e.message) };return}
        run(true) { val result=source.decideReview(owner,week,command);ensureCurrent();install(result);onDone() }
    }
    fun history() {
        val owner=state.value.owner ?: return
        run(false) { val records=source.reviewHistory(owner);ensureCurrent();mutable.update { it.copy(history=records) } }
    }
    private suspend fun ensureCurrent()=currentCoroutineContext().ensureActive()
    private fun editable()=state.value.owner!=null&&state.value.review?.status=="DRAFT"&&!state.value.loading&&!state.value.busy&&!state.value.needsReload
    private fun run(write:Boolean,block:suspend()->Unit) {
        if(state.value.owner==null||state.value.busy)return
        job?.cancel();val epoch=++generation
        mutable.update { it.copy(loading=!write,busy=write,error=null) }
        job=viewModelScope.launch {
            try { block() }catch(e:CancellationException){throw e}
            catch(e:Exception){if(epoch==generation)mutable.update { it.copy(error=if(e is AccountException)e.userMessage else "요청을 완료하지 못했어요. 연결을 확인해주세요.",needsReload=write||it.needsReload) }}
            finally { if(epoch==generation)mutable.update { it.copy(loading=false,busy=false) } }
        }
    }
    private fun install(review:WorkoutReviewDto?) {
        mutable.update { s->
            val choice=review?.choice ?: review?.suggestedChoice ?: "KEEP"
            val targets=review?.chosen ?: if(choice=="LAST_PERFORMANCE")review?.alternative else review?.planned
            s.copy(review=review,choice=choice,sets=targets.orEmpty().map { draft(it,s.units) },reason=review?.decisionReason.orEmpty(),needsReload=false,error=null)
        }
    }
    private fun draft(t:ReviewTarget,units:String)=ReviewSetDraft(t,weight(t.weightKg,units),t.reps?.toString().orEmpty(),t.durationSeconds?.toString().orEmpty())
    private fun weight(value:BigDecimal?,units:String)=value?.let { WorkoutNumbers.kgToDisplay(it,units).stripTrailingZeros().toPlainString() }.orEmpty()
    internal fun command(action:String):ReviewDecision {
        val s=state.value;val r=requireNotNull(s.review)
        require(s.reason.length<=1000&&'\u0000' !in s.reason){"이유는 1,000자 이내로 남겨주세요."}
        val targets=if(s.choice!="CUSTOM") { if(s.choice=="LAST_PERFORMANCE")requireNotNull(r.alternative) else r.planned }
        else s.sets.map { d->
            fun whole(value:String,max:Int)=value.toIntOrNull()?.takeIf { it in 1..max } ?: throw IllegalArgumentException("횟수·시간은 유효한 양의 정수로 입력해주세요.")
            val kg=if(r.exercise?.recordType=="WEIGHT_REPS") {
                if(d.weight==weight(d.original.weightKg,s.units))d.original.weightKg
                else {
                    val value=d.weight.toBigDecimalOrNull()
                    require(value!=null&&value.signum()>=0&&value.scale()<=3&&value<=BigDecimal("5000")){"중량은 소수점 세 자리 이내의 0 이상 값으로 입력해주세요."}
                    WorkoutNumbers.displayToKg(value,s.units).also { require(it<=BigDecimal("2000")){"중량의 입력 범위를 확인해주세요."} }
                }
            } else null
            ReviewTarget(d.original.setId,kg,if(r.exercise?.recordType in setOf("WEIGHT_REPS","REPS"))whole(d.reps,1000) else null,
                if(r.exercise?.recordType=="DURATION")whole(d.seconds,86400) else null)
        }
        return ReviewDecision(r.version,action,s.choice,targets,s.reason.trim().ifBlank { null })
    }
}
