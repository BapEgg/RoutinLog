package com.bapegg.routinlog.ui.screens

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bapegg.routinlog.ui.*
import com.bapegg.routinlog.ui.theme.*
import java.util.Locale
import kotlin.math.floor

@Composable
internal fun SettingsScreens(id: String, ui: PreviewSession) {
    when (id) {
        "S01" -> SettingsOverview(ui)
        "S02" -> ProfileSettings(ui)
        "S03" -> HealthSettings(ui)
        "S04" -> AccountSettings(ui)
        "S05" -> NotificationSettings(ui)
        "S06" -> DeleteAccount(ui)
        "E01" -> NetworkRequired(ui)
        "E02" -> UnsavedChanges(ui)
    }
}

@Composable
internal fun SettingsFooter(id: String, ui: PreviewSession) {
    when (id) {
        "S02" -> UiButton("변경 저장", {
            if (applyProfileDraft(ui)) ui.save("S01")
        })
        "S05" -> UiButton("알림 설정 저장", {
            ui.notify("체험 화면에 설정을 반영했어요. 실제 알림은 아직 발송되지 않아요."); ui.go("S01")
        })
        "S06" -> {
            Button(onClick = { ui.set("s.deleteDialog", "true") }, enabled = ui.get("s.deleteText").trim() == "삭제",
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("계정 삭제 확인") }
            UiButton("취소", { ui.set("s.deleteText", ""); ui.go("S04") }, primary = false)
        }
        "E01" -> {
            UiButton("다시 연결 확인", { ui.notify("실제 서버 연결 전이에요. 현재는 네트워크 안내 화면을 확인할 수 있어요.") })
            UiButton("오늘 기록으로", { ui.go("H01") }, primary = false)
        }
    }
}

@Composable
private fun SettingsOverview(ui: PreviewSession) {
    UiCard {
        ReportLabel("로그인 없이 둘러보는 중")
        MutedText("로그인하면 내 기록을 오래 보관할 수 있어요.")
        UiButton("로그인하고 내 기록 시작", { ui.go("A01") }, primary = false)
    }
    UiCard {
        UiRow("프로필 · 목표 · 단위", "목표 · 신체 정보 · 표시 단위", onClick = { ui.go("S02") })
        DividerLine()
        UiRow("기구 · 운동 환경", "운동 추가 시 확인", onClick = { ui.go("W07") })
        DividerLine()
        UiRow("걸음수 · 데이터 연동", "연결 전", onClick = { ui.go("S03") })
        DividerLine()
        UiRow("알림", "리포트 · 기록 알림", onClick = { ui.go("S05") })
        DividerLine()
        UiRow("데이터 · 계정", "내보내기 · 삭제", onClick = { ui.go("S04") })
    }
    UiButton("둘러보기 끝내기", { ui.reset() }, primary = false)
}

