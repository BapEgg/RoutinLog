package com.bapegg.routinlog.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bapegg.routinlog.data.ConditionValues
import com.bapegg.routinlog.ui.*
import com.bapegg.routinlog.ui.theme.*
import java.time.LocalDate

@Composable internal fun LiveConditionScreen(ui:PreviewSession,model:ConditionViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    var date by remember(state.date) { mutableStateOf(state.date) }
    var dateOpen by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var reloading by remember { mutableStateOf(false) }
    UiCard {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)){MutedText("기록하는 날짜");Text(state.date,style=MaterialTheme.typography.titleMedium)}
            TextButton(onClick={dateOpen=!dateOpen}){Text("날짜 변경")}
        }
        if(dateOpen) {
            ConditionField("컨디션 날짜",date,{date=it})
            UiButton("이 날짜 보기",{
                val parsed=runCatching { LocalDate.parse(date) }.getOrNull()
                if(parsed!=null&&parsed in LocalDate.of(1900,1,1)..ui.today()){ui.set("home.date",date);model.selectDate(date);dateOpen=false}
                else model.selectDate(date)
            },primary=false)
        }
    }
    state.error?.let { UiCard { Text(it,color=MaterialTheme.colorScheme.error);UiButton("저장된 내용 다시 확인",{reloading=true},primary=false) } }
    if(state.loading&&!state.loaded)UiCard { Text("컨디션 기록을 확인하고 있어요…");LinearProgressIndicator(Modifier.fillMaxWidth()) }
    val draft=state.draft
    if(state.loaded&&draft!=null) {
        UiCard {
            Badge(if(state.record==null)"아직 저장 전"else"저장된 기록 수정")
            SectionTitle("기억하고 싶은 항목만 남겨요")
            MutedText("선택하지 않은 항목은 미기록으로 남아요.")
        }
        UiCard {
            SectionTitle("지난밤 수면")
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)){ConditionField("수면 시간",draft.hours,{v->model.edit { it.copy(hours=v) }},true,"시간")}
                Column(Modifier.weight(1f)){ConditionField("수면 분",draft.minutes,{v->model.edit { it.copy(minutes=v) }},true,"분")}
            }
            Chips(listOf("6시간","7시간","8시간"),if(draft.minutes in listOf("","0"))"${draft.hours}시간"else"") { value->model.edit { it.copy(hours=value.removeSuffix("시간"),minutes="0") } }
            TextButton(onClick={model.edit { it.copy(hours="",minutes="") }}){Text("수면 입력 비우기")}
        }
        ConditionChoices("피로","지금 느끼는 정도",levelLabels,draft.values.fatigue){v->model.edit { it.copy(values=it.values.copy(fatigue=v)) }}
        ConditionChoices("근육통","느껴지는 정도",sorenessLabels,draft.values.soreness){v->model.edit { it.copy(values=it.values.copy(soreness=v,sorenessArea=if(v in listOf("MILD","HIGH"))it.values.sorenessArea else null)) }}
        if(draft.values.soreness in listOf("MILD","HIGH"))ConditionField("뻐근한 부위 · 선택",draft.values.sorenessArea.orEmpty(),{v->model.edit { it.copy(values=it.values.copy(sorenessArea=v)) }})
        ConditionChoices("스트레스","오늘 느낀 정도",levelLabels,draft.values.stress){v->model.edit { it.copy(values=it.values.copy(stress=v)) }}
        ConditionChoices("생활 속 움직임","운동 외 업무·집안일 등",activityLabels,draft.values.activity){v->model.edit { it.copy(values=it.values.copy(activity=v)) }}
        UiCard {
            SectionTitle("짧은 메모")
            ConditionField("기억하고 싶은 변화",draft.values.memo.orEmpty(),{v->model.edit { it.copy(values=it.values.copy(memo=v)) }},multiline=true)
            MutedText("${draft.values.memo.orEmpty().length} / 1,000자")
        }
        MutedText("생활 활동은 내가 느낀 정도를 남겨요. 걸음 수나 소비 열량으로 환산하지 않아요.")
        UiButton("컨디션 저장",{model.save { ui.go("H01") }})
        if(draft.version!=null)UiButton("이날 컨디션 삭제",{deleting=true},primary=false)
        UiButton("저장된 내용 다시 불러오기",{reloading=true},primary=false)
        SectionTitle("선택한 날짜까지 최근 30일")
        if(state.records.isEmpty())MutedText("아직 저장한 컨디션이 없어요.")
        state.records.forEach { record->UiCard { UiRow(record.date,conditionSummary(record.values),onClick={ui.set("home.date",record.date);model.selectDate(record.date)}) } }
    }
    if(deleting)AlertDialog(onDismissRequest={deleting=false},title={Text("이날 컨디션을 삭제할까요?")},text={Text("수면·상태·메모를 함께 삭제해요. 신체·식단·운동 기록은 유지돼요.")},
        confirmButton={TextButton(onClick={deleting=false;model.delete { ui.go("H01") }}){Text("삭제")}},dismissButton={TextButton(onClick={deleting=false}){Text("취소")}})
    if(reloading)AlertDialog(onDismissRequest={reloading=false},title={Text("저장된 내용으로 다시 볼까요?")},text={Text("이 날짜에서 아직 저장하지 않은 입력을 버리고 서버 기록을 불러와요.")},
        confirmButton={TextButton(onClick={reloading=false;model.refresh(true)}){Text("다시 불러오기")}},dismissButton={TextButton(onClick={reloading=false}){Text("취소")}})
    if(state.busy || (state.loading&&state.loaded))Dialog(onDismissRequest={},properties=DialogProperties(dismissOnBackPress=false,dismissOnClickOutside=false)) {
        UiCard { Text(if(state.busy)"컨디션 저장 중…"else"기록 확인 중…");LinearProgressIndicator(Modifier.fillMaxWidth()) }
    }
}
@Composable private fun ConditionField(label:String,value:String,change:(String)->Unit,numeric:Boolean=false,suffix:String="",multiline:Boolean=false) {
    OutlinedTextField(value,change,modifier=Modifier.fillMaxWidth().semantics { contentDescription=label },label={Text(label)},singleLine=!multiline,minLines=if(multiline)3 else 1,
        suffix=if(suffix.isEmpty())null else ({Text(suffix)}),keyboardOptions=KeyboardOptions(keyboardType=if(numeric)KeyboardType.Number else KeyboardType.Text))
}
@Composable private fun ConditionChoices(title:String,helper:String,labels:Map<String,String>,selected:String?,change:(String?)->Unit) {
    UiCard { SectionTitle(title);MutedText(helper);Chips(labels.values.toList(),labels[selected].orEmpty()){label->val value=labels.entries.first { it.value==label }.key;change(value.takeUnless { it==selected })} }
}
private val levelLabels=linkedMapOf("LOW" to "낮음","MODERATE" to "보통","HIGH" to "높음")
private val sorenessLabels=linkedMapOf("NONE" to "없음","MILD" to "조금","HIGH" to "많음")
private val activityLabels=linkedMapOf("LIGHT" to "주로 앉아서","MODERATE" to "자주 움직임","HIGH" to "많이 움직임")
internal fun conditionSummary(v:ConditionValues)=listOfNotNull(
    v.sleepMinutes?.let { "수면 ${it/60}시간 ${it%60}분" },v.fatigue?.let { "피로 ${levelLabels[it]}" },
    v.soreness?.let { "근육통 ${sorenessLabels[it]}" },v.stress?.let { "스트레스 ${levelLabels[it]}" },v.activity?.let { activityLabels[it] },
    v.memo?.takeIf { it.isNotBlank() }?.let { "메모 있음" }).joinToString(" · ")
