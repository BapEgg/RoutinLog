package com.bapegg.routinlog

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.bapegg.routinlog.ui.PreviewSession
import com.bapegg.routinlog.ui.ScreenCatalog
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class NativePreviewTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun session()=ViewModelProvider(compose.activity)[PreviewSession::class.java]
    private fun open(id:String){compose.runOnIdle{session().previewMode=true;session().go(id)};compose.waitForIdle()}

    @Test fun everyDesignedRouteRendersAndCanReturn(){
        assertEquals(73,ScreenCatalog.size)
        for((id,screen) in ScreenCatalog){
            compose.runOnIdle{session().reset()}
            open(id)
            val title=when(id){"A01"->"매일의 기록이\n나의 루틴이 되도록.";"H01"->"나만의 루틴로그";"F01"->"오늘 식단";else->screen.title}
            compose.onAllNodesWithText(title).onFirst().assertExists()
            if(id!="A01")compose.runOnIdle{session().back();assertNotEquals(id,session().route)}
        }
    }
    @Test fun guestTabsDoNotAuthenticateOrPersistRecords(){
        compose.onNodeWithText("로그인 없이 둘러보기").performScrollTo().performClick()
        compose.runOnIdle{assertTrue(session().previewMode);assertEquals("H01",session().route)}
        compose.onNodeWithText("식단",useUnmergedTree=true).performClick()
        compose.onNodeWithText("오늘 식단").assertExists()
        compose.onNodeWithText("운동",useUnmergedTree=true).performClick()
        compose.onNodeWithText("일주일 운동").assertExists()
        compose.runOnIdle{session().set("private.preview","changed");session().reset();assertTrue(session().values.isEmpty())}
    }
    @Test fun amountSheetChangesQuantityWithoutSavingMeal(){
        open("F04")
        compose.onNodeWithText("200 g").performClick()
        compose.runOnIdle{assertEquals("200",session().get("food.amount"));assertFalse(session().flag("food.done.점심"))}
        compose.onNodeWithText("400 kcal").assertExists()
        compose.onNodeWithText("정보 없음").assertExists()
    }
    @Test fun requiredConsentGatesOnboarding(){
        open("A02")
        compose.onNodeWithText("동의하고 계속").assertIsNotEnabled()
        compose.onNodeWithText("서비스 이용약관 동의 (필수)").performClick()
        compose.onNodeWithText("개인정보 수집·이용 동의 (필수)").performClick()
        compose.onNodeWithText("동의하고 계속").assertIsEnabled().performClick()
        compose.onNodeWithText("어떤 방향으로 기록할까요?").assertExists()
    }
    @Test fun workoutCompletionStartsRestWithoutCompletingOtherSets(){
        open("W09")
        compose.onAllNodes(hasText("세트 완료",substring=true)).onFirst().performClick()
        compose.onNodeWithText("세트 사이 휴식").assertExists()
        compose.onNodeWithText("다음 세트 시작").performClick()
        compose.onNodeWithText("세트 기록").assertExists()
    }
}
