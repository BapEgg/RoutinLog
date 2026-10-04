package com.bapegg.routinlog.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.bapegg.routinlog.notifications.RecordReminders
import com.bapegg.routinlog.ui.*

@Composable internal fun LiveReminders() {
    val context=LocalContext.current
    val owner=LocalAccount.current.state.userId ?: return
    val reminders=remember(context){RecordReminders(context)}
    var saved by remember(owner){mutableStateOf(reminders.settings(owner))}
    var permission by remember{mutableStateOf(reminders.enabled())}
    var message by remember{mutableStateOf<String?>(null)}
    val launcher=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){permission=reminders.enabled()}
    UiCard {
        SectionTitle("필요한 알림만 받아요")
        Choice("하루 기록 알림","음식과 운동 기록을 돌아볼 시간",saved.daily){saved=saved.copy(daily=!saved.daily)}
        if(saved.daily)Chips(listOf("18","19","20","21","22"),saved.hour.toString()){saved=saved.copy(hour=it.toInt())}
        if(saved.daily)MutedText("오후 ${saved.hour-12}시 이후 · 프로필의 시간대 기준")
        Choice("월요일 주간 리포트","오전 9시 이후 지난주 기록 확인",saved.weekly){saved=saved.copy(weekly=!saved.weekly)}
        UiButton("알림 설정 저장",{runCatching { reminders.save(owner,saved) }.onSuccess { message="알림 설정을 저장했어요." }.onFailure { message="설정을 저장하지 못했어요. 다시 시도해주세요." }})
        message?.let { MutedText(it) }
        MutedText("휴대폰의 절전 상태에 따라 알림 시간이 늦어질 수 있어요. 알림에는 체중·식사량 등 개인 수치를 표시하지 않아요.")
    }
    UiCard {
        SectionTitle("휴대폰 알림 권한")
        MutedText(if(permission)"앱 알림이 허용되어 있어요."else"휴대폰 알림 권한을 허용해야 받을 수 있어요.")
        UiButton("권한 확인",{permission=reminders.enabled();if(!permission&&Build.VERSION.SDK_INT>=33)launcher.launch(Manifest.permission.POST_NOTIFICATIONS)},false)
        UiButton("휴대폰 알림 설정 열기",{context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName))},false)
    }
}