@Composable
private fun ProfileSettings(ui: PreviewSession) {
    SectionTitle("지금의 목표")
    val goal = ui.get("s.goal", ui.get("onb.goal", "근육 증가"))
    Chips(listOf("근육 유지", "근육 증가", "체중 감량"), goal, { ui.set("s.goal", it) })
    val imperial = ui.get("s.units", ui.get("onb.units", "metric")) == "imperial"
    val weight = profileWeight(ui).toDoubleOrNull() ?: 83.2
    val height = profileHeight(ui).toDoubleOrNull() ?: 178.0
    Chips(listOf("kg · cm", "lb · ft/in"), if (imperial) "lb · ft/in" else "kg · cm", {
        if (it == "lb · ft/in") {
            ui.set("s.weightLbInput", String.format(Locale.US, "%.1f", weight * 2.2046226218))
            val inches = profileRoundedInches(height)
            ui.set("s.heightFtInput", floor(inches / 12).toInt().toString())
            ui.set("s.heightInInput", String.format(Locale.US, "%.1f", inches % 12))
        }
        ui.set("s.units", if (it == "kg · cm") "metric" else "imperial")
    })
    val inches = profileRoundedInches(height)
    val feet = floor(inches / 12).toInt()
    UiCard {
        KeyValue("초기 체중", if (imperial) "${String.format(Locale.US, "%.1f", weight * 2.2046226218)} lb" else "${String.format(Locale.US, "%.1f", weight)} kg")
        KeyValue("키", if (imperial) "$feet ft ${String.format(Locale.US, "%.1f", inches - feet * 12)} in" else "${String.format(Locale.US, "%.1f", height)} cm")
        UiButton(if (ui.flag("s.editBody")) "신체 기준 접기" else "신체 기준 수정", { ui.toggle("s.editBody") }, primary = false)
        if (ui.flag("s.editBody")) {
            if (imperial) {
                Input("체중", ui.get("s.weightLbInput", String.format(Locale.US, "%.1f", weight * 2.2046226218)), {
                    ui.set("s.weightLbInput", it)
                    ui.set("s.weightKg", it.toDoubleOrNull()?.div(2.2046226218)?.toString() ?: "")
                }, suffix = "lb", numeric = true)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        Input("키 · 피트", ui.get("s.heightFtInput", feet.toString()), {
                            ui.set("s.heightFtInput", it); updateImperialHeight(ui, feet, inches - feet * 12)
                        }, suffix = "ft", numeric = true)
                    }
                    Column(Modifier.weight(1f)) {
                        Input("키 · 인치", ui.get("s.heightInInput", String.format(Locale.US, "%.1f", inches - feet * 12)), {
                            ui.set("s.heightInInput", it); updateImperialHeight(ui, feet, inches - feet * 12)
                        }, suffix = "in", numeric = true)
                    }
                }
            } else {
                Input("체중", profileWeight(ui), { ui.set("s.weightKg", it) }, suffix = "kg", numeric = true)
                Input("키", profileHeight(ui), { ui.set("s.heightCm", it) }, suffix = "cm", numeric = true)
            }
        }
    }
    UiButton("일일 영양 목표 수정", {
        if (applyProfileDraft(ui)) { ui.set("onboardingReturnRoute", "S02"); ui.go("A07") }
    }, primary = false)
    ReportDateField("변경 적용일", "s.goalApplyDate", "2026-09-28", ui)
    ReportNote { BodyText("목적·목표를 바꿔도 이전 주는 당시의 목표로 비교해요.") }
}

private fun profileWeight(ui: PreviewSession) = ui.get("s.weightKg", ui.get("onb.weightKg", "83.2"))
private fun profileHeight(ui: PreviewSession) = ui.get("s.heightCm", ui.get("onb.heightCm", "178"))
private fun profileRoundedInches(height: Double) = floor(height / 2.54 * 10 + .5) / 10

private fun applyProfileDraft(ui: PreviewSession): Boolean {
    val weight = profileWeight(ui).toDoubleOrNull()
    val height = profileHeight(ui).toDoubleOrNull()
    // Match onboarding/storage technical bounds; these are not recommended human measurements.
    if (weight == null || height == null || !weight.isFinite() || !height.isFinite() || weight <= 0 || weight > 1000 || height <= 0 || height > 300) {
        ui.notify("체중은 0보다 크고 1,000kg 이하, 키는 0보다 크고 300cm 이하로 입력해주세요.")
        return false
    }
    ui.set("onb.goal", ui.get("s.goal", ui.get("onb.goal", "근육 증가")))
    ui.set("onb.units", ui.get("s.units", ui.get("onb.units", "metric")))
    ui.set("onb.weightKg", profileWeight(ui)); ui.set("onb.heightCm", profileHeight(ui))
    ui.set("onb.weightLb", String.format(Locale.US, "%.1f", weight * 2.2046226218))
    val inches = profileRoundedInches(height)
    ui.set("onb.heightFt", floor(inches / 12).toInt().toString())
    ui.set("onb.heightIn", String.format(Locale.US, "%.1f", inches % 12))
    listOf("s.goal", "s.units", "s.weightKg", "s.heightCm", "s.weightLbInput", "s.heightFtInput", "s.heightInInput").forEach { ui.values.remove(it) }
    return true
}

