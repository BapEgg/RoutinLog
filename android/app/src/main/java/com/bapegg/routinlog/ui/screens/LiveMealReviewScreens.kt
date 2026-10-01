package com.bapegg.routinlog.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bapegg.routinlog.data.*
import com.bapegg.routinlog.ui.*
import com.bapegg.routinlog.ui.theme.DeepBlue
import java.math.BigDecimal

@Composable fun LiveMealReviewScreens(id:String,ui:PreviewSession,model:MealReviewViewModel) {
    val s by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(id,s.owner){if(id=="R16"&&s.owner!=null)model.history()}
    LaunchedEffect(id,s.review?.status){if(id=="R14"&&s.review?.status=="APPLIED")ui.go("R17");if(id=="R14"&&s.review?.status=="HELD")ui.go("R16")}
    if(s.loading){UiCard{Text("식사 기록과 계획을 확인하고 있어요.");LinearProgressIndicator(Modifier.fillMaxWidth(),color=DeepBlue)};return}
    s.error?.let { UiCard { Text(it,color=MaterialTheme.colorScheme.error);UiButton("식단 초안 상태 다시 확인",{if(id=="R16")model.history() else model.reload()},false,enabled=!s.busy) } }
    if(s.busy)UiCard { Text("선택을 저장하고 있어요.");LinearProgressIndicator(Modifier.fillMaxWidth(),color=DeepBlue) }
    if(id=="R16") {
        MutedText("최근 20개 식단 초안과 선택")
        if(s.history.isEmpty())UiCard { Text("아직 남긴 식단 선택이 없어요.") }
        s.history.forEach { row->UiCard {
            Badge(mealReviewStatus(row.status));Text("${row.week} 주 기록")
            Text("${row.targetDate ?: "날짜 미정"} · ${row.slotLabel ?: "끼니 미정"}")
            row.chosen?.let { Text(it.name) };row.decisionReason?.let { MutedText(it) }
            if(row.status=="APPLIED")MutedText("당시 적용한 선택이에요. 이후 계획을 해제하거나 실제 식사를 다르게 기록할 수 있어요.")
            UiButton("이 주의 식단 초안",{model.open(row.week);ui.go("R12")},false,enabled=!s.busy)
        } };return
    }
    val r=s.review
    if(r==null) {
        UiCard { SectionTitle("내 식단, 다음 한 끼부터");MutedText("기록한 영양과 현재 목표를 비교하고, 저장한 식단에서 다음 한 끼를 골라요.") }
        if(s.week!=null)UiButton("식단 초안 만들기",{model.prepare()},enabled=!s.busy&&!s.needsReload)
        else UiButton("리포트에서 주 선택",{ui.go("R01")})
        return
    }
    val editable=r.status=="DRAFT"&&!s.busy&&!s.needsReload
    val items=model.selectedItems()
    MutedText("${r.week} 주 기록 기준")
    if(id=="R15") {
        val uri=LocalUriHandler.current
        UiCard { SectionTitle("확인한 기록");r.observations.forEach { Text(it) };r.nutrition.forEach { n->
            val unit=if(n.key=="kcal")"kcal" else "g"
            KeyValue(mealNutrientName(n.key),"${mealNumber(n.recorded)} / ${mealNumber(n.target)} $unit")
            if(n.missingItems>0)MutedText("정보 없는 음식 ${n.missingItems}개 · 합계는 확인된 부분만 표시해요.")
        } }
        UiCard { SectionTitle("초안을 만드는 기준");Text(r.reason);r.limitations.forEach { MutedText(it) };MutedText("기록 비교 규칙 · ${r.policyVersion}") }
        r.evidence.forEach { e->UiCard { SectionTitle(e.title);Text(e.population);MutedText(e.finding);MutedText(e.limitation)
            UiButton("식단 근거 원문",{runCatching { uri.openUri(e.url) }.onFailure { ui.notify("링크를 열지 못했어요.") }},false)
        } };return
    }
    if(id=="R13") {
        MealScope(r)
        items.forEach { item->UiCard {
            SectionTitle(item.name);MealBasis(item)
            Input("음식량",s.grams[item.foodId].orEmpty(),{model.grams(item.foodId,it)},"g",numeric=true,enabled=editable)
        } }
        MealTotals("수정한 한 끼",model.totals(items))
        Input("바꾼 이유 (선택)",s.reason,model::reason,multiline=true,enabled=editable)
        UiButton("식단 수정 내용 확인",{if(model.validate())ui.go("R14")},enabled=editable)
        return
    }
    Badge(mealReviewStatus(r.status))
    UiCard { SectionTitle(if(r.status=="APPLIED")"이날 먹을 식사를 정했어요" else "다음 식사 후보");Text(r.reason) }
    if(r.targetDate!=null)MealScope(r)
    if(r.options.isEmpty()) {
        UiButton("기본 식단 정하기",{ui.go("F02")},false)
    } else {
        if(id=="R12"&&r.status=="DRAFT") {
            r.options.forEach { o->Choice(if(o.id=="KEEP")"기존 식단 유지 · ${o.name}" else o.name,
                if(o.id==r.suggestedOptionId&&o.id!="KEEP")"기록 비교 후보 · 음식 ${o.items.size}개" else "저장한 구성 · 음식 ${o.items.size}개",s.optionId==o.id){model.choose(o.id)} }
        }
        val original=r.options.firstOrNull { it.id=="KEEP" }
        if(original!=null)MealTotals("기존 한 끼 · ${original.name}",original.totals)
        MealTotals("선택한 한 끼",model.totals(items))
        UiCard { items.forEach { item->Text("${item.name} · ${mealNumber(item.grams)} g");MealBasis(item) } }
        if(r.dayPlanComplete) {
            val totals=model.totals(r.otherItems+items)
            UiCard {
                SectionTitle("바꾼 뒤 하루 계획 / 현재 목표")
                mealTotalsRows(totals).forEach { (key,n)->KeyValue(mealNutrientName(key),"${mealKnown(n)} / ${mealNumber(mealGoal(r.target,key))} ${if(key=="kcal")"kcal" else "g"}") }
                MutedText("다른 끼니를 계획대로 먹는 경우예요. 실제 섭취량은 먹은 뒤 기록해요.")
            }
        } else MutedText("기본 식단을 정하지 않은 끼니가 있어 하루 전체 계획은 비교하지 않았어요.")
        if(r.status=="DRAFT") {
            Input("선택 이유 (선택)",s.reason,model::reason,multiline=true,enabled=editable)
            if(id=="R14") {
                MutedText("${r.targetDate} ${r.slotLabel} 계획에만 반영해요. 먹은 것으로 기록되지는 않아요.")
                UiButton("확인한 식단 적용",{model.decide("APPLY"){ui.go("R17")}},enabled=editable&&r.targetDate!=null)
            } else UiButton("식단 적용 전 확인",{if(model.validate())ui.go("R14")},enabled=editable)
            UiButton("음식량 직접 수정",{ui.go("R13")},false,enabled=editable)
        }
    }
    if(r.status=="DRAFT")UiButton("식단 변경은 보류",{model.decide("HOLD"){ui.go("R16")}},false,enabled=editable)
    r.decisionReason?.let { UiCard { MutedText("내가 남긴 이유");Text(it) } }
    UiButton("식단 기록과 근거",{ui.go("R15")},false,enabled=!s.busy)
    if(r.status!="APPLIED")UiButton("최신 기록으로 식단 초안 다시 만들기",{model.prepare(true)},false,enabled=!s.busy&&!s.needsReload)
    UiButton("지난 식단 선택",{ui.go("R16")},false,enabled=!s.busy)
    if(r.status=="APPLIED")UiButton("반영된 식사 계획 보기",{ui.set("mealReview.appliedDate",r.targetDate.orEmpty());ui.go("F01")},false)
}
@Composable private fun MealScope(r:MealReviewDto) { UiCard { KeyValue("적용 날짜",r.targetDate ?: "미정");KeyValue("변경할 끼니",r.slotLabel ?: "미정") } }
@Composable private fun MealBasis(i:LoggedMealItem) { MutedText("${when(i.preparation){"RAW"->"조리 전";"COOKED"->"조리 후";"AS_SOLD"->"판매 상태";else->"상태 미확인"}} · 기준 ${mealNumber(i.basisGrams)} g · ${i.sourceNote ?: "직접 등록한 영양정보"}") }
@Composable private fun MealTotals(title:String,n:NutritionTotals) { UiCard { SectionTitle(title);mealTotalsRows(n).forEach { (key,v)->
    val value=mealKnown(v)
    KeyValue(mealNutrientName(key),if(v.missingItems>0&&v.knownItems==0)value else "$value ${if(key=="kcal")"kcal" else "g"}")
} } }
private fun mealTotalsRows(n:NutritionTotals)=listOf("kcal" to n.kcal,"carbsG" to n.carbsG,"proteinG" to n.proteinG,"fatG" to n.fatG,"fiberG" to n.fiberG)
private fun mealGoal(n:NutritionValues?,key:String)=when(key){"kcal"->n?.kcal;"carbsG"->n?.carbsG;"proteinG"->n?.proteinG;"fatG"->n?.fatG;else->n?.fiberG}
private fun mealKnown(n:NutrientTotal)=if(n.missingItems>0)if(n.knownItems==0)"정보 없음" else "${mealNumber(n.knownAmount)} (일부)" else mealNumber(n.knownAmount)
private fun mealNumber(n:BigDecimal?)=n?.stripTrailingZeros()?.toPlainString() ?: "정보 없음"
private fun mealNutrientName(key:String)=when(key){"kcal"->"열량";"carbsG"->"탄수화물";"proteinG"->"단백질";"fatG"->"지방";else->"식이섬유"}
private fun mealReviewStatus(status:String)=when(status){"APPLIED"->"식사 계획 반영 완료";"HELD"->"식단 변경 보류";else->"식단 초안 검토 중"}
