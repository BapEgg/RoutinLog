package com.bapegg.routinlog.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bapegg.routinlog.data.CardioDto
import com.bapegg.routinlog.ui.*
import com.bapegg.routinlog.ui.theme.DeepBlue

@Composable internal fun LiveCardioScreen(ui:PreviewSession,model:CardioViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val selected=ui.get("cardio.date",ui.today().toString())
    LaunchedEffect(state.owner,selected){model.open(selected)}
    LaunchedEffect(state.notice){state.notice?.let { ui.notify(it);model.clearNotice() }}
    var removing by remember(state.owner,state.date){mutableStateOf<CardioDto?>(null)}
    var discarding by remember(state.owner,state.date){mutableStateOf(false)}
    var options by remember(state.owner,state.draft?.id){mutableStateOf(false)}
    MutedText("${state.date} · 유산소를 따로 남겨요")
    state.error?.let { UiCard {
        Text(it,color=MaterialTheme.colorScheme.error)
        UiButton("기록 다시 불러오기",model::refresh,false,!state.busy)
        if(state.draft!=null)MutedText("작성 내용은 남아 있어요. 다른 곳에서 바뀐 기록을 수정하려면 작성 내용을 닫고 다시 선택해주세요.")
    } }
    if(state.loading)LinearProgressIndicator(Modifier.fillMaxWidth(),color=DeepBlue)
    if(state.loaded) {
        val draft=state.draft
        if(draft==null) {
            UiCard {
                SectionTitle("이날의 유산소")
                HeroNumber(state.records.sumOf { it.values.minutes }.toString(),"분 기록")
                MutedText(if(state.records.isEmpty())"아직 남긴 유산소가 없어요." else "${state.records.size}개 기록 · 시간 합계")
                UiButton("유산소 추가",{model.begin()})
            }
            state.records.forEach { record->UiCard {
                CardioDetails(record)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    UiButton("수정",{model.begin(record)},false,modifier=Modifier.weight(1f))
                    UiButton("삭제",{removing=record},false,modifier=Modifier.weight(1f))
                }
            } }
            UiButton("기록 새로고침",model::refresh,false)
        } else {
            UiCard {
                Badge(if(draft.version==null)"새 기록" else "기록 수정")
                CardioField("운동 이름",draft.activity,{v->model.edit { it.copy(activity=v) }})
                Chips(listOf("걷기","러닝","실내 자전거"),draft.activity){v->model.edit { it.copy(activity=v) }}
                CardioField("운동 시간 · 분",draft.minutes,{v->model.edit { it.copy(minutes=v) }},true)
            }
            UiCard {
                SectionTitle("칼로리 기록 방식")
                Chips(listOf("시간만 기록","기기 값도 기록"),if(draft.device)"기기 값도 기록"else"시간만 기록") { v->model.edit { it.copy(device=v=="기기 값도 기록") } }
                if(draft.device) {
                    CardioField("기기 이름",draft.deviceName,{v->model.edit { it.copy(deviceName=v) }})
                    CardioField("표시된 칼로리 · kcal",draft.kcal,{v->model.edit { it.copy(kcal=v) }},true)
                    Text("기기의 표시 기준")
                    listOf("ACTIVE" to "활동 칼로리","TOTAL" to "총 칼로리","UNKNOWN" to "잘 모르겠어요").forEach { (value,label)->
                        Choice(label,selected=draft.energyKind==value){model.edit { it.copy(energyKind=value) }}
                    }
                    MutedText("기기에 적힌 값을 그대로 남겨요. 실제 소비량과 차이가 있을 수 있어요.")
                } else MutedText("칼로리를 몰라도 괜찮아요. 시간만 남길 수 있어요.")
            }
            UiCard {
                SectionTitle("기억할 운동 조건")
                Text("표준 소비 열량 추정 · 선택")
                listOf("17355" to "트레드밀 걷기 · 4.8~5.5 km/h · 경사 0%","17358" to "트레드밀 걷기 · 5.6~6.3 km/h · 경사 0%",
                    "01214" to "실내 자전거 · 50 W","01220" to "실내 자전거 · 90~100 W","02048" to "일립티컬 · 보통 강도").forEach { (code,label)->
                    Choice(label,selected=draft.metCode==code){model.edit { it.copy(metCode=if(it.metCode==code)null else code) }}
                }
                MutedText("실제로 수행한 조건과 일치할 때만 선택해주세요. 19~59세 표준 MET와 해당 날짜 이전 체중을 사용해요. 경사나 부하가 다르면 선택하지 마세요.")
                UiButton(if(options)"추가 항목 접기"else"속도·경사·체감 강도 추가",{options=!options},false)
                if(options) {
                    CardioField("속도 · km/h · 선택",draft.speed,{v->model.edit { it.copy(speed=v) }},true)
                    CardioField("경사 · % · 선택",draft.incline,{v->model.edit { it.copy(incline=v) }},true)
                    CardioField("거리 · km · 선택",draft.distance,{v->model.edit { it.copy(distance=v) }},true)
                    MutedText("단일 값으로 남겨요. 중간에 달라진 조건은 메모에 적거나 기록을 나눠주세요.")
                    Text("운동 중 체감 강도 · 1 아주 가벼움 — 10 최대")
                    Chips((1..10).map(Int::toString),draft.effort?.toString().orEmpty()){v->model.edit { it.copy(effort=v.toInt()) }}
                    TextButton(onClick={model.edit { it.copy(effort=null) }}){Text("체감 강도 선택 해제")}
                    Text("운동 후 피로")
                    listOf("LOW" to "낮음","MODERATE" to "보통","HIGH" to "높음").forEach { (value,label)->
                        Choice(label,selected=draft.fatigue==value){model.edit { it.copy(fatigue=if(it.fatigue==value)null else value) }}
                    }
                }
                CardioField("메모 · 선택",draft.memo,{v->model.edit { it.copy(memo=v) }},multiline=true)
            }
            UiButton("유산소 기록 저장",model::save)
            UiButton("작성 내용 닫기",{discarding=true},false)
        }
        MutedText("걸음 수와 겹칠 수 있어 소비 칼로리를 합산하지 않아요. 식단 목표에도 자동으로 더하지 않아요.")
    }
    if(state.busy)Dialog(onDismissRequest={},properties=DialogProperties(dismissOnBackPress=false,dismissOnClickOutside=false)) {
        UiCard { CircularProgressIndicator(color=DeepBlue);Text("기록을 저장하고 있어요") }
    }
    removing?.let { record->AlertDialog(onDismissRequest={removing=null},title={Text("이 기록을 삭제할까요?")},text={Text("${record.values.activity} · ${record.values.minutes}분 기록이 삭제돼요.")},
        confirmButton={TextButton(onClick={removing=null;model.delete(record)}){Text("삭제")}},dismissButton={TextButton(onClick={removing=null}){Text("취소")}}) }
    if(discarding)AlertDialog(onDismissRequest={discarding=false},title={Text("작성 내용을 닫을까요?")},text={Text("저장하지 않은 변경은 사라져요. 이미 저장한 기록은 남아 있어요.")},
        confirmButton={TextButton(onClick={discarding=false;model.discard()}){Text("작성 내용 버리기")}},dismissButton={TextButton(onClick={discarding=false}){Text("계속 작성")}})
}