private fun updateImperialHeight(ui: PreviewSession, fallbackFeet: Int, fallbackInches: Double) {
    val feet = ui.get("s.heightFtInput", fallbackFeet.toString()).toDoubleOrNull()
    val inches = ui.get("s.heightInInput", fallbackInches.toString()).toDoubleOrNull()
    ui.set("s.heightCm", if (feet != null && inches != null && feet.isFinite() && inches.isFinite() && feet >= 0 && feet % 1.0 == 0.0 && inches >= 0 && inches < 12) ((feet * 12 + inches) * 2.54).toString() else "")
}

@Composable
private fun HealthSettings(ui: PreviewSession) {
    UiCard {
        SectionTitle("건강 데이터")
        Badge("연결 전")
        KeyValue("읽을 정보", "걸음 수")
        KeyValue("출처", "휴대폰의 건강 데이터")
        KeyValue("마지막 확인", "아직 확인하지 않았어요")
        UiButton("걸음수 연결하기", { ui.notify("건강 데이터 연동은 아직 준비 중이에요. 실제 권한을 요청하거나 걸음 기록을 읽지 않아요.") }, primary = false)
    }
    ReportNote { BodyText("걸음수를 연결하지 않아도 식단과 운동은 계속 기록할 수 있어요.") }
    UiCard {
        SectionTitle("걸음수는 어떻게 가져오나요?")
        BodyText("휴대폰에 모인 걸음 기록을 가져와요. GPS를 계속 켜둘 필요는 없고, 휴대폰 상태에 따라 최신 기록이 늦게 표시될 수 있어요.")
    }
    UiButton("기록이 늦게 보이나요?", { ui.toggle("s.healthHelp") }, primary = false)
    if (ui.flag("s.healthHelp")) ReportNote {
        BodyText("휴대폰의 건강 앱에 걸음 기록이 있는지, 읽기 권한이 허용되어 있는지 확인해주세요. 앱에서 다시 확인할 때 최신 기록을 가져와요.")
    }
}

@Composable
private fun AccountSettings(ui: PreviewSession) {
    UiCard { KeyValue("계정", "로그인 전"); KeyValue("기록 보관", "로그인 후 보관 가능") }
    SectionTitle("내 기록")
    UiCard {
        ReportLabel("기록 내보내기")
        BodyText("신체·식사·운동·계획·변경 이력을 파일로 받을 수 있어요.")
        UiButton("내 기록 내보내기", { ui.notify("내보내기는 실제 기록 저장을 연결한 뒤 제공돼요. 아직 파일을 생성하지 않았어요.") }, primary = false)
    }
    SectionTitle("계정 관리")
    UiCard {
        UiRow("계정과 기록 삭제", "삭제할 항목과 주의사항 확인", onClick = { ui.go("S06") })
        DividerLine()
        UiRow("둘러보기 끝내기", "시작 화면으로 돌아가요", onClick = { ui.reset() })
    }
}

@Composable
private fun NotificationSettings(ui: PreviewSession) {
    UiCard {
        SettingSwitch("월요일 주간 리포트 알림", ui.flag("s.weeklyNotification", true), { ui.set("s.weeklyNotification", it.toString()) })
        if (ui.flag("s.weeklyNotification", true)) ReportTimeField("리포트 알림 시간", "s.reportTime", "08:00", ui)
    }
    UiCard {
        SettingSwitch("저녁 기록 알림", ui.flag("s.dailyNotification"), { ui.set("s.dailyNotification", it.toString()) })
        if (ui.flag("s.dailyNotification")) ReportTimeField("기록 알림 시간", "s.dailyTime", "21:00", ui)
    }
    ReportNote { BodyText("필요한 알림만 켜두세요. 알림을 꺼도 리포트는 앱에서 볼 수 있어요.") }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = Celery, checkedThumbColor = Charcoal))
    }
}

