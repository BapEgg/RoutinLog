package com.bapegg.routinlog.review

import com.bapegg.routinlog.condition.ConditionDto
import com.bapegg.routinlog.workout.*
import java.time.LocalDate

/** Explicit product rules, not an LLM, clinical assessment or validated individual load prescription. */
internal object WorkoutReviewRules {
    private val evidence=listOf(ReviewEvidence(
        "Currier 등 · ACSM 저항운동 지침 (2026)","https://pubmed.ncbi.nlm.nih.gov/41843416/",
        "건강한 성인 대상 무작위시험을 종합한 체계적 문헌고찰 137편 · 참여자 3만 명 이상",
        "점진적인 저항운동과 목표에 맞춘 부하·운동량 구성을 다뤄요.",
        "이 연구가 개인의 정확한 회복일, 다음 중량 또는 이 앱의 조정 규칙을 검증한 것은 아니에요. 재활·통증 치료 프로그램으로 사용하지 않아요.",
    ))
    data class Result(val view:WorkoutReviewDto,val day:WorkoutDayDto?,val entry:PlannedExercise?)
    fun create(week:LocalDate,goal:String,source:List<WorkoutDayDto>,conditions:List<ConditionDto>,upcoming:List<WorkoutDayDto>):Result {
        val candidates=upcoming.filter { it.session==null }.flatMap { day->day.planned?.entries.orEmpty().filter { entry->
            entry.exercise.recordType in setOf(RecordType.WEIGHT_REPS,RecordType.REPS,RecordType.DURATION) && entry.sets.any { !it.warmup } &&
                entry.sets.filterNot { it.warmup }.all { when(entry.exercise.recordType){
                    RecordType.WEIGHT_REPS->it.weightKg!=null&&it.reps!=null;RecordType.REPS->it.reps!=null;RecordType.DURATION->it.durationSeconds!=null
                } }
        }.map { day to it } }
        val flagged=conditions.any { it.values.fatigue=="HIGH" || it.values.soreness=="HIGH" || !it.values.memo.isNullOrBlank() || !it.values.sorenessArea.isNullOrBlank() } || source.any { d->d.session?.let { s->
            !s.note.isNullOrBlank()||s.entries.any { e->!e.note.isNullOrBlank()||e.sets.any { !it.note.isNullOrBlank() } }
        }==true }
        val reductions=candidates.mapNotNull { (day,entry)->lastComparable(entry,source)?.let { (date,targets)->Triple(day,entry,date to targets) } }
        val reduced=reductions.firstOrNull()
        val picked=if(reduced!=null)reduced.first to reduced.second else candidates.firstOrNull()
        val day=picked?.first;val entry=picked?.second
        val planned=entry?.sets.orEmpty().filterNot { it.warmup }.map { ReviewTarget(it.id,it.weightKg,it.reps,it.durationSeconds) }
        val alternative=if(flagged)null else reduced?.third?.second
        val reason=when {
            entry==null->"다음 주에 비교할 세트 목표가 아직 없거나, 남은 운동을 이미 시작했어요. 먼저 날짜별 운동 계획을 확인해주세요."
            flagged->"피로·근육통 또는 남긴 메모를 확인할 필요가 있어요. 기록을 먼저 확인하고 다음 수행을 직접 정해보세요."
            alternative!=null->"같은 종목·기구·중량 기준으로 목표보다 낮춰 완료한 기록이 있어요. 다음 한 번을 지난 수행치로 잡아볼 수 있어요."
            else->"바로 중량을 바꿀 만큼 비교 가능한 기록이 없어요. 기존 목표를 확인하거나 직접 수정해보세요."
        }
        val observations=mutableListOf("기준 주 ${week} ~ ${week.plusDays(6)} · 목표 ${when(goal){"GAIN"->"근육 증가";"LOSE"->"체중 감량";else->"유지"}}")
        if(reduced!=null)observations.add("${reduced.third.first} ${reduced.second.exercise.name}: 현재 계획의 본 세트와 같은 기준으로 완료한 수행치를 확인했어요.")
        if(flagged)observations.add("회복 상태나 메모의 의미를 자동으로 판정하지 않았어요.")
        return Result(WorkoutReviewDto(week,week.plusWeeks(1),day?.date,day?.planned?.routineName,entry?.exercise,planned,alternative,
            if(alternative!=null)"LAST_PERFORMANCE" else "KEEP",goal,reason,observations,
            listOf("한 종목의 본 세트만 다음 한 날짜에 반영해요. 워밍업과 다른 종목, 기본 루틴은 그대로예요.",
                "지난 실제 수행치를 후보로 보여주는 것은 앱의 보수적인 비교 규칙이에요. 한 주의 감소로 정체·회복 부족·적정 강도를 확정하지 않아요.",
                "누락된 운동이나 식사 차이를 다음 주 운동량·칼로리로 보상하지 않아요."),evidence),day,entry)
    }
    private fun lastComparable(entry:PlannedExercise,source:List<WorkoutDayDto>):Pair<LocalDate,List<ReviewTarget>>? {
        if(entry.exercise.recordType!=RecordType.WEIGHT_REPS || entry.exercise.loadConvention==LoadConvention.UNSPECIFIED)return null
        val planned=entry.sets.filterNot { it.warmup }
        // Only the most recent same-exercise session is eligible; never cherry-pick an older reduced performance.
        val day=source.sortedByDescending { it.date }.firstOrNull { it.session?.entries?.any { e->e.exercise.id==entry.exercise.id }==true } ?: return null
        val session=day.session ?: return null
        if(session.status!=WorkoutStatus.COMPLETED)return null
        val actual=session.entries.filter { it.exercise==entry.exercise }
        if(actual.size!=1)return null
        val sourcePlan=session.planned?.entries?.singleOrNull { it.id==actual.single().plannedEntryId&&it.exercise==entry.exercise } ?: return null
        // A newly increased future goal is not evidence that last week's original goal was missed.
        if(planned.any { p->sourcePlan.sets.none { it.id==p.id&&!it.warmup&&it.weightKg?.compareTo(p.weightKg)==0&&it.reps==p.reps } })return null
        val sets=actual.single().sets
        val targets=planned.map { p->
            val matching=sets.filter { it.planSetId==p.id }
            val a=matching.singleOrNull()?.takeIf { it.status==SetStatus.DONE&&it.weightKg!=null&&it.reps!=null } ?: return null
            if(a.weightKg!!>p.weightKg!! || a.reps!!>p.reps!!)return null
            ReviewTarget(p.id,a.weightKg,a.reps)
        }
        if(targets.zip(planned).none { (a,p)->a.weightKg!!<p.weightKg!! || a.reps!!<p.reps!! })return null
        return day.date to targets
    }
}