@Composable private fun CardioField(label:String,value:String,change:(String)->Unit,numeric:Boolean=false,multiline:Boolean=false) {
    OutlinedTextField(value=value,onValueChange=change,label={Text(label)},modifier=Modifier.fillMaxWidth(),singleLine=!multiline,
        shape=androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        colors=OutlinedTextFieldDefaults.colors(unfocusedContainerColor=androidx.compose.ui.graphics.Color.White,
            focusedContainerColor=androidx.compose.ui.graphics.Color.White,unfocusedBorderColor=com.bapegg.routinlog.ui.theme.Border,focusedBorderColor=DeepBlue),
        minLines=if(multiline)3 else 1,keyboardOptions=KeyboardOptions(keyboardType=if(numeric)KeyboardType.Decimal else KeyboardType.Text))
}
@Composable internal fun CardioDetails(record:CardioDto) {
    val v=record.values
    SectionTitle(v.activity)
    KeyValue("운동 시간","${v.minutes}분")
    if(v.deviceKcal!=null) {
        val kind=when(v.energyKind){"ACTIVE"->"활동";"TOTAL"->"총";else->"기준 미확인"}
        KeyValue("기기 표시 · $kind","${v.deviceKcal.stripTrailingZeros().toPlainString()} kcal")
        MutedText(v.deviceName.orEmpty())
    } else MutedText("소비 칼로리 미기록")
    v.speedKmh?.let { KeyValue("속도","${it.stripTrailingZeros().toPlainString()} km/h") }
    v.inclinePercent?.let { KeyValue("경사","${it.stripTrailingZeros().toPlainString()} %") }
    v.distanceKm?.let { KeyValue("거리","${it.stripTrailingZeros().toPlainString()} km") }
    v.effort?.let { KeyValue("체감 강도","$it / 10") }
    v.fatigue?.let { KeyValue("운동 후 피로",when(it){"LOW"->"낮음";"HIGH"->"높음";else->"보통"}) }
    v.memo?.let { MutedText(it) }
    record.estimate?.let { estimate->
        SectionTitle("표준 MET 추정")
        MutedText(estimate.label)
        KeyValue("총 소비 추정","${estimate.totalKcal} kcal")
        KeyValue("휴식분 제외 추정","${estimate.activeKcal} kcal")
        MutedText("${estimate.met} MET × ${estimate.weightKg} kg × 시간(h). 체중 기준: ${estimate.weightSource}. 휴식분은 1 MET를 제외했어요.")
        MutedText("2024 Adult Compendium · 개인 체력·피로·동작 효율의 차이를 정밀하게 반영하지 않아요. 기기 값이나 걸음 수와 합산하지 않아요.")
        val uri=androidx.compose.ui.platform.LocalUriHandler.current
        TextButton(onClick={uri.openUri(estimate.sourceUrl)}){Text("추정 근거 보기")}
    }
}
@Composable internal fun CardioReport(records:List<CardioDto>) {
    UiCard {
        SectionTitle("이번 주 유산소")
        if(records.isEmpty())MutedText("이번 주에 남긴 유산소 기록이 없어요.") else {
            KeyValue("기록한 시간","${records.sumOf { it.values.minutes }}분")
            KeyValue("기록 횟수 · 날짜","${records.size}회 · ${records.map { it.date }.distinct().size}일")
            MutedText("기기 칼로리는 개별 기록에서 확인해요. 서로 다른 표시 기준이나 걸음 기록과 합산하지 않아요.")
        }
    }
    records.forEach { record->
        var open by remember(record.id,record.version){mutableStateOf(false)}
        UiCard {
            UiRow(record.date,"${record.values.activity} · ${record.values.minutes}분",if(open)"접기"else"보기",onClick={open=!open})
            if(open)CardioDetails(record)
        }
    }
}
