package com.bapegg.routinlog.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bapegg.routinlog.data.*
import com.bapegg.routinlog.ui.*
import java.util.UUID

@Composable internal fun FeatureFeedback(model:FeatureViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    if(state.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
    state.error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
}

@Composable internal fun LivePrograms(ui:PreviewSession,model:FeatureViewModel,workouts:WorkoutViewModel?) {
    val state by model.state.collectAsStateWithLifecycle()
    val workout=workouts?.state?.collectAsStateWithLifecycle()?.value
    val account=LocalAccount.current.state
    val uri=LocalUriHandler.current
    LaunchedEffect(state.owner){model.catalog();workouts?.loadCatalog()}
    FeatureFeedback(model)
    if(state.catalog==null){UiButton("프로그램 다시 불러오기",model::catalog,false,!state.busy);return}
    val catalog=state.catalog!!
    val selected=catalog.programs.find { it.id==ui.get("program.live") }
    if(ui.route=="W02"||selected==null) {
        BodyText("목표와 생활에 맞는 시작 루틴을 골라보세요. 적용한 뒤 종목과 세트는 자유롭게 바꿀 수 있어요.")
        catalog.programs.forEach { p->UiCard {
            Badge("주 ${p.sessions.size}회")
            SectionTitle(p.name)
            BodyText(p.audience)
            MutedText(p.purpose)
            UiButton("구성과 근거 보기",{ui.set("program.live",p.id);ui.go("W03")},false)
        } }
        return
    }
    val p=selected
    var days by rememberSaveable(state.owner,p.id) { mutableStateOf(account.profile?.exerciseDays?.takeIf { it.size==p.sessions.size }?.joinToString(",") ?: "") }
    val chosen=days.split(',').mapNotNull(String::toIntOrNull).sorted()
    val requestId=rememberSaveable(state.owner,p.id,days,workout?.plan?.version){UUID.randomUUID().toString()}
    var confirm by remember { mutableStateOf(false) }
    UiCard { SectionTitle(p.name);BodyText(p.purpose);MutedText("${p.author} · ${catalog.revision}");MutedText(p.limitation) }
    p.sessions.forEach { session->UiCard {
        SectionTitle(session.name)
        session.moves.forEach { move->
            val name=workout?.catalog?.items?.find { it.key==move.key }?.name ?: move.key
            KeyValue(name,"${move.sets}세트 × ${move.reps}회")
        }
        MutedText("시작 예시 · 중량은 직접 선택 · 세트 사이 기본 휴식 120초")
    } }
    UiCard {
        SectionTitle("어느 요일에 할까요?")
        (1..7).forEach { d->Choice("${listOf("월","화","수","목","금","토","일")[d-1]}요일",selected=d in chosen) {
            days=(if(d in chosen)chosen-d else chosen+d).sorted().joinToString(",")
        } }
        MutedText("${p.sessions.size}일을 선택해주세요. 선택한 요일 순으로 위 루틴이 배치돼요.")
        if(chosen.zipWithNext().any { (a,b)->b-a==1 }||(1 in chosen&&7 in chosen))MutedText("연속된 운동일이 있어요. 부위가 겹치면 피로와 수행을 보고 휴식·종목을 조정하세요.")
    }
    catalog.evidence.filter { it.id in p.evidenceIds }.forEach { e->UiCard {
        SectionTitle(e.authors);BodyText(e.scope);MutedText(e.limitation)
        UiButton("근거 원문",{uri.openUri(e.url)},false)
    } }
    MutedText("기존 루틴과 수행 기록은 남아요. 오늘부터 기본 요일 계획을 바꾸며, 따로 변경해둔 날짜와 이미 시작한 운동은 유지돼요.")
    UiButton("이 요일로 적용",{confirm=true},enabled=chosen.size==p.sessions.size&&workout?.loaded==true&&!state.busy)
    if(confirm)AlertDialog(onDismissRequest={confirm=false},title={Text("기본 요일 계획을 바꿀까요?")},text={Text("선택한 요일에는 새 루틴을, 나머지 요일에는 휴식을 설정해요. 이전 수행 기록은 유지돼요.")},
        confirmButton={TextButton(onClick={confirm=false;model.apply(ProgramApply(requestId,p.id,chosen,workout?.plan?.version)){workouts?.refresh();ui.go("W01")}}){Text("적용")}},dismissButton={TextButton(onClick={confirm=false}){Text("취소")}})
}

@Composable internal fun LivePreparation(model:FeatureViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.owner){model.preparation()}
    FeatureFeedback(model)
    val saved=state.preparation
    if(saved==null){UiButton("목록 불러오기",model::preparation,false,!state.busy);return}
    var draft by remember(state.owner,saved.version){mutableStateOf(saved.items)}
    var name by rememberSaveable(state.owner){mutableStateOf("")}
    var duration by rememberSaveable(state.owner){mutableStateOf("")}
    BodyText("운동 전 준비와 스트레칭을 저장해두세요. 매번 목록을 다시 만들지 않아도 돼요.")
    draft.forEach { item->UiCard {
        SectionTitle(item.name);item.seconds?.let { MutedText("${it}초") }
        item.note?.let { MutedText(it) }
        UiButton("목록에서 빼기",{draft=draft.filterNot { it.id==item.id }},false,!state.busy)
    } }
    Input("동작 이름",name,{name=it.take(100)})
    Input("시간 · 초 · 선택",duration,{duration=it.take(4)})
    UiButton("준비 동작 추가",{draft=draft+PreparationItem(UUID.randomUUID().toString(),name.trim(),duration.toIntOrNull());name="";duration=""},false,
        !state.busy&&name.isNotBlank()&&draft.size<30&&(duration.isBlank()||duration.toIntOrNull() in 1..3600))
    UiButton("이 목록 저장",{model.save(PreparationDto(draft,saved.version))},enabled=!state.busy)
    UiButton("저장된 목록 다시 불러오기",model::preparation,false,!state.busy)
    MutedText("준비 운동 목록이에요. 실제 수행 세트는 운동 기록에서 워밍업 세트로 구분해 남길 수 있어요.")
}

