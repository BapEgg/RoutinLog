package com.bapegg.routinlog.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bapegg.routinlog.BuildConfig
import com.bapegg.routinlog.ui.theme.Border
import com.bapegg.routinlog.ui.theme.Celery
import com.bapegg.routinlog.ui.theme.Charcoal
import com.bapegg.routinlog.ui.theme.Ink
import com.bapegg.routinlog.ui.theme.Muted
import com.bapegg.routinlog.ui.theme.Silver
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun RoutineLogApp(model: RoutineLogViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    BackHandler(enabled = state.screen != AppScreen.WELCOME, onBack = model::back)
    Scaffold(containerColor = Silver) { insets ->
        Box(Modifier.fillMaxSize().padding(insets).imePadding(), contentAlignment = Alignment.TopCenter) {
            key(state.screen) {
                Column(
                    Modifier.widthIn(max = 560.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    when (state.screen) {
                        AppScreen.WELCOME -> WelcomeScreen(model)
                        AppScreen.SAMPLE_HOME -> SampleHome(state, model)
                        AppScreen.SAMPLE_BODY -> SampleBody(state, model)
                        AppScreen.DEBUG_STATUS -> if (BuildConfig.DEBUG) DebugStatus(state, model)
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun WelcomeScreen(model: RoutineLogViewModel) {
    Text("루틴로그", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(24.dp))
    Text("매일의 기록이\n나의 루틴이 되도록.", style = MaterialTheme.typography.headlineLarge,
        modifier = Modifier.semantics { heading() })
    Text("한 번 설정하고, 달라진 것만.\n식사와 운동, 몸의 변화를 함께 기록해요.", color = Muted)
    Surface(shape = RoundedCornerShape(24.dp), color = Charcoal, contentColor = Color.White) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("오늘의 작은 기록", color = Celery, style = MaterialTheme.typography.labelLarge)
            Text("차곡차곡, 나를 알아가는 시간", style = MaterialTheme.typography.titleLarge)
            Text("먹은 것 · 움직인 것 · 달라진 몸", color = Color(0xFFD1D9E2))
        }
    }
    Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Text("Google 로그인 준비 중", textAlign = TextAlign.Center)
    }
    OutlinedButton(onClick = model::enterSample, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = Ink),
        border = BorderStroke(1.dp, Border)) {
        Text("샘플로 먼저 둘러보기", textAlign = TextAlign.Center)
    }
    Text("지금은 샘플 체험만 이용할 수 있어요.\n개인 기록을 보관하는 기능은 준비 중이에요.",
        style = MaterialTheme.typography.bodyMedium, color = Muted, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth())
    if (BuildConfig.DEBUG) {
        TextButton(onClick = model::openDebugStatus, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("개발 연결 확인")
        }
    }
}

@Composable
private fun SampleHome(state: RoutineLogUiState, model: RoutineLogViewModel) {
    Header("오늘의 루틴", model::back)
    SampleBanner()
    Text(state.sampleBody.date.format(DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN)),
        style = MaterialTheme.typography.titleLarge)
    Surface(shape = RoundedCornerShape(24.dp), color = Charcoal, contentColor = Color.White) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("오늘 운동 · 예시", color = Celery, style = MaterialTheme.typography.labelLarge)
            Text("하체 A", style = MaterialTheme.typography.headlineMedium)
            Text("레그프레스 · 레그컬 · 카프레이즈", color = Color(0xFFD1D9E2))
            Text("운동 기록은 준비 중이에요.", color = Color(0xFFD1D9E2), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                colors = ButtonDefaults.buttonColors(disabledContainerColor = Color(0xFF455367), disabledContentColor = Color.White)) {
                Text("운동 시작 · 준비 중")
            }
        }
    }
    InfoCard {
        Text("체중 · 허리", style = MaterialTheme.typography.titleMedium)
        Text("${state.sampleBody.weightKg?.toPlainString() ?: "—"} kg", style = MaterialTheme.typography.headlineLarge)
        Text("허리 ${state.sampleBody.waistCm?.toPlainString() ?: "—"} cm", color = Muted)
        Button(onClick = model::openBody, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text("측정값 입력해보기")
        }
    }
    state.sampleNotice?.let { notice ->
        Text(notice, style = MaterialTheme.typography.bodyMedium, color = Muted,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
    InfoCard {
        Text("평소 먹는 식사 · 예시", style = MaterialTheme.typography.titleMedium)
        Text("아침  그릭요거트 · 블루베리\n점심  현미밥 · 닭가슴살\n저녁  나의 기본 식사")
        Text("식단 편집과 주간 리포트는 준비 중이에요.", color = Muted,
            style = MaterialTheme.typography.bodyMedium)
    }
    OutlinedButton(onClick = model::endSample, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White), border = BorderStroke(1.dp, Border)) {
        Text("체험 마치기")
    }
}

@Composable
private fun SampleBody(state: RoutineLogUiState, model: RoutineLogViewModel) {
    Header("체중 · 허리 입력", model::back)
    SampleBanner()
    Text(state.sampleBody.date.format(DateTimeFormatter.ofPattern("M월 d일 · 오늘", Locale.KOREAN)), color = Muted)
    Text("오늘의 변화를 남겨요.", style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.semantics { heading() })
    Text("측정한 항목만 입력해도 괜찮아요.", color = Muted)
    MeasurementField("체중", "kg", state.weightInput, state.weightError, model::changeWeight)
    MeasurementField("허리둘레", "cm", state.waistInput, state.waistError, model::changeWaist)
    state.formError?.let { error ->
        Text(error, color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
    Button(onClick = model::applySampleInput, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Text("체험 화면에 반영", textAlign = TextAlign.Center)
    }
    Text("입력한 값은 샘플 화면에서만 사용해요.\n서버나 기기에 실제 기록으로 저장하지 않아요.",
        style = MaterialTheme.typography.bodyMedium, color = Muted, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth())
}

@Composable
private fun MeasurementField(label: String, unit: String, value: String, error: String?, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, suffix = { Text(unit) },
        modifier = Modifier.fillMaxWidth(), singleLine = true,
        textStyle = MaterialTheme.typography.headlineMedium,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        isError = error != null, supportingText = { Text(error ?: "선택 · 소수점 한 자리까지") },
        shape = RoundedCornerShape(16.dp),
    )
}

@Composable
private fun DebugStatus(state: RoutineLogUiState, model: RoutineLogViewModel) {
    Header("개발 연결 확인", model::back)
    InfoCard {
        Text("Debug 전용", fontWeight = FontWeight.Bold)
        Text("GET /api/v1/system/status", style = MaterialTheme.typography.bodyMedium)
        Text(BuildConfig.API_BASE_URL.ifBlank { "주소 미설정" }, style = MaterialTheme.typography.bodyMedium)
        Text(state.statusMessage, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        if (state.checkingStatus) CircularProgressIndicator(Modifier.size(28.dp))
        Button(onClick = model::checkStatus, enabled = !state.checkingStatus,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text("서버 응답 확인")
        }
    }
    Text("이 요청은 서버 상태만 확인합니다. 샘플 기록을 업로드하거나 Google 로그인 성공을 대신하지 않습니다.",
        style = MaterialTheme.typography.bodyMedium, color = Muted)
}

@Composable
private fun Header(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp).semantics { contentDescription = "뒤로 가기" }) {
            Text("‹", fontSize = 32.sp)
        }
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
    }
}

@Composable
private fun SampleBanner() {
    Surface(shape = RoundedCornerShape(16.dp), color = Celery.copy(alpha = .34f)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("샘플 체험", fontWeight = FontWeight.SemiBold)
            Text("가상 기록이에요. 체험을 마치면 초기화돼요.", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun InfoCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = Color.White, border = BorderStroke(1.dp, Border)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}
