package com.bapegg.routinlog.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bapegg.routinlog.steps.StepAvailability
import com.bapegg.routinlog.ui.*
import com.bapegg.routinlog.ui.theme.*
import kotlinx.coroutines.delay
import java.time.*
import java.time.format.DateTimeFormatter

@Composable internal fun LiveStepsScreen(ui:PreviewSession,model:StepsViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val context=LocalContext.current
    var consent by remember { mutableStateOf(false) }
    var stopping by remember { mutableStateOf(false) }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->if(granted)model.connect()else model.permissionDenied()}
    val row=state.days.firstOrNull { it.date==state.date }
    UiCard(dark=true) {
        Badge(if(state.connected)"이 휴대폰 연결됨"else"연결 전")
        SectionTitle(state.date)
        AnimatedContent(row?.steps,label="recorded-steps") { count->HeroNumber(count?.let { "%,d".format(it) } ?: "—","걸음") }
        MutedText(if(row==null)"아직 가져온 걸음 기록이 없어요."else"휴대폰에서 가져와 저장한 걸음이에요.")
        row?.let { MutedText("마지막 기록 ${Instant.parse(it.through).atZone(ZoneId.of(it.timeZones.first())).format(DateTimeFormatter.ofPattern("M/d HH:mm"))}") }
    }
    state.error?.let { UiCard { Text(it,color=MaterialTheme.colorScheme.error) } }
    if(state.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
    UiCard {
        SectionTitle("이번 주 걸음")
        val date=LocalDate.parse(state.date)
        val monday=date.minusDays((date.dayOfWeek.value-1).toLong())
        val days=(0L..6L).map(monday::plusDays)
        StepWeekChart(days.map { day->state.days.firstOrNull { it.date==day.toString() }?.steps })
        MutedText("—는 미기록이에요. 연결한 시점부터 수집하며, 휴대하지 않은 시간이나 연동이 끊긴 구간은 빠질 수 있어요.")
    }
    UiCard {
        SectionTitle("걸음 연결")
        BodyText("신체 활동 권한을 허용하면 앱을 닫은 동안에도 휴대폰에서 걸음을 모아요. 일별 걸음과 수집 구간을 내 계정에 저장해요.")
        MutedText("GPS 위치·이동 경로는 수집하지 않아요. 워치나 다른 앱의 걸음과 합치지 않아요.")
        if(state.availability==StepAvailability.UNSUPPORTED)BodyText("이 기기에서는 걸음 센서를 사용할 수 없어요. 식단과 운동은 계속 기록할 수 있어요.")
        else if(state.availability==StepAvailability.SERVICES_REQUIRED)BodyText("Google Play 서비스를 설치하거나 업데이트한 후 다시 확인해주세요.")
        else if(!state.connected)UiButton("이 휴대폰 걸음 연결",{consent=true},enabled=!state.busy)
        if(state.connected)UiButton("걸음 연결 해제",{stopping=true},primary=false,enabled=!state.busy)
        UiButton("지금 새로고침",{model.refresh()},primary=false,enabled=!state.busy)
        if(state.availability==StepAvailability.PERMISSION_REQUIRED)UiButton("앱 권한 설정 열기",{
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${context.packageName}")))
        },primary=false)
    }
    UiCard {
        SectionTitle("기록이 갱신되는 시점")
        BodyText("앱을 열었을 때와 사용 중 주기적으로 확인해요. 백그라운드에서도 인터넷이 연결되면 동기화를 시도해요.")
        MutedText("절전·강제 종료·긴 미접속으로 갱신이 늦어질 수 있어요. 휴대폰의 최근 약 10일 보관 범위를 벗어난 미동기화 기록은 복구되지 않을 수 있어요.")
        MutedText("걸음 수만으로 소비 열량이나 운동 완료를 판단하지 않아요.")
    }
    if(state.days.isNotEmpty()) {
        SectionTitle("최근 가져온 기록")
        state.days.forEach { day->UiCard {
            KeyValue(day.date,"%,d걸음".format(day.steps))
            MutedText("${day.timeZones.joinToString()} · ${day.segments}개 수집 구간")
        } }
    }
    if(consent)AlertDialog(onDismissRequest={consent=false},title={Text("이 휴대폰의 걸음을 저장할까요?")},
        text={Text("허용한 이후의 일별 걸음과 수집 구간을 내 계정에 저장해요. 다른 휴대폰이 연결돼 있다면 이 휴대폰으로 바뀝니다. 언제든 연결을 해제할 수 있어요.")},
        confirmButton={TextButton(onClick={consent=false;if(Build.VERSION.SDK_INT>=29&&state.availability==StepAvailability.PERMISSION_REQUIRED)permission.launch(Manifest.permission.ACTIVITY_RECOGNITION)else model.connect()}){Text("허용하고 연결")}},
        dismissButton={TextButton(onClick={consent=false}){Text("나중에")}})
    if(stopping)AlertDialog(onDismissRequest={stopping=false},title={Text("걸음 연결을 해제할까요?")},text={Text("이 휴대폰의 수집과 동기화를 멈춰요. 이미 내 계정에 저장된 기록은 유지돼요.")},
        confirmButton={TextButton(onClick={stopping=false;model.disconnect()}){Text("해제")}},dismissButton={TextButton(onClick={stopping=false}){Text("취소")}})
}

@Composable private fun StepWeekChart(values:List<Long?>) {
    val maximum=(values.filterNotNull().maxOrNull() ?: 1).coerceAtLeast(1)
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
        values.forEachIndexed { index,value->
            val height=remember(values){Animatable(0f)}
            LaunchedEffect(values){delay(index*230L);height.animateTo((value ?: 0).toFloat()/maximum,tween(230))}
            val day=listOf("월","화","수","목","금","토","일")[index]
            Column(Modifier.weight(1f).semantics { contentDescription="$day ${value?.let { "${it}걸음" } ?: "미기록"}" },horizontalAlignment=Alignment.CenterHorizontally) {
                Box(Modifier.height(110.dp).fillMaxWidth(),contentAlignment=Alignment.BottomCenter) {
                    if(value!=null&&value>0)Box(Modifier.fillMaxWidth(.65f).height((110*height.value).dp).background(Celery,RoundedCornerShape(6.dp)))
                }
                Text(day,fontSize=12.sp)
                Text(value?.toString() ?: "—",fontSize=10.sp)
            }
        }
    }
}