@Composable internal fun LiveAnalysis(ui:PreviewSession,model:FeatureViewModel,week:String) {
    val state by model.state.collectAsStateWithLifecycle()
    val reviews=LocalWorkoutReviews.current
    val meals=LocalMealReviews.current
    val uri=LocalUriHandler.current
    var consent by remember(state.owner,week){mutableStateOf(false)}
    val result=state.analysis?.takeIf { it.week==week }
    val imperial=LocalAccount.current.state.profile?.units=="IMPERIAL"
    UiCard {
        SectionTitle("최근 4주와 다음 방향")
        FeatureFeedback(model)
        UiButton("내 기록 흐름 분석",{model.analyze(week)},false,!state.busy)
        UiButton("AI로 우선순위 살펴보기",{consent=true},false,!state.busy)
        result?.let { analysis->
            Badge(if(analysis.mode=="AI_RANKED")"AI가 고른 우선순위"else"기록 기반 비교")
            MutedText(analysis.notice)
            analysis.trends.forEach { t->
                KeyValue(t.week,"체중 ${t.weightKg?.let { liveNumber(it*if(imperial)2.2046226218 else 1.0) } ?: "—"} ${if(imperial)"lb"else"kg"} · 허리 ${t.waistCm?.let { liveNumber(it/if(imperial)2.54 else 1.0) } ?: "—"} ${if(imperial)"in"else"cm"}")
                MutedText("식사 ${t.foodDays}일 · 운동 ${t.workoutDays}일 · 걸음 기록 ${t.stepsDays}일")
                MutedText("확인된 열량 ${t.kcal?.let(::liveNumber) ?: "—"} / 목표 ${t.targetKcal?.let(::liveNumber) ?: "—"} kcal (${t.targetDays}일 목표). 식사 기록이 있는 날도 일부 음식이 빠졌을 수 있어요.")
            }
            analysis.candidates.sortedBy { it.id!=analysis.selectedId }.forEach { candidate->
                SectionTitle(candidate.title);BodyText(candidate.explanation)
                UiButton("확인하고 직접 선택",{
                    if(candidate.route=="R05")reviews?.open(week)
                    if(candidate.route=="R12")meals?.open(week)
                    ui.go(candidate.route)
                },false)
                analysis.evidence.filter { it.id in candidate.evidenceIds }.forEach { e->TextButton(onClick={uri.openUri(e.url)}){Text(e.authors)} }
            }
        }
    }
    if(consent)AlertDialog(onDismissRequest={consent=false},title={Text("AI에 주간 요약을 보낼까요?")},
        text={Text("선택한 주와 이전 3주의 체중·허리 평균, 영양 합계, 기록 일수·운동 일수·걸음 합계와 목표를 OpenAI API에 보내요. 이름·이메일·자유 메모·사진은 보내지 않아요. 요청할 때만 보내며 거절해도 기록 비교와 초안을 사용할 수 있어요.")},
        confirmButton={TextButton(onClick={consent=false;model.analyze(week,true)}){Text("이번 요청에 동의")}},dismissButton={TextButton(onClick={consent=false}){Text("기록 비교만 사용")}})
}

@Composable internal fun ExportRecords(model:FeatureViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val context=LocalContext.current
    val owner=state.owner
    var pendingOwner by remember { mutableStateOf<String?>(null) }
    val launcher=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri->
        if(uri!=null&&pendingOwner!=null&&model.state.value.owner==pendingOwner)model.export { text->
            context.contentResolver.openOutputStream(uri,"wt")?.bufferedWriter()?.use { it.write(text) } ?: error("파일 저장 실패")
        }
        pendingOwner=null
    }
    UiCard {
        SectionTitle("내 기록 내보내기")
        MutedText("프로필과 신체·식단·운동 등 개인 기록을 JSON 파일로 저장해요. 민감한 정보가 포함되므로 본인이 관리하는 위치를 선택해주세요.")
        FeatureFeedback(model)
        UiButton("저장 위치 선택",{pendingOwner=owner;launcher.launch("routinlog-records.json")},false,!state.busy&&owner!=null)
    }
}
