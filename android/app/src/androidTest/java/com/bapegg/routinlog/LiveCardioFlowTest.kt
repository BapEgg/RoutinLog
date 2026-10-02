package com.bapegg.routinlog

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bapegg.routinlog.data.*
import com.bapegg.routinlog.ui.*
import com.bapegg.routinlog.ui.screens.LiveCardioScreen
import com.bapegg.routinlog.ui.theme.RoutineLogTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate

class LiveCardioFlowTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val source=Fake()
    private lateinit var model:CardioViewModel
    private fun start() {
        lateinit var ui:PreviewSession
        compose.runOnUiThread {
            model=ViewModelProvider(compose.activity,viewModelFactory { initializer { CardioViewModel(source) } })[CardioViewModel::class.java]
            ui=ViewModelProvider(compose.activity)[PreviewSession::class.java]
            ui.accountMode=true;ui.set("cardio.date",LocalDate.now().toString());model.bind("test",java.time.ZoneId.systemDefault().id)
        }
        compose.setContent { RoutineLogTheme { Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                LiveCardioScreen(ui,model)
            }
        } } }
        compose.waitUntil(5000){model.state.value.loaded}
    }
    private fun click(text:String)=compose.onNodeWithText(text).performScrollTo().performClick()
    private fun enter(label:String,value:String)=compose.onNodeWithText(label).performScrollTo().performTextReplacement(value)
    @Test fun saveDeviceRecordEditToTimeOnlyAndDeleteWithConfirmation() {
        start();click("유산소 추가");enter("운동 이름","실내 자전거");enter("운동 시간 · 분","30")
        click("기기 값도 기록");enter("기기 이름","실내 자전거 화면");enter("표시된 칼로리 · kcal","215")
        click("총 칼로리");click("속도·경사·체감 강도 추가");enter("거리 · km · 선택","8.5")
        click("유산소 기록 저장")
        compose.runOnIdle {
            assertEquals("TOTAL",source.saved.values.single().values.energyKind)
            assertEquals("215",source.saved.values.single().values.deviceKcal?.toPlainString())
        }
        compose.onNodeWithText("기기 표시 · 총").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("이날의 유산소").performScrollTo();compose.waitForIdle()
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap->File(compose.activity.cacheDir,"qa-cardio-record.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) } }
        click("수정");enter("운동 시간 · 분","35");click("시간만 기록");click("유산소 기록 저장")
        compose.runOnIdle { assertEquals(35,source.saved.values.single().values.minutes);assertNull(source.saved.values.single().values.deviceKcal) }
        click("삭제");compose.onNodeWithText("이 기록을 삭제할까요?").assertExists();compose.onNodeWithText("취소").performClick()
        compose.runOnIdle { assertEquals(1,source.saved.size) }
        click("삭제");compose.onAllNodesWithText("삭제").onLast().performClick()
        compose.runOnIdle { assertTrue(source.saved.isEmpty()) }
    }
    @Test fun failedSaveRetainsInputAndRetryCreatesOneRecord() {
        start();click("유산소 추가");enter("운동 이름","퇴근 후 걷기");enter("운동 시간 · 분","20")
        source.fail=true;click("유산소 기록 저장")
        compose.onNodeWithText("연결 실패 · 다시 시도해주세요.").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals("20",model.state.value.draft?.minutes);assertTrue(source.saved.isEmpty()) }
        source.fail=false;click("유산소 기록 저장")
        compose.runOnIdle { assertEquals(1,source.saved.size);assertNull(source.saved.values.single().values.deviceKcal) }
    }
    private class Fake:CardioDataSource {
        val saved=linkedMapOf<String,CardioDto>();var fail=false
        override suspend fun listCardio(owner:String,from:String,to:String)=saved.values.filter { it.date in from..to }
        override suspend fun saveCardio(owner:String,id:String,write:CardioWrite):CardioDto {
            if(fail)throw AccountException(AccountErrorKind.NETWORK,"연결 실패 · 다시 시도해주세요.")
            return CardioDto(id,write.date,write.values,(write.version ?: -1)+1).also { saved[id]=it }
        }
        override suspend fun deleteCardio(owner:String,id:String,version:Long){saved.remove(id)}
    }
}
