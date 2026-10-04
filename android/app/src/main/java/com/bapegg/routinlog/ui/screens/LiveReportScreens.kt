package com.bapegg.routinlog.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bapegg.routinlog.data.*
import com.bapegg.routinlog.ui.*
import com.bapegg.routinlog.ui.theme.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable fun LiveReportScreens(id:String,ui:PreviewSession,model:ReportViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.owner) { if(state.owner!=null&&state.report==null&&!state.loading)model.refresh() }
    val reviews=LocalWorkoutReviews.current
    val mealReviews=LocalMealReviews.current
    val report=state.report
    if(state.loading) { UiCard { Text("한 주의 기록을 모으고 있어요.");LinearProgressIndicator(Modifier.fillMaxWidth(),color=DeepBlue) };return }
    if(state.error!=null) { UiCard { Text(state.error!!);UiButton("리포트 다시 불러오기",model::refresh) };return }
    if(report==null)return
    MutedText("${report.from} — ${report.to}${if(report.to!=report.weekEnd) " · 이번 주 진행 중" else " · 월~일"}")
    when(id) {
        "R01" -> {
            LocalFeatures.current?.let { LiveAnalysis(ui,it,report.from) }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                UiButton("이전 주",{model.move(-1)},false,report.from>"1900-01-08",Modifier.weight(1f))
                UiButton("다음 주",{model.move(1)},false,report.from<report.latestWeek,Modifier.weight(1f))
            }
            if(report.from!=report.latestWeek)UiButton("이번 주 기록 보기",{model.select(report.latestWeek)},false)
            UiCard {
                val hasRecords=report.cardio.isNotEmpty()||report.mealDays.any { it.items.isNotEmpty() }||report.body.isNotEmpty()||report.steps.isNotEmpty()||report.conditions.isNotEmpty()||report.workouts.any { it.session!=null }
                SectionTitle(if(hasRecords)"기록이 모여 한 주가 되었어요" else "아직 모인 기록이 없어요")
                KeyValue("식사 기록이 있는 날","${report.mealDays.count { it.items.isNotEmpty() }} / ${report.mealDays.size}일")
                KeyValue("체중 · 허리 측정","${report.weight.count}회 · ${report.waist.count}회")
                KeyValue("걸음 · 컨디션 기록","${report.steps.size}일 · ${report.conditions.size}일")
                MutedText("기록하지 않은 날은 0으로 채우지 않아요."+if(report.to!=report.weekEnd)" 이번 주는 오늘까지의 기록이에요." else "")
            }
            UiCard {
                SectionTitle("목표와 기록한 영양", "상세",{ui.go("R02")})
                val kcal=report.nutrition.first { it.key=="kcal" }
                HeroNumber(amount(kcal.recorded),"kcal")
                MutedText("확인된 섭취량 합계 · 목표 ${amount(kcal.target)} kcal (${kcal.targetDays}일)")
                MutedText("빠뜨린 식사나 영양정보가 있으면 실제 섭취량과 다를 수 있어요.")
            }
            UiCard {
                SectionTitle("계획과 실제 운동","상세",{ui.go("R03")})
                KeyValue("날짜별 계획","${report.workouts.count { plan(it)!=null }}일")
                KeyValue("수행한 세트가 있는 날","${report.workouts.count { d->d.session?.entries?.any { e->e.sets.any { it.status=="DONE" } }==true }}일")
                KeyValue("유산소 기록", "${report.cardio.size}회 · ${report.cardio.sumOf { it.values.minutes }}분")
                KeyValue("완료한 세트","${report.workouts.sumOf { d->d.session?.entries?.sumOf { e->e.sets.count { it.status=="DONE" } } ?: 0 }}세트")
                MutedText("세트 수에는 워밍업이 포함돼요. 종목이 달라지면 같은 운동량으로 보지 않아요.")
            }
            UiCard {
                SectionTitle("몸과 생활 활동","상세",{ui.go("R04")})
                BodyAverage("평균 체중",report.weight,false)
                BodyAverage("평균 허리둘레",report.waist,true)
            }
            MutedText("식사와 운동의 차이만으로 몸의 변화 원인을 확정할 수는 없어요. 몇 주의 흐름을 함께 살펴보세요.")
            if(reviews!=null) {
                UiButton("다음 수행 초안 확인",{reviews.open(report.from);ui.go("R05")})
                UiButton("지난 초안과 내 선택",{ui.go("R09")},false)
            }
            UiButton("최신 기록으로 새로고침",model::refresh,false)
            if(mealReviews!=null) {
                UiButton("다음 식단 초안 확인",{mealReviews.open(report.from);ui.go("R12")})
                UiButton("지난 식단 선택",{ui.go("R16")},false)
            }
        }
        "R02" -> NutritionReport(report)
        "R03" -> WorkoutReport(report)
        "R04" -> ActivityReport(report)
    }
}

