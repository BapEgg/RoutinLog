package com.bapegg.routinlog

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bapegg.routinlog.data.*
import com.bapegg.routinlog.ui.*
import com.bapegg.routinlog.ui.screens.*
import com.bapegg.routinlog.ui.theme.RoutineLogTheme
import com.google.gson.JsonObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LiveFeaturesFlowTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private lateinit var model:FeatureViewModel
    private lateinit var workout:WorkoutViewModel
    private lateinit var ui:PreviewSession
    private val source=Features()
    private fun start(route:String) {
        compose.runOnUiThread {
            model=ViewModelProvider(compose.activity,viewModelFactory { initializer { FeatureViewModel(source) } })[FeatureViewModel::class.java]
            workout=ViewModelProvider(compose.activity,viewModelFactory { initializer { WorkoutViewModel(LiveWorkoutFlowTest.FakeWorkouts()) } })[WorkoutViewModel::class.java]
            ui=ViewModelProvider(compose.activity)[PreviewSession::class.java]
            ui.accountMode=true;ui.go(route);model.bind("owner");workout.bind("owner")
        }
        compose.setContent { RoutineLogTheme { CompositionLocalProvider(LocalFeatures provides model,LocalAccount provides AccountActions(AccountUiState(initializing=false,userId="owner",ready=true))) {
            Surface { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                when(ui.route) {
                    "W02","W03"->LivePrograms(ui,model,workout)
                    "W17"->LivePreparation(model)
                    "R01"->LiveAnalysis(ui,model,"2026-09-28")
                    else->Text("적용 후 운동 일정")
                }
            } }
        } } }
    }
    private fun click(text:String)=compose.onNodeWithText(text).performScrollTo().performClick()
    @Test fun programRequiresDaysAndConfirmationBeforeApply() {
        start("W02")
        compose.waitUntil(5000){model.state.value.catalog!=null&&workout.state.value.loaded}
        click("구성과 근거 보기")
        compose.onNodeWithText("이 요일로 적용").performScrollTo().assertIsNotEnabled()
        click("월요일");click("목요일");click("이 요일로 적용")
        compose.onNodeWithText("기본 요일 계획을 바꿀까요?").assertIsDisplayed()
        assertNull(source.applied)
        compose.onNodeWithText("적용").performClick()
        compose.waitUntil(5000){source.applied!=null}
        assertEquals(listOf(1,4),source.applied!!.days)
        compose.onNodeWithText("적용 후 운동 일정").assertExists()
    }
    @Test fun preparationCanBeSavedAndAnalysisStaysExplicit() {
        start("W17")
        compose.waitUntil(5000){model.state.value.preparation!=null}
        compose.onNodeWithContentDescription("동작 이름").performScrollTo().performTextInput("발목 준비")
        compose.onNodeWithContentDescription("시간 · 초 · 선택").performScrollTo().performTextInput("30")
        click("준비 동작 추가");click("이 목록 저장")
        compose.waitUntil(5000){source.prep.items.size==1}
        assertEquals(30,source.prep.items.single().seconds)
        compose.runOnIdle { ui.go("R01") }
        assertNull(source.analysis)
        click("내 기록 흐름 분석")
        compose.waitUntil(5000){source.analysis!=null}
        assertFalse(source.analysis!!.useAi)
        click("AI로 우선순위 살펴보기")
        compose.onNodeWithText("AI에 주간 요약을 보낼까요?").assertIsDisplayed()
        compose.onNodeWithText("기록 비교만 사용").performClick()
        assertFalse(source.analysis!!.useAi)
    }
    private class Features:FeatureDataSource {
        var applied:ProgramApply?=null;var prep=PreparationDto();var analysis:AnalysisWrite?=null
        override suspend fun programs(owner:String)=ProgramCatalogDto("test",listOf(TrainingProgram("test","전신 A/B","주 2회","유지·근육 증가",listOf(ProgramSession("A",emptyList()),ProgramSession("B",emptyList())),emptyList(),"루틴로그 구성","시작용 예시")),emptyList())
        override suspend fun applyProgram(owner:String,write:ProgramApply):ProgramApplied { applied=write;return ProgramApplied(write.programId,emptyList(),WorkoutPlanDto(emptyList())) }
        override suspend fun preparation(owner:String)=prep
        override suspend fun savePreparation(owner:String,write:PreparationDto):PreparationDto { prep=write.copy(version=0);return prep }
        override suspend fun analyze(owner:String,write:AnalysisWrite):WeeklyAnalysis { analysis=write;return WeeklyAnalysis(write.week,"RULES","기록 비교",emptyList(),emptyList(),null,emptyList()) }
        override suspend fun exportRecords(owner:String)=JsonObject()
        override suspend fun photo(owner:String,kind:String,id:String)=ThumbnailDto()
        override suspend fun savePhoto(owner:String,kind:String,id:String,write:ThumbnailDto)=write
    }
}
