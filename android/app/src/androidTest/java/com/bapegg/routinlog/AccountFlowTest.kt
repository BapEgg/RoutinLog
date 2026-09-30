package com.bapegg.routinlog

import android.app.Activity
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.bapegg.routinlog.data.*
import com.bapegg.routinlog.ui.AccountDrafts
import com.bapegg.routinlog.ui.AccountViewModel
import com.bapegg.routinlog.ui.PreviewSession
import com.bapegg.routinlog.ui.RoutineLogApp
import com.bapegg.routinlog.ui.RoutineLogViewModel
import com.bapegg.routinlog.ui.theme.RoutineLogTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.ZoneId

/** Real Compose account screens with a test-only data source; no credentials or network. */
class AccountFlowTest {
    // ui-test-manifest supplies this host, so MainActivity never creates real repositories.
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var account: AccountViewModel
    private lateinit var source: FakeAccountDataSource

    private fun session() = ViewModelProvider(compose.activity)[PreviewSession::class.java]

    private fun start(fake: FakeAccountDataSource = FakeAccountDataSource()) {
        source = fake
        lateinit var model: RoutineLogViewModel
        compose.runOnUiThread {
            val factory = viewModelFactory {
                initializer { AccountViewModel(source) }
                initializer { RoutineLogViewModel(SystemStatusRepository.create("", debug = true)) }
            }
            account = ViewModelProvider(compose.activity, factory)[AccountViewModel::class.java]
            model = ViewModelProvider(compose.activity, factory)[RoutineLogViewModel::class.java]
        }
        compose.setContent { RoutineLogTheme { RoutineLogApp(model, accountModel = account) } }
        compose.waitUntil(5_000) { account.state.value.ready && !account.state.value.busy }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals("H01", session().route)
            assertTrue(session().accountMode)
            assertFalse(session().previewMode)
        }
    }

    private fun openTodayBody() {
        compose.onNodeWithText("측정값 수정").performScrollTo().performClick()
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals("H04", session().route)
            assertEquals(source.today, session().get("body.loadedDate"))
        }
        compose.onNodeWithText("측정값 저장").assertIsEnabled()
    }

    private fun replaceMeasurements(weight: String, waist: String, memo: String) {
        // The three native text inputs are ordered weight, waist, optional memo.
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement(weight)
        compose.onAllNodes(hasSetTextAction())[1].performTextReplacement(waist)
        compose.onAllNodes(hasSetTextAction())[2].performTextReplacement(memo)
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            FileOutputStream(File(instrumentation.targetContext.cacheDir, name)).use {
                assertTrue("Screenshot could not be encoded", bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally { bitmap.recycle() }
    }

    @Test fun restoredProfileAndRecordsOpenLiveHomeWithoutSampleData() {
        start()
        compose.onNodeWithText("내 계정 · 온라인 기록").assertExists()
        compose.onNodeWithText("체중과 허리둘레").assertExists()
        compose.onNodeWithText("73.4").assertExists()
        compose.onNodeWithText("84.6").assertExists()
        compose.onNodeWithText("저장된 2일의 측정 기록").assertExists()
        compose.onNodeWithText("샘플 체험 · 실제 기록은 저장되지 않아요").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, source.restoreCalls)
            assertEquals(0, source.loginCalls)
            assertEquals(source.profile, account.state.value.profile)
            assertEquals(source.today, account.state.value.records.first().date)
            assertEquals("73.4", session().get("body.${source.today}.weight"))
            assertFalse(session().values.values.contains("83.2"))
        }
    }

    @Test fun explicitRefreshUpdatesProfileDisplayAndVersionForTheNextEdit() {
        start()
        compose.runOnIdle {
            source.profile = source.profile.copy(goal = "LOSE", units = "IMPERIAL", version = 4)
        }
        compose.onNodeWithText("서버에서 새로 불러오기").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("체중 감량").assertExists()
        compose.onNodeWithText("161.8").assertExists()
        compose.onNodeWithText("33.3").assertExists()
        compose.onNodeWithText("lb").assertExists()
        compose.onNodeWithText("in").assertExists()
        compose.runOnIdle {
            assertEquals("H01", session().route)
            assertEquals(source.profile, account.state.value.profile)
            assertEquals(source.profile, session().loadedProfile)
            assertEquals("imperial", session().get("onb.units"))
            assertEquals("체중 감량", session().get("onb.goal"))
            assertEquals("4", session().get("profile.version"))
            val nextEdit = AccountDrafts.profile(session())
            assertEquals(4L, requireNotNull(nextEdit.version))
            assertEquals("LOSE", nextEdit.goal)
            assertEquals("IMPERIAL", nextEdit.units)
            assertEquals(source.profile.initialWeightKg, nextEdit.initialWeightKg, 0.00001)
            assertEquals(source.profile.heightCm, nextEdit.heightCm, 0.00001)
        }
    }

    @Test fun failedProfileSaveDoesNotApplyDraftGoalOrUnitsToLiveRecords() {
        val fake = FakeAccountDataSource().apply { profile = profile.copy(goal = "GAIN") }
        start(fake)
        compose.onNodeWithContentDescription("설정").performClick()
        compose.onNodeWithText("프로필 · 목표 · 단위").performScrollTo().performClick()
        compose.onNodeWithText("체중 감량").performClick()
        compose.onNodeWithText("lb · ft/in").performClick()
        compose.runOnIdle { source.profileSaveFailure = networkError() }
        compose.onNodeWithText("변경 저장").performClick()
        compose.waitForIdle()
        compose.onNodeWithText(NETWORK_MESSAGE).assertExists()
        compose.runOnIdle {
            assertEquals(1, source.profileSaveCalls)
            assertEquals("S02", session().route)
            assertEquals("체중 감량", session().get("onb.goal"))
            assertEquals("imperial", session().get("onb.units"))
            assertEquals("GAIN", account.state.value.profile?.goal)
            assertEquals("METRIC", account.state.value.profile?.units)
        }
        compose.onNodeWithContentDescription("뒤로 가기").performClick()
        compose.onNodeWithContentDescription("뒤로 가기").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("근육 증가").assertExists()
        compose.onNodeWithText("체중 감량").assertDoesNotExist()
        compose.onNodeWithText("73.4").assertExists()
        compose.onNodeWithText("84.6").assertExists()
        compose.onNodeWithText("kg").assertExists()
        compose.onNodeWithText("cm").assertExists()
        openTodayBody()
        compose.onNodeWithText("체중 · kg").assertExists()
        compose.onNodeWithText("허리둘레 · cm").assertExists()
        compose.onNodeWithText("체중 · lb").assertDoesNotExist()
        compose.onNodeWithText("허리둘레 · in").assertDoesNotExist()
        compose.runOnIdle {
            // The editor draft can survive failure without becoming the saved display baseline.
            assertEquals("imperial", session().get("onb.units"))
            assertEquals("73.4", session().get("body.draftWeight"))
        }
    }

    @Test fun bodyEditorReloadsVersionAndWaitsForConfirmedSaveBeforeReturningHome() {
        start()
        capture("qa-live-home.png")
        compose.runOnIdle {
            // Another device updated the record after the home page loaded it.
            source.records[source.today] = BodyMeasurementDto(source.today, 73.2, 84.2, 5, "기상 후")
        }
        openTodayBody()
        capture("qa-live-body.png")
        compose.runOnIdle {
            assertTrue(source.listCalls.contains(source.today to source.today))
            assertEquals("73.2", session().get("body.draftWeight"))
            assertEquals("5", session().get("body.draftVersion"))
            source.saveGate = CompletableDeferred()
        }
        replaceMeasurements("72.6", "84.12", "식사 전 측정")
        compose.onNodeWithText("측정값 저장").performClick()
        compose.runOnIdle {
            val request = requireNotNull(source.lastBodyWrite)
            assertEquals(source.today, request.first)
            assertEquals(72.6, requireNotNull(request.second.weightKg), 0.00001)
            assertEquals(84.12, requireNotNull(request.second.waistCm), 0.00001)
            assertEquals(5L, requireNotNull(request.second.version))
            assertEquals("식사 전 측정", request.second.memo)
            assertTrue(account.state.value.busy)
            assertEquals("H04", session().route)
            assertEquals(73.2, requireNotNull(account.state.value.records.first().weightKg), 0.00001)
            source.saveGate!!.complete(Unit)
        }
        compose.waitUntil(5_000) { !account.state.value.busy }
        compose.waitForIdle()
        compose.onNodeWithText("72.6").assertExists()
        compose.onNodeWithText("84.1").assertExists()
        compose.runOnIdle {
            assertEquals("H01", session().route)
            val saved = account.state.value.records.first()
            assertEquals(source.records[source.today], saved)
            assertEquals(6L, saved.version)
            assertEquals("6", session().get("body.${source.today}.version"))
        }
    }

    @Test fun failedBodySaveKeepsDraftAndPreviouslySavedValuesThenAllowsRetry() {
        start()
        openTodayBody()
        replaceMeasurements("72.6", "84.1", "아직 저장하지 못한 메모")
        compose.runOnIdle { source.saveFailure = networkError() }
        compose.onNodeWithText("측정값 저장").performClick()
        compose.waitForIdle()
        compose.onNodeWithText(NETWORK_MESSAGE).assertExists()
        compose.runOnIdle {
            assertEquals("H04", session().route)
            assertEquals("72.6", session().get("body.draftWeight"))
            assertEquals("84.1", session().get("body.draftWaist"))
            assertEquals("아직 저장하지 못한 메모", session().get("body.draftMemo"))
            assertEquals("4", session().get("body.draftVersion"))
            assertEquals(73.4, requireNotNull(account.state.value.records.first().weightKg), 0.00001)
            assertEquals(4L, source.records.getValue(source.today).version)
            assertNull(account.state.value.notice)
            source.saveFailure = null
        }
        compose.onNodeWithText("측정값 저장").assertIsEnabled().performClick()
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals("H01", session().route)
            assertEquals(2, source.bodySaveCalls)
            assertEquals(5L, account.state.value.records.first().version)
            assertEquals("아직 저장하지 못한 메모", account.state.value.records.first().memo)
        }
        compose.onNodeWithText("72.6").assertExists()
    }

    @Test fun expiredSessionClearsProfileRecordsAndUnsentDrafts() {
        start()
        openTodayBody()
        replaceMeasurements("72.6", "84.1", "세션 만료 전에 입력한 메모")
        compose.runOnIdle {
            source.listFailure = AccountException(AccountErrorKind.EXPIRED, EXPIRED_MESSAGE)
            account.refresh()
        }
        compose.waitForIdle()
        compose.onNodeWithText("로그인 없이 둘러보기").assertExists()
        compose.onNodeWithText(EXPIRED_MESSAGE).assertExists()
        compose.runOnIdle {
            assertNull(source.identity.value)
            assertNull(account.state.value.userId)
            assertNull(account.state.value.profile)
            assertTrue(account.state.value.records.isEmpty())
            assertFalse(account.state.value.ready)
            assertEquals("A01", session().route)
            assertFalse(session().accountMode)
            assertFalse(session().previewMode)
            assertNull(session().loadedProfile)
            assertTrue(session().values.isEmpty())
        }
    }

    @Test fun failedLogoutPreservesLiveAccountAndDoesNotInvokeSuccessCallback() {
        start()
        var success = false
        compose.runOnIdle {
            source.logoutFailure = networkError()
            account.logout { success = true; session().reset() }
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertFalse(success)
            assertEquals(1, source.logoutCalls)
            assertAccountStillLoaded()
        }
        compose.onNodeWithText(NETWORK_MESSAGE).assertExists()
        compose.onNodeWithText("73.4").assertExists()
    }

    @Test fun failedAccountDeletionPreservesLiveAccountAndRecords() {
        start()
        var success = false
        compose.runOnIdle {
            source.deleteAccountFailure = networkError()
            account.deleteAccount(compose.activity) { success = true; session().reset() }
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertFalse(success)
            assertEquals(1, source.deleteAccountCalls)
            assertAccountStillLoaded()
            assertEquals(2, source.records.size)
        }
        compose.onNodeWithText(NETWORK_MESSAGE).assertExists()
    }

    @Test fun confirmedLogoutClearsLiveAccountAndReturnsToWelcome() {
        start()
        var success = false
        compose.runOnIdle { account.logout { success = true; session().reset() } }
        compose.waitForIdle()
        compose.onNodeWithText("로그인 없이 둘러보기").assertExists()
        compose.runOnIdle {
            assertTrue(success)
            assertNull(source.identity.value)
            assertNull(account.state.value.profile)
            assertTrue(account.state.value.records.isEmpty())
            assertEquals("A01", session().route)
            assertFalse(session().accountMode)
            assertTrue(session().values.isEmpty())
        }
    }

    private fun assertAccountStillLoaded() {
        assertEquals(TEST_USER_ID, account.state.value.userId)
        assertEquals(TEST_USER_ID, source.identity.value?.userId)
        assertEquals(source.profile, account.state.value.profile)
        assertTrue(account.state.value.ready)
        assertEquals(2, account.state.value.records.size)
        assertEquals("H01", session().route)
        assertTrue(session().accountMode)
        assertEquals("73.4", session().get("body.${source.today}.weight"))
    }

    private class FakeAccountDataSource : AccountDataSource {
        val today = LocalDate.now().toString()
        var profile = ProfileDto(
            age = 41, sex = "FEMALE", heightCm = 178.2,
            initialWeightKg = 74.2, initialWaistCm = 85.6,
            goal = "MAINTAIN", activityLevel = "MODERATE", exerciseDays = listOf(2, 5),
            exerciseMinutes = 60, experience = "INTERMEDIATE", units = "METRIC",
            nutritionMode = "MANUAL", dailyCalories = 2300,
            carbohydrateG = 275.0, proteinG = 150.0, fatG = 66.7, fiberG = 25.0,
            termsVersion = "2026-09-30", privacyVersion = "2026-09-30", healthConsentVersion = "2026-09-30",
            timeZone = ZoneId.systemDefault().id, effectiveFrom = today, version = 3,
            recentExerciseDays = 2, recentExerciseMinutes = 60,
            recentExerciseType = "MIXED", recentExerciseIntensity = "MODERATE", weeklyFrequency = 2,
        )
        private val identityState = MutableStateFlow<AccountIdentity?>(AccountIdentity(TEST_USER_ID))
        override val identity = identityState.asStateFlow()
        val records = linkedMapOf(
            today to BodyMeasurementDto(today, 73.4, 84.6, 4, "기상 후"),
            LocalDate.parse(today).minusDays(1).toString().let { it to BodyMeasurementDto(it, 73.6, 84.8, 2) },
        )
        val listCalls = mutableListOf<Pair<String?, String?>>()
        var restoreCalls = 0
        var loginCalls = 0
        var bodySaveCalls = 0
        var profileSaveCalls = 0
        var logoutCalls = 0
        var deleteAccountCalls = 0
        var lastBodyWrite: Pair<String, BodyMeasurementWriteDto>? = null
        var saveGate: CompletableDeferred<Unit>? = null
        var saveFailure: AccountException? = null
        var profileSaveFailure: AccountException? = null
        var listFailure: AccountException? = null
        var logoutFailure: AccountException? = null
        var deleteAccountFailure: AccountException? = null

        override suspend fun restoreSession(): AccountIdentity? {
            restoreCalls++
            return identity.value
        }

        override suspend fun login(activity: Activity): AccountIdentity {
            loginCalls++
            error("This test must restore a fake session, not launch Google sign-in")
        }

        override suspend fun getProfile() = profile
        override suspend fun saveProfile(profile: ProfileDto): ProfileDto {
            profileSaveCalls++
            failIfRequested(profileSaveFailure)
            return profile.copy(version = (this.profile.version ?: 0) + 1).also { this.profile = it }
        }

        override suspend fun listBody(from: String?, to: String?): List<BodyMeasurementDto> {
            listCalls += from to to
            failIfRequested(listFailure)
            return records.values.filter { (from == null || it.date >= from) && (to == null || it.date <= to) }
        }

        override suspend fun saveBody(date: String, measurement: BodyMeasurementWriteDto): BodyMeasurementDto {
            bodySaveCalls++
            lastBodyWrite = date to measurement
            saveGate?.await()
            failIfRequested(saveFailure)
            val existing = records[date]
            check(measurement.version == existing?.version) { "The editor must send the most recently loaded version" }
            return BodyMeasurementDto(date, measurement.weightKg, measurement.waistCm,
                (existing?.version ?: 0) + 1, measurement.memo).also { records[date] = it }
        }

        override suspend fun deleteBody(date: String, version: Long) = error("Not used by these tests")

        override suspend fun logout() {
            logoutCalls++
            failIfRequested(logoutFailure)
            identityState.value = null
        }

        override suspend fun deleteAccount(activity: Activity) {
            deleteAccountCalls++
            failIfRequested(deleteAccountFailure)
            records.clear()
            identityState.value = null
        }

        private fun failIfRequested(failure: AccountException?) {
            if (failure != null) {
                if (failure.kind == AccountErrorKind.EXPIRED) identityState.value = null
                throw failure
            }
        }
    }

    private companion object {
        const val TEST_USER_ID = "a27eaa24-4cb9-4a96-a818-a730323f18dd"
        const val NETWORK_MESSAGE = "연결하지 못했어요. 입력한 내용을 확인하고 다시 시도해주세요."
        const val EXPIRED_MESSAGE = "로그인이 만료되었어요. 다시 로그인해주세요."
        fun networkError() = AccountException(AccountErrorKind.NETWORK, NETWORK_MESSAGE)
    }
}