@Composable private fun NutritionReport(report:WeeklyReport) {
    UiCard {
        Text("당시 목표와 기록한 섭취량")
        MutedText("목표를 바꾼 날은 그날부터 적용된 값을 합산했어요. 차이는 확인된 섭취량에서 목표를 뺀 값이며, 실제 부족·초과량을 확정하지 않아요.")
    }
    report.nutrition.forEach { row ->
        val unit=if(row.key=="kcal")"kcal" else "g"
        val label=mapOf("kcal" to "열량","carbsG" to "탄수화물","proteinG" to "단백질","fatG" to "지방","fiberG" to "식이섬유").getValue(row.key)
        UiCard {
            SectionTitle(label)
            KeyValue("목표 · ${row.targetDays}일", "${amount(row.target)} $unit")
            KeyValue("확인된 섭취량", "${amount(row.recorded)} $unit")
            if(row.target!=null&&row.target.signum()>0&&row.recorded!=null)ProgressLine((row.recorded.toFloat()/row.target.toFloat()).coerceIn(0f,1f))
            val difference=if(row.targetDays==report.mealDays.size&&row.missingItems==0&&row.recorded!=null&&row.target!=null)row.recorded-row.target else null
            KeyValue("기록 기준 차이",difference?.let { "${signed(it)} $unit" } ?: "비교할 정보 부족")
            if(row.missingItems>0)MutedText("음식 ${row.missingItems}개의 양 또는 영양정보가 없어요. 합계는 확인 가능한 부분만 표시해요.")
            if(row.targetDays<report.mealDays.size)MutedText("목표가 없는 날 ${report.mealDays.size-row.targetDays}일은 목표 합계에서 제외했어요.")
        }
    }
    SectionTitle("날짜별 식사 기록")
    report.mealDays.forEach { day ->
        var open by remember(report.from,day.date) { mutableStateOf(false) }
        UiCard {
            UiRow(dayLabel(day.date),value=if(day.items.isEmpty())"미기록" else "${day.items.size}끼 · ${if(open)"접기" else "보기"}",onClick={open=!open})
            if(open)day.items.forEach { meal ->
                Text("${meal.slotLabel} · ${if(meal.status=="SKIPPED")"먹지 않음" else "먹음"}")
                meal.items.forEach { item->MutedText("${item.name} · ${amount(item.grams)} g") }
                meal.note?.let { MutedText(it) }
            }
        }
    }
    MutedText("식사 기록이 있다고 그날의 모든 식사를 기록한 것은 아니에요. 지난 주의 차이를 다음 주 목표에 자동으로 더하거나 빼지 않아요.")
}

private fun plan(day:WorkoutDayDto)=if(day.session!=null)day.session.planned else day.planned
@Composable private fun WorkoutReport(report:WeeklyReport) {
    val units=LocalAccount.current.state.profile?.units ?: "METRIC"
    CardioReport(report.cardio)
    report.workouts.forEach { day ->
        var open by remember(report.from,day.date) { mutableStateOf(false) }
        val baseline=plan(day)
        UiCard {
            UiRow(dayLabel(day.date),baseline?.routineName ?: "지정된 운동 없음",if(open)"접기" else "자세히",onClick={open=!open})
            KeyValue("운동 기록",when(day.session?.status){"COMPLETED"->"기록 마침";"IN_PROGRESS"->"기록 진행 중";else->"미기록"})
            day.`override`?.let { MutedText("이날 일정 변경 · ${it.workout?.routineName ?: "휴식"}");it.note?.let { note->MutedText(note) } }
            if(open) {
                baseline?.entries?.forEach { entry ->
                    Text("계획 · ${entry.exercise.name}")
                    MutedText("${entry.exercise.target} · ${entry.exercise.equipment} · ${loadLabel(entry.exercise.loadConvention)}")
                    entry.sets.forEachIndexed { i,s ->MutedText("${i+1}세트${if(s.warmup)" · 워밍업" else ""} · ${plannedSetText(s,entry.exercise.recordType,units)}") }
                }
                day.session?.entries?.forEach { entry ->
                    Text("실제 · ${entry.exercise.name}")
                    MutedText("${entry.exercise.target} · ${entry.exercise.equipment} · ${loadLabel(entry.exercise.loadConvention)}")
                    entry.replacementReason?.let { MutedText("변경 이유 · $it") }
                    entry.sets.forEachIndexed { i,s ->
                        MutedText("${i+1}세트 · ${when(s.status){"DONE"->actualSetText(s,entry.exercise.recordType,units);"SKIPPED"->"생략";else->"미기록"}}")
                        s.note?.let { MutedText(it) }
                    }
                    entry.note?.let { MutedText(it) }
                }
                day.session?.note?.let { MutedText(it) }
                if(day.session==null)MutedText("기록이 없어 실제 수행 여부를 확인할 수 없어요.")
            }
        }
    }
    MutedText("계획은 운동을 시작할 때 저장한 내용이에요. 시작 전에는 해당 날짜의 일정이 표시돼요. 다른 기구·중량 기준의 수행량을 직접 비교하지 않아요.")
}

