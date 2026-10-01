package com.bapegg.routinlog.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bapegg.routinlog.data.*
import com.bapegg.routinlog.ui.*
import com.bapegg.routinlog.ui.theme.DeepBlue

@Composable fun LiveWorkoutReviewScreens(id:String,ui:PreviewSession,model:WorkoutReviewViewModel) {
    val s by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(id,s.review?.status) {
        if(id=="R07"&&s.review?.status=="APPLIED")ui.go("R11")
        if(id=="R07"&&s.review?.status=="HELD")ui.go("R09")
    }
    LaunchedEffect(id,s.owner) { if(id=="R09"&&s.owner!=null)model.history() }
    if(s.loading) { UiCard { Text("내 기록과 선택을 확인하고 있어요.");LinearProgressIndicator(Modifier.fillMaxWidth(),color=DeepBlue) };return }
    if(s.error!=null)UiCard {
        Text(s.error!!,color=MaterialTheme.colorScheme.error)
        UiButton("저장 상태 다시 확인",{if(id=="R09")model.history() else model.reload()},false,enabled=!s.busy)
    }
    if(s.busy)UiCard { Text("선택을 저장하고 있어요.");LinearProgressIndicator(Modifier.fillMaxWidth(),color=DeepBlue) }
    if(id=="R09") {
        MutedText("최근 20개 초안과 선택 · 다시 만든 초안도 남겨요")
        if(s.history.isEmpty())UiCard { Text("아직 남긴 선택이 없어요.");MutedText("리포트에서 다음 수행 초안을 확인해보세요.") }
        s.history.forEach { row->UiCard {
            Badge(reviewStatus(row.status))
            SectionTitle("${row.week} 주간 기록")
            Text(row.exercise?.name ?: "계획 확인이 필요한 주")
            row.targetDate?.let { MutedText("적용 대상 · $it") }
            row.choice?.let { KeyValue("내 선택",choiceLabel(it)) }
            row.decisionReason?.let { MutedText("내가 남긴 이유 · $it") }
            if(row.status=="APPLIED")MutedText("당시 적용한 선택이에요. 이후 직접 바꾼 일정과는 다를 수 있어요.")
            UiButton("이 주의 초안 확인",{model.open(row.week);ui.go("R05")},false,enabled=!s.busy)
        } }
        return
    }
    val r=s.review
    if(r==null) {
        UiCard {
            SectionTitle("다음 운동, 기록을 보고 정해요")
            MutedText("같은 종목의 계획과 실제 수행을 비교해 다음 한 번의 목표를 검토해요. 최종 선택은 내가 해요.")
            if(s.week!=null)MutedText("기준 주 · ${s.week}")
        }
        if(s.week!=null)UiButton("운동 초안 만들기",{model.prepare()},enabled=!s.busy&&!s.needsReload)
        else UiButton("리포트에서 주 선택",{ui.go("R01")})
        return
    }
    val canEdit=r.status=="DRAFT"&&!s.busy&&!s.needsReload
    MutedText("${r.week} 주 기록 → ${r.targetWeek} 주 계획")
    when(id) {
        "R08" -> {
            val uri=LocalUriHandler.current
            UiCard { SectionTitle("내 기록에서 확인한 것");r.observations.forEach { Text(it) } }
            UiCard { SectionTitle("초안을 만드는 기준");Text(r.reason);r.limitations.forEach { MutedText(it) };MutedText("규칙형 초안 · ${r.policyVersion}") }
            r.evidence.forEach { e->UiCard {
                SectionTitle(e.title);Text(e.population);MutedText(e.finding);MutedText(e.limitation)
                UiButton("근거 원문 확인",{runCatching { uri.openUri(e.url) }.onFailure { ui.notify("링크를 열지 못했어요.") }},false)
            } }
        }
        "R06" -> {
            ReviewScope(r)
            s.sets.forEachIndexed { i,set->UiCard {
                SectionTitle("본 세트 ${i+1}")
                if(r.exercise?.recordType=="WEIGHT_REPS")Input("중량",set.weight,{model.edit(i,"weight",it)},if(s.units=="IMPERIAL")"lb" else "kg",numeric=true,enabled=canEdit)
                if(r.exercise?.recordType in setOf("WEIGHT_REPS","REPS"))Input("반복수",set.reps,{model.edit(i,"reps",it)},"회",numeric=true,enabled=canEdit)
                if(r.exercise?.recordType=="DURATION")Input("수행 시간",set.seconds,{model.edit(i,"seconds",it)},"초",numeric=true,enabled=canEdit)
            } }
            Input("내가 바꾼 이유 (선택)",s.reason,model::reason,multiline=true,enabled=canEdit)
            UiButton("수정한 목표 확인",{if(model.validate())ui.go("R07")},enabled=canEdit)
        }
        "R07" -> {
            ReviewScope(r)
            val command=runCatching { model.command("APPLY") }.getOrNull()
            UiCard {
                SectionTitle("적용 전 비교");KeyValue("내 선택",choiceLabel(s.choice))
                TargetComparison(r.planned,command?.targets.orEmpty(),r.exercise?.recordType.orEmpty(),s.units)
                s.reason.takeIf { it.isNotBlank() }?.let { MutedText("내가 남긴 이유 · $it") }
            }
            MutedText("확인한 날짜의 한 종목만 바뀌어요. 기본 루틴과 식단 목표, 이미 수행한 기록은 바뀌지 않아요.")
            UiButton("확인한 목표 적용",{model.decide("APPLY"){ui.go("R11")}},enabled=canEdit&&command!=null&&r.targetDate!=null)
            UiButton("더 수정하기",{ui.go("R06")},false,enabled=canEdit)
        }
        else -> {
            Badge(if(r.status=="DRAFT")"기록 기반 운동 초안" else reviewStatus(r.status))
            UiCard { SectionTitle(r.exercise?.name ?: "먼저 계획을 확인해요");Text(r.reason);r.observations.forEach { MutedText(it) } }
            if(r.targetDate!=null)ReviewScope(r)
            if(r.status=="DRAFT"&&r.planned.isNotEmpty()) {
                Choice("기존 계획 유지","강도나 회복이 적정하다는 판정은 아니에요.",s.choice=="KEEP"){model.choose("KEEP")}
                if(r.alternative!=null)Choice("지난 실제 수행치","목표를 낮춰 완료한 기록을 다음 한 번의 후보로 사용해요.",s.choice=="LAST_PERFORMANCE"){model.choose("LAST_PERFORMANCE")}
                UiCard { TargetComparison(r.planned,runCatching { model.command("APPLY").targets }.getOrDefault(emptyList()),r.exercise!!.recordType,s.units) }
                Input("선택 이유 (선택)",s.reason,model::reason,multiline=true,enabled=canEdit)
                UiButton("적용 전 확인",{if(model.validate())ui.go("R07")},enabled=canEdit)
                UiButton("목표 직접 수정",{ui.go("R06")},false,enabled=canEdit)
            } else if(r.status!="DRAFT") {
                UiCard {
                    KeyValue("내 선택",choiceLabel(r.choice.orEmpty()))
                    if(r.chosen!=null)TargetComparison(r.planned,r.chosen,r.exercise?.recordType.orEmpty(),s.units)
                    r.decisionReason?.let { MutedText(it) }
                }
            } else UiButton("날짜별 운동 계획 정하기",{ui.go("W01")},false)
            if(r.status=="DRAFT")UiButton("이번에는 보류",{model.decide("HOLD"){ui.go("R09")}},false,enabled=canEdit)
            UiButton("기록과 근거 확인",{ui.go("R08")},false,enabled=!s.busy)
            if(r.status!="APPLIED")UiButton("새 기록으로 초안 다시 만들기",{model.prepare(true)},false,enabled=!s.busy&&!s.needsReload)
            UiButton("지난 초안과 내 선택",{ui.go("R09")},false,enabled=!s.busy)
            if(r.status=="APPLIED")UiButton("반영된 운동 일정 보기",{ui.set("review.appliedDate",r.targetDate.orEmpty());ui.go("W01")},false)
        }
    }
}
@Composable private fun ReviewScope(r:WorkoutReviewDto) {
    UiCard {
        KeyValue("적용 날짜",r.targetDate ?: "미정");KeyValue("루틴",r.routineName ?: "미정")
        KeyValue("종목",r.exercise?.name ?: "미정")
        r.exercise?.let { MutedText("${it.equipment} · ${it.target} · ${when(it.loadConvention){"PER_HAND"->"한 손 기준";"MACHINE"->"기구 표시 중량";"TOTAL"->"전체 중량";"EXTERNAL"->"추가 중량";"BODYWEIGHT"->"맨몸";else->"중량 기준 미지정"}}") }
    }
}
@Composable private fun TargetComparison(before:List<ReviewTarget>,after:List<ReviewTarget>,type:String,units:String) {
    before.forEachIndexed { i,p->
        val next=after.firstOrNull { it.setId==p.setId }
        Text("본 세트 ${i+1}")
        KeyValue("기존 계획",targetText(p,type,units))
        KeyValue("선택한 목표",next?.let { targetText(it,type,units) } ?: "입력 확인 필요")
    }
}
private fun targetText(t:ReviewTarget,type:String,units:String)=plannedSetText(PlannedSet(t.setId,t.weightKg,t.reps,t.durationSeconds),type,units)
private fun reviewStatus(status:String)=when(status){"APPLIED"->"적용 완료";"HELD"->"이번에는 보류";else->"검토 중"}
private fun choiceLabel(choice:String)=when(choice){"KEEP"->"기존 계획 유지";"LAST_PERFORMANCE"->"지난 실제 수행치";"CUSTOM"->"직접 수정";else->"아직 선택하지 않음"}
