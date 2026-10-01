package com.bapegg.routinlog.ui

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
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
import androidx.compose.ui.platform.LocalContext
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
import com.bapegg.routinlog.data.BodyMeasurementWriteDto
import com.bapegg.routinlog.domain.BodyMeasurementInput
import java.math.RoundingMode

@Composable fun RoutineLogApp(model:RoutineLogViewModel,initialRoute:String?=null,accountModel:AccountViewModel?=null,mealModel:MealViewModel?=null,workoutModel:WorkoutViewModel?=null,conditionModel:ConditionViewModel?=null,stepsModel:StepsViewModel?=null,reportModel:ReportViewModel?=null,reviewModel:WorkoutReviewViewModel?=null) {
    val ui:PreviewSession=viewModel()
    val accountState = accountModel?.state?.collectAsStateWithLifecycle()?.value ?: AccountUiState(initializing=false)
    LaunchedEffect(accountState.userId,accountState.ready,accountState.profile?.timeZone) {
        reportModel?.bind(accountState.userId.takeIf { accountState.ready && accountState.profile!=null },accountState.profile?.timeZone)
    }
    LaunchedEffect(ui.route,accountState.ready,accountState.userId,accountState.profile?.version) {
        if(ui.accountMode && ui.route=="R01" && accountState.ready)reportModel?.refresh()
    }
    LaunchedEffect(accountState.userId,accountState.ready,accountState.profile?.units) {
        reviewModel?.bind(accountState.userId.takeIf { accountState.ready && accountState.profile!=null },accountState.profile?.units ?: "METRIC")
    }
    LaunchedEffect(ui.route,ui.get("review.appliedDate")) {
        if(ui.accountMode && ui.route=="W01" && ui.get("review.appliedDate").isNotBlank()) {
            val date=ui.get("review.appliedDate");ui.set("review.appliedDate", "");workoutModel?.loadDate(date)
        }
    }
    val stepsState=stepsModel?.state?.collectAsStateWithLifecycle()?.value
    LaunchedEffect(accountState.userId,accountState.initializing,accountState.busy,accountState.profile?.timeZone) {
        if(!accountState.initializing&&!accountState.busy)stepsModel?.bind(accountState.userId.takeIf { accountState.ready&&accountState.profile!=null },accountState.profile?.timeZone)
    }
    LaunchedEffect(ui.get("home.date"),stepsState?.busy,stepsState?.owner) {
        if(ui.accountMode&&stepsState?.busy==false)stepsModel?.selectDate(ui.get("home.date",ui.today().toString()))
    }
    val lifecycleOwner=LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner,stepsModel) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            delay(1000)
            while(true){stepsModel?.refresh();delay(60_000)}
        }
    }
    val conditionState = conditionModel?.state?.collectAsStateWithLifecycle()?.value
    LaunchedEffect(accountState.userId,accountState.ready,accountState.profile?.timeZone) {
        conditionModel?.bind(accountState.userId.takeIf { accountState.ready && accountState.profile!=null },accountState.profile?.timeZone)
    }
    LaunchedEffect(ui.get("home.date"),ui.accountMode,accountState.userId,accountState.ready,accountState.profile?.timeZone) {
        if(ui.accountMode && accountState.ready && accountState.profile!=null)conditionModel?.selectDate(ui.get("home.date",LocalDate.now(java.time.ZoneId.of(accountState.profile.timeZone)).toString()))
    }
    LaunchedEffect(conditionState?.notice) { conditionState?.notice?.let { ui.notify(it);conditionModel?.clearNotice() } }
    val context = LocalContext.current
    val mealState = mealModel?.state?.collectAsStateWithLifecycle()?.value
    val workoutState = workoutModel?.state?.collectAsStateWithLifecycle()?.value
    LaunchedEffect(accountState.userId, accountState.ready, accountState.profile?.timeZone, accountState.profile?.version) {
        mealModel?.bind(accountState.userId.takeIf { accountState.ready && accountState.profile != null }, accountState.profile?.timeZone, accountState.profile?.version)
    }
    LaunchedEffect(mealState?.notice) { mealState?.notice?.let { ui.notify(it); mealModel?.clearNotice() } }
    LaunchedEffect(accountState.userId, accountState.ready, accountState.profile?.timeZone, accountState.profile?.units) {
        workoutModel?.bind(accountState.userId.takeIf { accountState.ready && accountState.profile != null }, accountState.profile?.timeZone, accountState.profile?.units ?: "METRIC")
    }
    LaunchedEffect(workoutState?.notice) { workoutState?.notice?.let { ui.notify(it); workoutModel?.clearNotice() } }
    LaunchedEffect(accountState.routingVersion) {
        if(accountState.routingVersion>0 && !ui.previewMode && initialRoute==null) {
            if(accountState.ready && accountState.userId!=null) {
                AccountDrafts.begin(ui,accountState.profile);AccountDrafts.records(ui,accountState.records)
            } else ui.reset()
        }
    }
    LaunchedEffect(accountState.records,ui.accountMode) {
        if(ui.accountMode)AccountDrafts.records(ui,accountState.records)
    }
    LaunchedEffect(accountState.notice) { accountState.notice?.let{ui.notify(it);accountModel?.clearNotice()} }
    val actions=AccountActions(
        state=accountState,
        login={
            var current=context
            while(current is android.content.ContextWrapper && current !is android.app.Activity)current=current.baseContext
            val activity=current as? android.app.Activity
            if(activity!=null&&accountModel!=null){ui.previewMode=false;accountModel.login(activity)}
            else ui.notify("로그인을 실행할 수 없어요. 앱을 다시 열어주세요.")
        },
        refresh={accountModel?.refresh { profile ->
            if(profile!=null)AccountDrafts.apply(ui,profile)else AccountDrafts.begin(ui,null)
        }},
        resume={ui.reset();accountModel?.refresh(navigate=true)},
        saveProfile={
            runCatching {AccountDrafts.profile(ui)}.onSuccess {draft->
                accountModel?.saveProfile(draft){saved->AccountDrafts.apply(ui,saved);ui.go("H01")}
            }.onFailure {ui.notify(it.message?:"시작 설정을 확인해주세요.")}
        },
        loadDate={date->accountModel?.loadDate(date){record->
            if(ui.accountMode&&ui.get("body.date",ui.today().toString())==date){AccountDrafts.bodyDraft(ui,record);ui.set("body.loadedDate",date)}
        }},
        loadRange={from,to->accountModel?.loadRange(from,to)},
        saveBody={
            val date=runCatching {LocalDate.parse(ui.get("body.date",ui.today().toString()))}.getOrNull()
            if(date==null||date !in LocalDate.of(1900,1,1)..ui.today())ui.notify("1900년부터 오늘까지의 측정 날짜를 선택해주세요.") else {
                val result=BodyMeasurementInput.validate(date,ui.get("body.draftWeight"),ui.get("body.draftWaist"))
                val record=result.measurement
                if(record==null)ui.set("body.error",listOfNotNull(result.formError,result.weightError,result.waistError).joinToString("\n"))
                else if(ui.get("body.draftMemo").length>1000)ui.set("body.error","메모는 1,000자까지 입력해주세요.")
                else accountModel?.saveBody(date.toString(),BodyMeasurementWriteDto(
                    weightKg=record.weightKg?.setScale(3,RoundingMode.HALF_UP)?.toDouble(),
                    waistCm=record.waistCm?.setScale(2,RoundingMode.HALF_UP)?.toDouble(),
                    version=ui.get("body.draftVersion").toLongOrNull(),memo=ui.get("body.draftMemo").takeIf{it.isNotBlank()}
                )){ui.set("home.date",date.toString());ui.go("H01")}
            }
        },
        deleteBody={ui.get("body.draftVersion").toLongOrNull()?.let{version->
            accountModel?.deleteBody(ui.get("body.date"),version){ui.go("H05")}
        }},
        logout={accountModel?.logout{ui.reset()}},
        deleteAccount={
            var current=context
            while(current is android.content.ContextWrapper&&current !is android.app.Activity)current=current.baseContext
            (current as? android.app.Activity)?.let{activity->accountModel?.deleteAccount(activity){ui.reset();ui.notify("계정과 저장된 기록을 삭제했어요.")}}
        },
    )
    CompositionLocalProvider(LocalAccount provides actions,LocalSteps provides stepsModel,LocalConditions provides conditionModel,LocalReports provides reportModel,LocalWorkoutReviews provides reviewModel) { RoutineLogContent(model,initialRoute,ui,mealModel,workoutModel) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun RoutineLogContent(model:RoutineLogViewModel,initialRoute:String?,ui:PreviewSession,mealModel:MealViewModel?,workoutModel:WorkoutViewModel?) {
    val account=LocalAccount.current
    val connection by model.state.collectAsStateWithLifecycle()
    var catalog by remember { mutableStateOf(false) }
    val snackbar=remember { SnackbarHostState() }
    LaunchedEffect(initialRoute){if(BuildConfig.DEBUG && initialRoute in ScreenCatalog){ui.previewMode=true;ui.go(initialRoute!!)}}
    LaunchedEffect(ui.message){ui.message?.let {snackbar.showSnackbar(it);ui.message=null}}
    BackHandler(enabled=ui.route!="A01"){ui.back()}
    val sheet=(ui.route=="R06" && !ui.accountMode) || (ui.route=="F04" && !ui.accountMode)
    val base=if(sheet)ScreenCatalog.getValue(ui.route).back!! else ui.route
    val keyboard=WindowInsets.ime.getBottom(LocalDensity.current)>0
    Scaffold(containerColor=Silver,snackbarHost={SnackbarHost(snackbar)},topBar={
        Column(Modifier.statusBarsPadding()) {
            if(ui.previewMode || BuildConfig.DEBUG)Row(Modifier.fillMaxWidth().background(Color(0xFFE4EAEF)).padding(start=20.dp,end=8.dp),verticalAlignment=Alignment.CenterVertically) {
                Text(if(ui.previewMode)"샘플 체험 · 실제 기록은 저장되지 않아요" else if(ui.accountMode)"내 계정 · 온라인 기록" else "개발 버전",Modifier.weight(1f),fontSize=11.sp,color=Muted)
                if(BuildConfig.DEBUG)TextButton(onClick={catalog=true},contentPadding=PaddingValues(horizontal=10.dp,vertical=0.dp),modifier=Modifier.heightIn(min=40.dp)){Text("화면 목록",fontSize=11.sp)}
            }
            ScreenHeader(base,ui)
        }
    },bottomBar={if(!keyboard)Column(Modifier.navigationBarsPadding().background(Color(0xFFF8FAFC))) {
        if(hasFooter(base)&&(!ui.accountMode||base in setOf("A02","A03","A04","A05","A06","A07","A08","H04","S02","E01")))Column(Modifier.padding(horizontal=20.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){ScreenFooter(base,ui)}
        if(ScreenCatalog[base]?.tab?.isNotBlank()==true)AppTabs(base,ui)
    }}) {padding->
        Box(Modifier.fillMaxSize().padding(padding).imePadding(),contentAlignment=Alignment.TopCenter) {
            AnimatedContent(targetState=base,label="screen",transitionSpec={fadeIn() togetherWith fadeOut()}) {id->
                Column(Modifier.widthIn(max=600.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=20.dp,vertical=14.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    if(account.state.error!=null&&!ui.previewMode)UiCard {
                        Text(account.state.error,color=MaterialTheme.colorScheme.error)
                        if(!account.state.ready)UiButton("다시 연결",account.resume,primary=false)
                        else if(ui.accountMode&&id=="H04")UiButton("최신 기록 다시 불러오기",{account.loadDate(ui.get("body.date",ui.today().toString()))},primary=false)
                    }
                    ScreenContent(id,ui,mealModel,workoutModel);Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
    if(sheet)ModalBottomSheet(onDismissRequest={ui.back()},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),containerColor=Silver) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically){Text(ScreenCatalog.getValue(ui.route).title,Modifier.weight(1f),style=MaterialTheme.typography.titleLarge);IconButton(onClick={ui.back()},modifier=Modifier.semantics{contentDescription="닫기"}){UiIcon("X")}}
            ScreenContent(ui.route,ui,mealModel,workoutModel);ScreenFooter(ui.route,ui);Spacer(Modifier.height(12.dp))
        }
    }
    if(catalog)AlertDialog(onDismissRequest={catalog=false},confirmButton={TextButton(onClick={catalog=false}){Text("닫기")}},title={Text("전체 화면 · ${ScreenCatalog.size}개")},text={
        LazyColumn(Modifier.heightIn(max=530.dp)) {
            item{Text("가상 데이터로 디자인과 화면 흐름을 확인해요.",color=Muted)}
            item{TextButton(onClick=model::checkStatus,enabled=!connection.checkingStatus){Text("개발 서버 연결 확인")};Text(connection.statusMessage,fontSize=12.sp,color=Muted)}
            items(ScreenCatalog.values.toList()){s->TextButton(onClick={catalog=false;if(!ui.previewMode)ui.reset();ui.previewMode=true;ui.go(s.id)},modifier=Modifier.fillMaxWidth()){Text("${s.id}   ${s.title}",Modifier.fillMaxWidth(),color=Ink)}}
        }
    })
    if(account.state.initializing||account.state.busy)androidx.compose.ui.window.Dialog(onDismissRequest={},properties=androidx.compose.ui.window.DialogProperties(dismissOnBackPress=false,dismissOnClickOutside=false)) {
        UiCard { Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator(Modifier.size(28.dp),color=DeepBlue,strokeWidth=3.dp)
            Text(if(account.state.initializing)"내 기록 확인 중…"else"연결 중…")
        } }
    }
}
@Composable private fun ScreenContent(id:String,ui:PreviewSession,mealModel:MealViewModel?,workoutModel:WorkoutViewModel?){
    // The outgoing animated screen must never switch to sample data after sign-out.
    if(!ui.accountMode && !ui.previewMode && id!="A01")return
    if(ui.accountMode && id in setOf("R05","R06","R07","R08","R09","R11")) { LocalWorkoutReviews.current?.let { LiveWorkoutReviewScreens(id,ui,it) } ?: LiveFeaturePending(ui);return }
    if(ui.accountMode && id in setOf("R01","R02","R03","R04")) { LocalReports.current?.let { LiveReportScreens(id,ui,it) } ?: LiveFeaturePending(ui);return }
    if(ui.accountMode && id in setOf("H06","S03")) { LocalSteps.current?.let { LiveStepsScreen(ui,it) } ?: LiveFeaturePending(ui);return }
    if(ui.accountMode && id=="H07") { LocalConditions.current?.let { LiveConditionScreen(ui,it) } ?: LiveFeaturePending(ui);return }
    if(ui.accountMode && id in setOf("F01","F02","F03","F04","F06","F07","F08","F13","F14") && mealModel!=null) {
        LiveFoodScreens(id,ui,mealModel);return
    }
    if(ui.accountMode && id in setOf("W01","W04","W05","W06","W07","W08","W09","W10","W11","W13","W14","W16") && workoutModel!=null) {
        LiveWorkoutScreens(id,ui,workoutModel);return
    }
    if(ui.accountMode&&id !in setOf("A01","A02","A03","A04","A05","A06","A07","A08","H01","H02","H03","H04","H05","S01","S02","S04","S06","E01")) {
        LiveFeaturePending(ui);return
    }
    when(id.first()){'A','H'->HomeScreens(id,ui);'F'->FoodScreens(id,ui);'W'->WorkoutScreens(id,ui);else->ReportScreens(id,ui)}
}
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
                val title=if(id=="H01")runCatching{LocalDate.parse(ui.get("home.date",ui.today().toString())).format(DateTimeFormatter.ofPattern("M월 d일"))}.getOrDefault("오늘")else if(id=="F01")"오늘 식단"else if(ui.accountMode)when(id){"R01"->"한 주의 루틴";"R05"->"다음 수행 초안";"R06"->"내 목표로 수정";"R09"->"지난 초안과 내 선택";"W01"->"나의 운동";"W06"->"운동 정보·내 기록";"W08"->"운동 기록";"W09"->"세트 기록";"W10"->"세트 사이 휴식";"W14"->"날짜별 일정 변경";else->screen.title}else screen.title
                Text(title,style=if(root)MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,modifier=Modifier.semantics{heading()}.then(if(id=="H01")Modifier.clickable{ui.go("H03")}else Modifier))
                if(root&&id=="H01")Text("나의 일상을 차곡차곡",fontSize=12.sp,color=Muted)
            }
            if(root && !(ui.accountMode && id in setOf("W01","R01")))Surface(shape=RoundedCornerShape(12.dp),border=BorderStroke(1.dp,Border),color=Color.White){IconButton(onClick={ui.go(if(id=="H01")"S01"else"H03")},modifier=Modifier.semantics{contentDescription=if(id=="H01")"설정"else"달력"}){UiIcon(if(id=="H01")"Settings"else"CalendarDays")}}
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
