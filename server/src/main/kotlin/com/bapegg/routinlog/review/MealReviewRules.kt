package com.bapegg.routinlog.review

import com.bapegg.routinlog.food.*
import com.bapegg.routinlog.report.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.UUID

/** Arithmetic comparisons against existing user targets; never a nutrition prescription. */
internal object MealReviewRules {
    private val keys=listOf("kcal","proteinG","carbsG","fatG","fiberG")
    private fun value(n:NutritionValues,key:String)=when(key){"kcal"->n.kcal;"proteinG"->n.proteinG;"carbsG"->n.carbsG;"fatG"->n.fatG;else->n.fiberG}
    private fun total(n:NutritionTotals,key:String)=when(key){"kcal"->n.kcal;"proteinG"->n.proteinG;"carbsG"->n.carbsG;"fatG"->n.fatG;else->n.fiberG}
    private fun ratio(a:BigDecimal,b:BigDecimal)=(a-b).abs().divide(b,8,RoundingMode.HALF_UP)
    fun option(template:MealTemplateDto,foods:List<FoodDto>):MealReviewOption? {
        val items=template.items.map { item->
            val food=foods.firstOrNull { it.id==item.foodId } ?: return null
            LoggedMealItem(UUID.nameUUIDFromBytes((template.id+":"+food.id).toByteArray(Charsets.UTF_8)).toString(),food.id,food.name,food.brand,food.basisGrams,
                food.nutrition,food.preparation,food.sourceNote,item.grams,food.source)
        }
        return MealReviewOption(template.id,template.name,items,MealNutrition.totals(items))
    }
    fun create(week:LocalDate,report:WeeklyReport,plan:MealPlanDto,templates:List<MealTemplateDto>,foods:List<FoodDto>,upcoming:List<MealDayDto>):MealReviewDto {
        val options=templates.mapNotNull { option(it,foods) }
        val day=upcoming.firstOrNull { d->plan.slots.any { s->s.templateId!=null&&options.any { it.id==s.templateId }&&d.items.none { it.slotId==s.id }&&d.plannedMeals.none { it.slotId==s.id } } }
        val slots=plan.slots.filter { s->s.templateId!=null&&options.any { it.id==s.templateId }&&day?.items.orEmpty().none { it.slotId==s.id }&&day?.plannedMeals.orEmpty().none { it.slotId==s.id } }
        val current=plan.slots.associate { s->s.id to (day?.plannedMeals?.firstOrNull { it.slotId==s.id }?.items ?: options.firstOrNull { it.id==s.templateId }?.items) }
        val complete=current.isNotEmpty()&&current.values.all { !it.isNullOrEmpty() }
        val recordedAll=report.mealDays.size==7&&report.to==report.weekEnd&&report.mealDays.all { d->plan.slots.all { s->d.items.any { it.slotId==s.id } } }
        val focus=if(recordedAll)report.nutrition.filter { it.targetDays==7&&it.missingItems==0&&it.recorded!=null&&it.target?.signum()==1&&it.recorded.compareTo(it.target)!=0 }
            .maxByOrNull { ratio(it.recorded!!,it.target!!) }?.key else null
        val target=day?.target
        val baseline=MealNutrition.totals(current.values.filterNotNull().flatten())
        fun distance(t:NutritionTotals,key:String):BigDecimal? {
            val goal=target?.let { value(it,key) } ?: return null
            val sum=total(t,key)
            return if(goal.signum()<=0||sum.missingItems>0||sum.knownItems==0)null else ratio(sum.knownAmount,goal)
        }
        // Candidate must improve the observed nutrient and not worsen distance for any other known target.
        val compared=keys.filter { target?.let { n->value(n,it)?.signum() }==1 }
        val baseDistances=compared.associateWith { distance(baseline,it) }
        val canCompare=complete&&focus!=null&&focus in compared&&baseDistances.values.all { it!=null }&&current.values.filterNotNull().flatten().none { it.preparation==FoodPreparation.UNKNOWN }
        data class Candidate(val slot:MealSlot,val option:MealReviewOption,val gain:BigDecimal)
        val candidate=if(canCompare)slots.flatMap { slot->options.filter { it.id!=slot.templateId }.mapNotNull { option->
            if(option.items.any { it.preparation==FoodPreparation.UNKNOWN })return@mapNotNull null
            val totals=MealNutrition.totals(current.filterKeys { it!=slot.id }.values.filterNotNull().flatten()+option.items)
            val distances=compared.associateWith { distance(totals,it) }
            if(compared.any { distances[it]==null||distances.getValue(it)!!>baseDistances.getValue(it)!! })return@mapNotNull null
            val gain=baseDistances.getValue(focus!!)!!-distances.getValue(focus)!!
            if(gain.signum()<=0)null else Candidate(slot,option,gain)
        } }.maxByOrNull { it.gain } else null
        val slot=if(day==null)null else candidate?.slot ?: slots.firstOrNull()
        val original=options.firstOrNull { it.id==slot?.templateId }?.copy(id="KEEP")
        // Keep the recommended option visible even with a large saved-meal library.
        val visible=listOfNotNull(original,candidate?.option)+options.filter { it.id!=slot?.templateId&&it.id!=candidate?.option?.id }.take(18)
        val labels=mapOf("kcal" to "열량","proteinG" to "단백질","carbsG" to "탄수화물","fatG" to "지방","fiberG" to "식이섬유")
        return MealReviewDto(week,day?.date,slot?.id,slot?.label,target,current.filterKeys { it!=slot?.id }.values.filterNotNull().flatten(),complete,
            if(slot==null)emptyList() else visible,if(original==null)null else candidate?.option?.id ?: "KEEP",report.nutrition,
            listOf("식사 기록이 있는 날 ${report.mealDays.count { it.items.isNotEmpty() }} / ${report.mealDays.size}일",
                "끼니의 기록 여부는 현재 설정한 ${plan.slots.size}개 끼니를 기준으로 비교해요. 누락된 간식이나 음료까지 확인한 것은 아니에요."),
            when { slot==null->"비교할 기본 식단이나 남은 끼니가 없어요. 저장 식단과 끼니 구성을 먼저 확인해주세요."
                candidate!=null->"기록 기준 ${labels[focus]} 차이를 살펴봤어요. 저장한 ‘${candidate.option.name}’로 바꾸면 현재 하루 목표와 계획의 차이가 줄어들어요."
                else->"자동으로 바꿀 만큼 비교 가능한 후보가 없어요. 기존 식단을 유지하거나 저장한 식단에서 직접 골라보세요." },
            listOf("지난주의 부족·초과량을 다음 주 목표에 더하거나 빼지 않아요. 몸의 변화 원인을 단정하지 않아요.",
                "자동 후보는 하루 계획의 알려진 영양 목표와 차이가 커지지 않는 저장 식단에서만 골라요. 최적 식단이나 의학적 권장량이라는 뜻은 아니에요.",
                "정보가 없는 영양소는 0으로 간주하지 않아요. 생것·조리 후·판매 상태와 기준량은 등록한 표기를 그대로 사용해요.",
                "한 날짜의 한 끼에만 반영해요. 식사 기록과 기본 식단, 하루 영양 목표는 바뀌지 않아요."),
            listOf(ReviewEvidence("Aragon 등 · ISSN 식단과 체성분 지침 (2017)","https://pubmed.ncbi.nlm.nih.gov/28630601/",
                "식단·체성분 관련 문헌을 비판적으로 검토한 학회 입장문 · 주로 4주 이상 중재 연구",
                "지속적인 에너지 섭취와 식단을 유지할 수 있는지가 체성분 목표에서 중요하다고 설명해요.",
                "메타분석 자체가 아니며 여성·고령층 등의 근거 한계가 있어요. 이 앱의 한 끼 선택 규칙이나 개인별 음식량을 검증한 연구는 아니에요.")))
    }
}
