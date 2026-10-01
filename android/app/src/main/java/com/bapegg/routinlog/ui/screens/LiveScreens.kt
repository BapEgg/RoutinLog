package com.bapegg.routinlog.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bapegg.routinlog.ui.*
import com.bapegg.routinlog.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable internal fun LiveToday(ui: PreviewSession) {
    val account=LocalAccount.current
    val date=runCatching{LocalDate.parse(ui.get("home.date",ui.today().toString()))}.getOrDefault(ui.today())
    val record=account.state.records.firstOrNull{it.date==date.toString()}
    val imperial=account.state.profile?.units=="IMPERIAL"
    SectionTitle("오늘의 기록", "기록 달력", {ui.go("H03")})
    UiCard(dark=true) {
        Badge(if(record==null)"아직 기록 전"else"기록 완료")
        Text("체중과 허리둘레",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
        MutedText("${date.monthValue}월 ${date.dayOfMonth}일 · 측정한 만큼만 남겨요.")
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f)){MutedText("체중");HeroNumber(record?.weightKg?.let{liveNumber(it*if(imperial)2.2046226218 else 1.0)}?:"—",if(imperial)"lb"else"kg")}
            Column(Modifier.weight(1f)){MutedText("허리둘레");HeroNumber(record?.waistCm?.let{liveNumber(it/if(imperial)2.54 else 1.0)}?:"—",if(imperial)"in"else"cm")}
        }
        UiButton(if(record==null)"오늘 측정값 기록"else"측정값 수정",{prepareBody(ui,date);ui.go("H04")})
    }
    UiCard {
        UiRow("신체 기록 · 변화", "저장된 ${account.state.records.size}일의 측정 기록",icon="ChartNoAxesCombined",onClick={ui.go("H05")})
    }
    UiCard {
        UiRow("식단 기록", "저장한 식사를 불러와 먹은 양만 바꿔요", icon="Utensils", onClick={ui.go("F01")})
    }
    UiCard {
        UiRow("운동 기록", "요일별 루틴과 실제 수행한 세트를 함께 남겨요", icon="Dumbbell", onClick={ui.go("W01")})
    }
    val conditions=LocalConditions.current?.state?.collectAsStateWithLifecycle()?.value
    UiCard { UiRow(if(date==ui.today())"오늘의 컨디션"else"이날의 컨디션",
        conditions?.records?.firstOrNull { it.date==date.toString() }?.let { conditionSummary(it.values) } ?: "수면·피로·근육통·생활 활동을 남겨요",icon="HeartPulse",onClick={ui.go("H07")}) }
    account.state.profile?.let {profile->
        UiCard {
            SectionTitle("내 시작 목표","수정",{ui.go("S02")})
            KeyValue("목적",when(profile.goal){"GAIN"->"근육 증가";"LOSE"->"체중 감량";else->"근육 유지"})
            if(profile.dailyCalories!=null) {
                HeroNumber(profile.dailyCalories.toString(),"kcal / 일")
                KeyValue("탄수화물", "${profile.carbohydrateG?.let(::liveNumber)?:"—"} g")
                KeyValue("단백질", "${profile.proteinG?.let(::liveNumber)?:"—"} g")
                KeyValue("지방", "${profile.fatG?.let(::liveNumber)?:"—"} g")
            } else MutedText("영양 목표 없이 기록을 시작했어요.")
        }
    }
    UiButton("서버에서 새로 불러오기",account.refresh,primary=false)
    MutedText("주간 리포트는 샘플에서 먼저 확인할 수 있어요.")
}

@Composable internal fun LiveBodyHistory(ui:PreviewSession) {
    val account=LocalAccount.current
    val waist=ui.get("body.metric","체중")=="허리둘레"
    val imperial=account.state.profile?.units=="IMPERIAL"
    val period=ui.get("body.period","주간")
    val span=when(period){"월간"->30L;"연간"->365L;else->7L}
    val first=ui.today().minusDays(span-1)
    val records=account.state.records.filter{runCatching{LocalDate.parse(it.date)>=first}.getOrDefault(false)}
    val factor=if(imperial)if(waist)1/2.54 else 2.2046226218 else 1.0
    val unit=if(imperial)if(waist)"in"else"lb"else if(waist)"cm"else"kg"
    val points=records.mapNotNull{r->(if(waist)r.waistCm else r.weightKg)?.let{r to it*factor}}
    Chips(listOf("체중","허리둘레"),if(waist)"허리둘레"else"체중"){ui.set("body.metric",it)}
    Chips(listOf("주간","월간","연간"),period){ui.set("body.period",it)}
    UiCard {
        SectionTitle("기록한 날의 평균")
        HeroNumber(if(points.isEmpty())"—"else liveNumber(points.map{it.second}.average()),unit)
        if(points.size>=2) {
            LineChart(points.reversed().map{it.second.toFloat()},Modifier.fillMaxWidth().height(150.dp))
            KeyValue(points.last().first.date,points.first().first.date)
        }
        MutedText(if(points.isEmpty())"이 기간에는 기록이 없어요."else"${points.size}일 기록 · 측정한 순서로 연결했어요. 미기록일은 평균에서 제외해요.")
    }
    UiCard {
        SectionTitle("측정 기록")
        if(records.isEmpty())MutedText("체중이나 허리둘레 중 하나부터 남겨보세요.")
        records.forEach {record->
            UiRow(record.date,
                "체중 ${record.weightKg?.let{liveNumber(it*if(imperial)2.2046226218 else 1.0)}?:"—"} ${if(imperial)"lb"else"kg"} · 허리 ${record.waistCm?.let{liveNumber(it/if(imperial)2.54 else 1.0)}?:"—"} ${if(imperial)"in"else"cm"}",
                value="수정",onClick={prepareBody(ui,LocalDate.parse(record.date));ui.go("H04")})
        }
    }
    UiButton("측정값 기록",{prepareBody(ui,ui.today());ui.go("H04")})
    UiButton("서버에서 새로 불러오기",account.refresh,primary=false)
}

