package com.bapegg.routinlog.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bapegg.routinlog.BuildConfig
import com.bapegg.routinlog.ui.screens.*
import com.bapegg.routinlog.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun RoutineLogApp(model:RoutineLogViewModel,initialRoute:String?=null) {
    val ui:PreviewSession=viewModel()
    val connection by model.state.collectAsStateWithLifecycle()
    var catalog by remember { mutableStateOf(false) }
    val snackbar=remember { SnackbarHostState() }
    LaunchedEffect(initialRoute){if(BuildConfig.DEBUG && initialRoute in ScreenCatalog){ui.previewMode=true;ui.go(initialRoute!!)}}
    LaunchedEffect(ui.message){ui.message?.let {snackbar.showSnackbar(it);ui.message=null}}
    BackHandler(enabled=ui.route!="A01"){ui.back()}
    val sheet=ui.route in listOf("F04","R06")
    val base=if(sheet)ScreenCatalog.getValue(ui.route).back!! else ui.route
    val keyboard=WindowInsets.ime.getBottom(LocalDensity.current)>0
    Scaffold(containerColor=Silver,snackbarHost={SnackbarHost(snackbar)},topBar={
        Column(Modifier.statusBarsPadding()) {
            if(ui.previewMode || BuildConfig.DEBUG)Row(Modifier.fillMaxWidth().background(Color(0xFFE4EAEF)).padding(start=20.dp,end=8.dp),verticalAlignment=Alignment.CenterVertically) {
                Text(if(ui.previewMode)"샘플 체험 · 실제 기록은 저장되지 않아요" else "디자인 구현 확인",Modifier.weight(1f),fontSize=11.sp,color=Muted)
                if(BuildConfig.DEBUG)TextButton(onClick={catalog=true},contentPadding=PaddingValues(horizontal=10.dp,vertical=0.dp),modifier=Modifier.heightIn(min=40.dp)){Text("화면 목록",fontSize=11.sp)}
            }
            ScreenHeader(base,ui)
        }
    },bottomBar={if(!keyboard)Column(Modifier.navigationBarsPadding().background(Color(0xFFF8FAFC))) {
        if(hasFooter(base))Column(Modifier.padding(horizontal=20.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){ScreenFooter(base,ui)}
        if(ScreenCatalog[base]?.tab?.isNotBlank()==true)AppTabs(base,ui)
    }}) {padding->
        Box(Modifier.fillMaxSize().padding(padding).imePadding(),contentAlignment=Alignment.TopCenter) {
            AnimatedContent(targetState=base,label="screen",transitionSpec={fadeIn() togetherWith fadeOut()}) {id->
                Column(Modifier.widthIn(max=600.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=20.dp,vertical=14.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    ScreenContent(id,ui);Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
    if(sheet)ModalBottomSheet(onDismissRequest={ui.back()},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),containerColor=Silver) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically){Text(ScreenCatalog.getValue(ui.route).title,Modifier.weight(1f),style=MaterialTheme.typography.titleLarge);IconButton(onClick={ui.back()},modifier=Modifier.semantics{contentDescription="닫기"}){UiIcon("X")}}
            ScreenContent(ui.route,ui);ScreenFooter(ui.route,ui);Spacer(Modifier.height(12.dp))
        }
    }
    if(catalog)AlertDialog(onDismissRequest={catalog=false},confirmButton={TextButton(onClick={catalog=false}){Text("닫기")}},title={Text("전체 화면 · ${ScreenCatalog.size}개")},text={
        LazyColumn(Modifier.heightIn(max=530.dp)) {
            item{Text("가상 데이터로 디자인과 화면 흐름을 확인해요.",color=Muted)}
            item{TextButton(onClick=model::checkStatus,enabled=!connection.checkingStatus){Text("개발 서버 연결 확인")};Text(connection.statusMessage,fontSize=12.sp,color=Muted)}
            items(ScreenCatalog.values.toList()){s->TextButton(onClick={catalog=false;ui.previewMode=true;ui.go(s.id)},modifier=Modifier.fillMaxWidth()){Text("${s.id}   ${s.title}",Modifier.fillMaxWidth(),color=Ink)}}
        }
    })
}
@Composable private fun ScreenContent(id:String,ui:PreviewSession){when(id.first()){'A','H'->HomeScreens(id,ui);'F'->FoodScreens(id,ui);'W'->WorkoutScreens(id,ui);else->ReportScreens(id,ui)}}
@Composable private fun ScreenFooter(id:String,ui:PreviewSession){when(id.first()){'A','H'->HomeFooter(id,ui);'F'->FoodFooter(id,ui);'W'->WorkoutFooter(id,ui);else->ReportFooter(id,ui)}}
private fun hasFooter(id:String)=id !in setOf("A01","H01","H02","H03","H05","H06","F01","F02","F06","F08","R08","R09","S01","S03","S04","E02")
@Composable private fun ScreenHeader(id:String,ui:PreviewSession) {
    if(id=="A01")return
    val screen=ScreenCatalog.getValue(id)
    val step=if(id.startsWith("A"))id.drop(1).toInt()-1 else 0
    val root=id in listOf("H01","F01","W01","R01")
    Column(Modifier.fillMaxWidth().padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        if(!root)Row(verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick={ui.back()},modifier=Modifier.size(48.dp).offset(x=(-12).dp).semantics{contentDescription="뒤로 가기"}){UiIcon("ChevronLeft",tint=Ink)}
            Text(when(id.first()){'A'->"시작 설정";'H'->"오늘의 기록";'F'->"식단";'W'->"운동";'R'->"주간 리포트";else->"설정"},Modifier.weight(1f),fontSize=12.sp,color=Muted)
            if(step>0)Text("$step / 7",fontSize=12.sp,color=Muted)
        }else Spacer(Modifier.height(12.dp))
        Row(verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                if(id=="H01")Text("나만의 루틴로그",color=Muted,fontSize=11.sp)
                val title=if(id=="H01")runCatching{LocalDate.parse(ui.get("home.date",LocalDate.now().toString())).format(DateTimeFormatter.ofPattern("M월 d일"))}.getOrDefault("오늘")else if(id=="F01")"오늘 식단"else screen.title
                Text(title,style=if(root)MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,modifier=Modifier.semantics{heading()}.then(if(id=="H01")Modifier.clickable{ui.go("H03")}else Modifier))
                if(root&&id=="H01")Text("나의 일상을 차곡차곡",fontSize=12.sp,color=Muted)
            }
            if(root)Surface(shape=RoundedCornerShape(12.dp),border=BorderStroke(1.dp,Border),color=Color.White){IconButton(onClick={ui.go(if(id=="H01")"S01"else"H03")},modifier=Modifier.semantics{contentDescription=if(id=="H01")"설정"else"달력"}){UiIcon(if(id=="H01")"Settings"else"CalendarDays")}}
        }
        if(step>0)ProgressLine(step/7f)
        Spacer(Modifier.height(4.dp))
    }
}
@Composable private fun AppTabs(id:String,ui:PreviewSession) {
    Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal=12.dp,vertical=7.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
        listOf(Triple("오늘","H01","House"),Triple("식단","F01","Utensils"),Triple("운동","W01","Dumbbell"),Triple("리포트","R01","ChartNoAxesCombined")).forEach{(label,to,icon)->
            val active=id.first()==to.first()
            Surface(onClick={ui.go(to)},modifier=Modifier.weight(1f).semantics{selected=active;role=Role.Tab},shape=RoundedCornerShape(12.dp),color=if(active)Color(0xFFEEF5E7)else Color(0xFFF2F5F9),border=BorderStroke(1.dp,if(active)Celery else Color(0xFFDCE3EB))) {
                Column(Modifier.heightIn(min=50.dp).padding(vertical=5.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                    Surface(shape=RoundedCornerShape(12.dp),color=if(active)Celery else Color.Transparent){Box(Modifier.width(46.dp).height(28.dp),contentAlignment=Alignment.Center){UiIcon(icon,tint=if(active)Ink else Muted)}}
                    Text(label,fontSize=11.sp,color=if(active)Ink else Muted,fontWeight=if(active)FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}
