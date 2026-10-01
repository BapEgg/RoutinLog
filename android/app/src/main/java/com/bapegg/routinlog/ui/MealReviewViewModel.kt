package com.bapegg.routinlog.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bapegg.routinlog.data.*
import com.bapegg.routinlog.domain.NutritionMath
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.math.BigDecimal

val LocalMealReviews=staticCompositionLocalOf<MealReviewViewModel?> { null }
data class MealReviewState(val owner:String?=null,val week:String?=null,val review:MealReviewDto?=null,val optionId:String?=null,
    val grams:Map<String,String> = emptyMap(),val reason:String="",val history:List<MealReviewDto> = emptyList(),
    val loading:Boolean=false,val busy:Boolean=false,val needsReload:Boolean=false,val error:String?=null)
class MealReviewViewModel(private val source:MealReviewDataSource):ViewModel() {
    private val mutable=MutableStateFlow(MealReviewState());val state=mutable.asStateFlow()
    private var epoch=0L;private var job:Job?=null
    fun bind(owner:String?) { if(owner==state.value.owner)return;epoch++;job?.cancel();mutable.value=MealReviewState(owner=owner) }
    fun open(week:String) {
        if(state.value.owner==null||state.value.busy)return
        if(state.value.week==week&&state.value.review!=null)return
        mutable.update { MealReviewState(owner=it.owner,week=week) };reload()
    }
    fun reload() {
        val s=state.value;val owner=s.owner ?: return;val week=s.week ?: return
        run(false) {
            val review=source.getMealReview(owner,week);currentCoroutineContext().ensureActive()
            if(review?.status=="DRAFT"&&review.version==s.review?.version)mutable.update { it.copy(review=review,needsReload=false) } else install(review)
        }
    }
    fun prepare(refresh:Boolean=false) {
        val s=state.value;val owner=s.owner ?: return;val week=s.week ?: return
        if(s.needsReload)return
        run(true){val r=source.prepareMealReview(owner,week,ReviewPrepare(if(refresh)s.review?.version else null));currentCoroutineContext().ensureActive();install(r)}
    }
    fun choose(id:String) {
        if(!editable())return
        val option=state.value.review?.options?.firstOrNull { it.id==id } ?: return
        mutable.update { it.copy(optionId=id,grams=option.items.associate { i->i.foodId to text(i.grams) },error=null) }
    }
    fun grams(foodId:String,value:String) { if(editable()&&foodId in state.value.grams)mutable.update { it.copy(grams=it.grams+(foodId to value),error=null) } }
    fun reason(value:String) { if(editable())mutable.update { it.copy(reason=value,error=null) } }
    fun selectedItems():List<LoggedMealItem> {
        val s=state.value
        if(s.review?.status!="DRAFT")return s.review?.chosen?.items.orEmpty()
        return s.review.options.firstOrNull { it.id==s.optionId }?.items.orEmpty().map { it.copy(grams=amount(s.grams[it.foodId].orEmpty())) }
    }
    fun totals(items:List<LoggedMealItem>)=NutritionMath.totals(NutritionMath.loggedItems(items))
    fun validate():Boolean=try { command("APPLY");true }catch(e:IllegalArgumentException){mutable.update { it.copy(error=e.message) };false}
    internal fun command(decision:String):MealReviewDecision {
        val s=state.value;val review=requireNotNull(s.review)
        require(s.reason.length<=1000&&'\u0000' !in s.reason){"이유는 1,000자 이내로 남겨주세요."}
        val option=review.options.firstOrNull { it.id==s.optionId }
        require(option!=null||review.options.isEmpty()){"식단을 먼저 선택해주세요."}
        val amounts=option?.items.orEmpty().map { MealReviewAmount(it.foodId,requireNotNull(amount(s.grams[it.foodId].orEmpty())){"음식량은 0보다 큰 숫자로 소수 둘째 자리까지 입력해주세요."}) }
        return MealReviewDecision(review.version,decision,option?.id,amounts,s.reason.trim().ifBlank { null })
    }
    fun decide(decision:String,onDone:()->Unit={}) {
        if(!editable())return
        val s=state.value;val owner=s.owner ?: return;val week=s.week ?: return
        val write=try { command(decision) }catch(e:IllegalArgumentException){mutable.update { it.copy(error=e.message) };return}
        run(true){val r=source.decideMealReview(owner,week,write);currentCoroutineContext().ensureActive();install(r);onDone()}
    }
    fun history() { val owner=state.value.owner ?: return;run(false){val rows=source.mealReviewHistory(owner);currentCoroutineContext().ensureActive();mutable.update { it.copy(history=rows) }} }
    private fun editable()=state.value.owner!=null&&state.value.review?.status=="DRAFT"&&!state.value.loading&&!state.value.busy&&!state.value.needsReload
    private fun run(write:Boolean,block:suspend()->Unit) {
        if(state.value.owner==null||state.value.busy)return
        job?.cancel();val current=++epoch;mutable.update { it.copy(busy=write,loading=!write,error=null) }
        job=viewModelScope.launch {
            try { block() }catch(e:CancellationException){throw e}
            catch(e:Exception){if(current==epoch)mutable.update { it.copy(error=(e as? AccountException)?.userMessage ?: "요청을 완료하지 못했어요. 연결을 확인해주세요.",needsReload=write||it.needsReload) }}
            finally { if(current==epoch)mutable.update { it.copy(busy=false,loading=false) } }
        }
    }
    private fun install(r:MealReviewDto?) { mutable.update { it.copy(review=r,optionId=r?.suggestedOptionId,grams=(r?.chosen?.items ?: r?.options?.firstOrNull { o->o.id==r.suggestedOptionId }?.items).orEmpty().associate { i->i.foodId to text(i.grams) },reason=r?.decisionReason.orEmpty(),error=null,needsReload=false) } }
    private fun amount(value:String)=value.takeIf { it.length<=20 }?.toBigDecimalOrNull()?.takeIf { it.signum()>0&&it<=BigDecimal("100000")&&it.scale()<=2 }
    private fun text(value:BigDecimal?)=value?.stripTrailingZeros()?.toPlainString().orEmpty()
}