@Composable internal fun LiveFeaturePending(ui:PreviewSession) {
    UiCard {
        UiIcon("ClipboardList")
        SectionTitle("이 기능은 연결 중이에요.")
        BodyText("시작 설정과 체중·허리·식단·운동 기록은 내 계정에 저장할 수 있어요. 이 화면의 기록 기능도 차례로 연결할게요.")
        UiButton("내 기록으로",{ui.go("H01")})
        UiButton("샘플 화면 둘러보기",{ui.startPreview()},primary=false)
    }
}

@Composable internal fun LiveSettings(ui:PreviewSession,accountPage:Boolean=false) {
    val account=LocalAccount.current
    UiCard {
        Badge("Google 로그인 완료")
        SectionTitle("나의 루틴로그")
        MutedText("시작 설정과 신체·식단·운동·컨디션 기록을 내 계정에 보관해요.")
    }
    if(!accountPage)UiCard {
        UiRow("프로필 · 목표 · 단위", "저장한 기준 확인과 수정",icon="UserRound",onClick={ui.go("S02")})
        UiRow("데이터 · 계정", "계정 연결 관리",icon="ShieldCheck",onClick={ui.go("S04")})
    }
    else UiCard {
        UiRow("계정과 기록 삭제","Google 본인 확인 후 삭제",icon="Trash2",onClick={ui.go("S06")})
        MutedText("파일 내보내기는 추후 제공돼요. 신체 기록은 기록별 수정 화면에서도 삭제할 수 있어요.")
    }
    UiButton("샘플 화면 둘러보기",{ui.startPreview()},primary=false)
    var confirm by remember{mutableStateOf(false)}
    UiButton("로그아웃",{confirm=true},primary=false)
    if(confirm)AlertDialog(onDismissRequest={confirm=false},title={Text("로그아웃할까요?")},text={Text("서버에 저장된 기록은 유지돼요. 이 기기의 로그인 연결을 해제해요.")},
        confirmButton={TextButton(onClick={confirm=false;account.logout()}){Text("로그아웃")}},dismissButton={TextButton(onClick={confirm=false}){Text("취소")}})
}

@Composable internal fun LiveDeleteAccount(ui:PreviewSession) {
    val account=LocalAccount.current
    UiCard {
        SectionTitle("계정과 기록을 삭제할까요?")
        BodyText("프로필·목표 변경 이력·체중·허리둘레·컨디션·수면·메모, 등록 음식·저장 식사·기본 식단·섭취 기록, 등록 운동·루틴·요일 계획과 변경 이력·세트 기록을 모두 삭제해요. 모든 기기의 로그인 연결도 해제해요.")
        MutedText("Google 계정 자체는 삭제하지 않아요. 삭제 후에는 기록을 되돌릴 수 없어요.")
        MutedText("먼저 Google에서 현재 계정의 본인 확인을 진행해요. 다른 계정을 선택하면 삭제되지 않아요.")
    }
    Input("확인을 위해 ‘삭제’를 입력해주세요",ui.get("s.deleteText"),{ui.set("s.deleteText",it)})
    Button(onClick={ui.set("s.liveDeleteConfirm","true")},enabled=ui.get("s.deleteText").trim()=="삭제",
        colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.error),modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){Text("본인 확인하고 계정 삭제")}
    UiButton("취소",{ui.set("s.deleteText","");ui.go("S04")},primary=false)
    if(ui.flag("s.liveDeleteConfirm"))AlertDialog(onDismissRequest={ui.set("s.liveDeleteConfirm","false")},
        title={Text("계정과 기록 영구 삭제")},text={Text("본인 확인에 성공하면 계정과 기록을 바로 삭제해요.")},
        confirmButton={TextButton(onClick={ui.set("s.liveDeleteConfirm","false");account.deleteAccount()}){Text("계속",color=MaterialTheme.colorScheme.error)}},
        dismissButton={TextButton(onClick={ui.set("s.liveDeleteConfirm","false")}){Text("취소")}})
}

internal fun liveNumber(value:Double)=String.format(Locale.US,"%.1f",value)