@Composable private fun ActivityReport(report:WeeklyReport) {
    UiCard {
        BodyAverage("평균 체중",report.weight,false);BodyAverage("평균 허리둘레",report.waist,true)
        MutedText("각 항목을 실제 측정한 날만 평균에 포함해요. 전주는 월~일 전체이며 측정 횟수와 조건이 다를 수 있어요.")
    }
    UiCard {
        SectionTitle("날짜별 측정")
        report.mealDays.forEach { day ->
            val b=report.body.firstOrNull { it.date==day.date }
            KeyValue(dayLabel(day.date),"${bodyValue(b?.weightKg?.toBigDecimal(),false)} · ${bodyValue(b?.waistCm?.toBigDecimal(),true)}")
        }
    }
    UiCard {
        SectionTitle("걸음 수")
        KeyValue("수집된 기록 합계",if(report.steps.isEmpty())"정보 없음" else "${report.steps.sumOf { it.steps }}걸음")
        val maximum=report.steps.maxOfOrNull { it.steps }?.coerceAtLeast(1) ?: 1
        report.mealDays.forEach { d ->
            val step=report.steps.firstOrNull { it.date==d.date }
            KeyValue(dayLabel(d.date),step?.let { "${it.steps}걸음" } ?: "미수집")
            if(step!=null)ProgressLine(step.steps.toFloat()/maximum.toFloat())
        }
        MutedText("수집 당시 날짜 기준이에요. 일부 시간만 수집됐거나 휴대폰을 들고 있지 않았을 수 있어요. 걸음 수를 운동 열량으로 환산하지 않아요.")
    }
    UiCard {
        SectionTitle("수면과 컨디션")
        val sleep=report.conditions.mapNotNull { it.values.sleepMinutes }
        KeyValue("기록한 수면 평균",if(sleep.isEmpty())"정보 없음" else "${(sleep.average()/60).toBigDecimal().setScale(1,RoundingMode.HALF_UP)}시간 · ${sleep.size}일")
        if(report.conditions.isEmpty())MutedText("이 주에는 남긴 컨디션 기록이 없어요.")
        report.conditions.sortedBy { it.date }.forEach { day ->
            Text(dayLabel(day.date));val v=day.values
            v.fatigue?.let { KeyValue("피로",level(it)) };v.stress?.let { KeyValue("스트레스",level(it)) }
            v.soreness?.let { KeyValue("근육통",if(it=="NONE")"없음" else if(it=="MILD")"가벼움" else "심함") }
            v.activity?.let { KeyValue("생활 활동",level(it)) }
            v.sorenessArea?.let { MutedText("부위 · $it") };v.memo?.let { MutedText(it) }
        }
    }
}
private fun level(value:String)=when(value){"LOW","LIGHT"->"낮음";"MODERATE"->"보통";else->"높음"}
private fun loadLabel(value:String)=when(value){"TOTAL"->"전체 중량";"PER_HAND"->"한 손 기준";"MACHINE"->"기구 표시 중량";"EXTERNAL"->"추가 중량";"BODYWEIGHT"->"맨몸";else->"중량 기준 미지정"}
private fun dayLabel(date:String)=LocalDate.parse(date).format(DateTimeFormatter.ofPattern("M/d E",Locale.KOREAN))
private fun amount(value:BigDecimal?)=value?.setScale(1,RoundingMode.HALF_UP)?.stripTrailingZeros()?.toPlainString() ?: "정보 없음"
private fun signed(value:BigDecimal)=(if(value.signum()>0)"+" else "")+amount(value)
@Composable private fun bodyValue(value:BigDecimal?,waist:Boolean):String {
    val imperial=LocalAccount.current.state.profile?.units=="IMPERIAL"
    val converted=value?.let { if(imperial)it.divide(if(waist)BigDecimal("2.54") else BigDecimal("0.45359237"),3,RoundingMode.HALF_UP) else it }
    return if(converted==null)"미측정" else "${amount(converted)} ${if(imperial)if(waist)"in" else "lb" else if(waist)"cm" else "kg"}"
}
@Composable private fun BodyAverage(label:String,value:ReportAverage,waist:Boolean) {
    KeyValue(label,bodyValue(value.value,waist))
    MutedText("이번 ${value.count}회 · 전주 ${value.previousCount}회 (${bodyValue(value.previous,waist)})")
    value.change?.let { MutedText("평균 변화 ${if(it.signum()>0)"+" else ""}${bodyValue(it,waist)}") }
}