@Composable
private fun ReportTimeField(label: String, key: String, default: String, ui: PreviewSession) {
    val context = LocalContext.current
    val parts = ui.get(key, default).split(':')
    val hour = parts.getOrNull(0)?.toIntOrNull() ?: 8
    val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
    MutedText(label)
    UiButton(ui.get(key, default), {
        TimePickerDialog(context, { _, h, m -> ui.set(key, String.format(Locale.US, "%02d:%02d", h, m)) }, hour, minute, true).show()
    }, primary = false)
}

@Composable
private fun DeleteAccount(ui: PreviewSession) {
    ReportSymbol("Trash2", warning = true)
    ReportHeading("내 계정과 기록을\n삭제할까요?")
    UiCard {
        UiRow("신체·식사·운동 기록", icon = "Check")
        DividerLine()
        UiRow("저장 식단과 운동 계획", icon = "Check")
        DividerLine()
        UiRow("리포트와 제안 선택 이력", icon = "Check")
    }
    BodyText("삭제가 완료되면 되돌릴 수 없어요. 먼저 기록을 내보낼 수 있어요.")
    UiButton("기록 내보내기로 돌아가기", { ui.go("S04") }, primary = false)
    Input("확인하려면 ‘삭제’ 입력", ui.get("s.deleteText"), { ui.set("s.deleteText", it) })
    if (ui.flag("s.deleteDialog")) AlertDialog(
        onDismissRequest = { ui.set("s.deleteDialog", "false") },
        title = { Text("계정 삭제 확인") },
        text = { Text("현재는 로그인하지 않은 체험 화면이에요. 실제 계정이나 서버 기록을 삭제하지 않아요.") },
        confirmButton = { TextButton(onClick = { ui.set("s.deleteDialog", "false"); ui.set("s.deleteText", ""); ui.go("S04") }) { Text("확인") } },
        dismissButton = { TextButton(onClick = { ui.set("s.deleteDialog", "false") }) { Text("돌아가기") } },
    )
}

@Composable
private fun NetworkRequired(ui: PreviewSession) {
    ReportSymbol("WifiOff")
    ReportHeading("네트워크 연결을\n확인해주세요.")
    BodyText("기록을 불러오고 저장하려면 인터넷 연결이 필요해요.")
    UiCard {
        UiRow("Wi-Fi 또는 모바일 데이터 확인", "휴대폰 설정에서 연결 상태를 확인해주세요.", icon = "Wifi")
        DividerLine()
        UiRow("연결 후 다시 시도", "저장 완료 여부를 확인한 뒤 화면을 닫아주세요.", icon = "Repeat2")
    }
    ReportNote { BodyText("연결이 끊긴 동안의 입력은 저장되지 않아요. 인터넷에 연결한 뒤 다시 저장해주세요.") }
}

@Composable
private fun UnsavedChanges(ui: PreviewSession) {
    ReportSymbol("ClipboardList")
    ReportHeading("수정 중인 내용을\n남겨둘까요?")
    UiCard { ReportLabel("점심 · 구성 변경"); BodyText("닭가슴살 양 변경"); MutedText("아직 먹은 기록으로 저장하지 않았어요.") }
    UiButton("계속 수정", { ui.go("F03") })
    UiButton("화면에 남겨두고 나가기", { ui.set("foodDraftRetained", "true"); ui.notify("앱을 열어둔 동안만 수정 내용을 유지해요. 실제 식사 기록은 저장되지 않아요."); ui.go("H01") }, primary = false)
    UiButton("이번 변경 취소", { ui.set("s.discardDialog", "true") }, primary = false)
    if (ui.flag("s.discardDialog")) AlertDialog(
        onDismissRequest = { ui.set("s.discardDialog", "false") },
        title = { Text("이번 변경을 취소할까요?") },
        text = { Text("이번에 수정한 식사 구성을 되돌려요.") },
        confirmButton = { TextButton(onClick = {
            ui.set("s.discardDialog", "false"); ui.set("foodDraftRetained", "false"); ui.set("foodDiscardDraft", "true"); ui.go("F01")
        }) { Text("변경 취소") } },
        dismissButton = { TextButton(onClick = { ui.set("s.discardDialog", "false") }) { Text("계속 수정") } },
    )
}
