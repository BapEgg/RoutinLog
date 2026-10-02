package com.bapegg.routinlog

import android.app.Activity
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.bapegg.routinlog.data.*
import com.bapegg.routinlog.domain.NutritionMath
import com.bapegg.routinlog.ui.AccountDrafts
import com.bapegg.routinlog.ui.AccountViewModel
import com.bapegg.routinlog.ui.MealViewModel
import com.bapegg.routinlog.ui.RecordCalendarViewModel
import com.bapegg.routinlog.ui.ReportViewModel
import com.bapegg.routinlog.ui.WorkoutReviewViewModel
import com.bapegg.routinlog.ui.MealReviewViewModel
import com.bapegg.routinlog.ui.StepsViewModel
import com.bapegg.routinlog.steps.*
import java.time.Instant
import com.bapegg.routinlog.ui.ConditionViewModel
import com.bapegg.routinlog.ui.WorkoutViewModel
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
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId

/** Real Compose account screens with a test-only data source; no credentials or network. */
class AccountFlowTest {
    // ui-test-manifest supplies this host, so MainActivity never creates real repositories.
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var account: AccountViewModel
    private lateinit var source: FakeAccountDataSource
    private var meals: MealViewModel? = null
    private var workouts: WorkoutViewModel? = null
    private var conditions: ConditionViewModel? = null
    private var steps: StepsViewModel? = null
    private var calendar: RecordCalendarViewModel? = null
    private var reports: ReportViewModel? = null
    private var reviews: WorkoutReviewViewModel? = null
    private var mealReviews: MealReviewViewModel? = null

    private fun session() = ViewModelProvider(compose.activity)[PreviewSession::class.java]

    private fun start(fake: FakeAccountDataSource = FakeAccountDataSource(), mealFake: WrapperMealDataSource? = null, workoutFake: WrapperWorkoutDataSource? = null, conditionFake: WrapperConditionDataSource? = null, stepFake: StepFixture? = null, reportFake: ReportFixture? = null, reviewFake: ReviewFixture? = null, mealReviewFake: MealReviewFixture? = null, calendarFake: CalendarFixture? = null) {
        source = fake
        lateinit var model: RoutineLogViewModel
        compose.runOnUiThread {
            val factory = viewModelFactory {
                initializer { AccountViewModel(source) }
                initializer { RecordCalendarViewModel(requireNotNull(calendarFake)) }
                initializer { ReportViewModel(requireNotNull(reportFake)) }
                initializer { WorkoutReviewViewModel(requireNotNull(reviewFake)) }
                initializer { MealReviewViewModel(requireNotNull(mealReviewFake)) }
                initializer { RoutineLogViewModel(SystemStatusRepository.create("", debug = true)) }
                initializer { MealViewModel(requireNotNull(mealFake)) }
                initializer { WorkoutViewModel(requireNotNull(workoutFake)) }
                initializer { ConditionViewModel(requireNotNull(conditionFake)) }
                initializer { StepsViewModel(requireNotNull(stepFake),stepFake.engine { source.identity.value?.userId }) }
            }
            account = ViewModelProvider(compose.activity, factory)[AccountViewModel::class.java]
            model = ViewModelProvider(compose.activity, factory)[RoutineLogViewModel::class.java]
            calendar = if(calendarFake==null)null else ViewModelProvider(compose.activity,factory)[RecordCalendarViewModel::class.java]
            reports = if(reportFake==null)null else ViewModelProvider(compose.activity,factory)[ReportViewModel::class.java]
            reviews = if(reviewFake==null)null else ViewModelProvider(compose.activity,factory)[WorkoutReviewViewModel::class.java]
            mealReviews = if(mealReviewFake==null)null else ViewModelProvider(compose.activity,factory)[MealReviewViewModel::class.java]
            meals = if (mealFake == null) null else ViewModelProvider(compose.activity, factory)[MealViewModel::class.java]
            steps = if(stepFake==null)null else ViewModelProvider(compose.activity,factory)[StepsViewModel::class.java]
            conditions = if (conditionFake == null) null else ViewModelProvider(compose.activity, factory)[ConditionViewModel::class.java]
            workouts = if (workoutFake == null) null else ViewModelProvider(compose.activity, factory)[WorkoutViewModel::class.java]
        }
        compose.setContent { RoutineLogTheme { RoutineLogApp(model, accountModel = account, mealModel = meals, workoutModel = workouts, conditionModel = conditions, stepsModel = steps, reportModel = reports, reviewModel = reviews,mealReviewModel=mealReviews,calendarModel=calendar) } }
        compose.waitUntil(5_000) { account.state.value.ready && !account.state.value.busy }
        if(stepFake!=null)compose.waitUntil(5_000){steps?.state?.value?.let { it.loaded&&!it.busy }==true}
        if (conditionFake != null) compose.waitUntil(5_000) { conditions?.state?.value?.let { it.loaded && !it.loading } == true }
        if (mealFake != null) compose.waitUntil(5_000) { meals?.state?.value?.let { it.loaded && !it.loading } == true }
        if (workoutFake != null) compose.waitUntil(5_000) { workouts?.state?.value?.let { it.loaded && !it.loading } == true }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals("H01", session().route)
            assertTrue(session().accountMode)
            assertFalse(session().previewMode)
        }
    }

    private fun openReview(fixture:ReviewFixture) {
        start(reportFake=ReportFixture(),reviewFake=fixture)
        compose.onNodeWithText("리포트",useUnmergedTree=true).performClick()
        compose.waitUntil(5_000){reports?.state?.value?.report!=null}
        compose.onNodeWithText("다음 수행 초안 확인").performScrollTo().performClick()
        compose.onNodeWithText("운동 초안 만들기").performScrollTo().performClick()
        compose.waitUntil(5_000){reviews?.state?.value?.review!=null}
    }
    private fun openMealReview(fixture:MealReviewFixture,meals:WrapperMealDataSource?=null) {
        start(reportFake=ReportFixture(),mealFake=meals,mealReviewFake=fixture)
        compose.onNodeWithText("리포트",useUnmergedTree=true).performClick()
        compose.waitUntil(5_000){reports?.state?.value?.report!=null}
        compose.onNodeWithText("다음 식단 초안 확인").performScrollTo().performClick()
        compose.onNodeWithText("식단 초안 만들기").performScrollTo().performClick()
        compose.waitUntil(5_000){mealReviews?.state?.value?.review!=null}
    }
    @Test fun mealReviewEditsGramsConfirmsAndOpensReadOnlyFuturePlan() {
        val mealsFake=WrapperMealDataSource();val fixture=MealReviewFixture { r->mealsFake.plannedDate=r.targetDate;mealsFake.planned=listOfNotNull(r.chosen) }
        openMealReview(fixture,mealsFake)
        compose.onNodeWithText("음식량 직접 수정").performScrollTo().performClick()
        compose.onAllNodes(hasSetTextAction())[0].performScrollTo().performTextReplacement("120")
        compose.onNodeWithText("240 kcal").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("식단 수정 내용 확인").performScrollTo().performClick()
        compose.runOnIdle { assertNull(fixture.command) }
        compose.onNodeWithText("확인한 식단 적용").performScrollTo().performClick()
        compose.waitUntil(5_000){mealReviews?.state?.value?.review?.status=="APPLIED"}
        compose.onNodeWithText("식사 계획 반영 완료").performScrollTo().assertIsDisplayed()
        capture("qa-live-meal-review.png")
        compose.onNodeWithText("반영된 식사 계획 보기").performScrollTo().performClick()
        compose.waitUntil(5_000){this.meals?.state?.value?.date==fixture.saved?.targetDate&&this.meals?.state?.value?.loaded==true}
        compose.onNodeWithText("이날 정한 식사").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("이대로 먹었어요").assertDoesNotExist()
        compose.onNodeWithText("이날 식사 계획 해제").performScrollTo().performClick()
        compose.onNodeWithText("계획 해제").performClick()
        compose.waitUntil(5_000){this.meals?.state?.value?.day?.plannedMeals?.isEmpty()==true}
        compose.runOnIdle { assertTrue(this.meals!!.state.value.day!!.items.isEmpty());source.expireFromAnotherFeature() }
        compose.waitUntil(5_000){session().route=="A01"}
        compose.runOnIdle { assertNull(mealReviews!!.state.value.review) }
    }
    @Test fun heldMealReviewAppearsInDecisionHistory() {
        val fixture=MealReviewFixture();openMealReview(fixture)
        compose.onNodeWithText("식단 변경은 보류").performScrollTo().performClick()
        compose.waitUntil(5_000){mealReviews?.state?.value?.history?.isNotEmpty()==true}
        compose.onNodeWithText("식단 변경 보류").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals("HOLD",fixture.command!!.decision) }
    }
    private class MealReviewFixture(val onApplied:(MealReviewDto)->Unit={}) : MealReviewDataSource {
        var saved:MealReviewDto?=null;var command:MealReviewDecision?=null
        override suspend fun getMealReview(owner:String,week:String)=saved
        override suspend fun prepareMealReview(owner:String,week:String,write:ReviewPrepare):MealReviewDto {
            val item=LoggedMealItem("item","food","기준량 테스트 식품",basisGrams=BigDecimal("80"),nutrition=NutritionValues(kcal=BigDecimal("160")),preparation="AS_SOLD",grams=BigDecimal("80"))
            val option=MealReviewOption("KEEP","저장한 점심",listOf(item),NutritionMath.totals(NutritionMath.loggedItems(listOf(item))))
            return MealReviewDto(week,LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(7).toString(),"00000000-0000-0000-0000-000000000002","점심",NutritionValues(kcal=BigDecimal("2000")),emptyList(),true,listOf(option),"KEEP",emptyList(),emptyList(),"저장한 식단에서 다음 한 끼를 확인해요.",emptyList(),emptyList(),"meal-review-1","DRAFT",0).also { saved=it }
        }
        override suspend fun decideMealReview(owner:String,week:String,write:MealReviewDecision):MealReviewDto {
            command=write;val r=saved!!
            return r.copy(status=if(write.decision=="HOLD")"HELD" else "APPLIED",version=1,chosen=PlannedMeal(r.slotId!!,r.slotLabel!!,"저장한 점심",r.options.single().items.map { it.copy(grams=write.amounts.single().grams) }),decisionReason=write.reason)
                .also { saved=it;if(write.decision=="APPLY")onApplied(it) }
        }
        override suspend fun mealReviewHistory(owner:String)=listOfNotNull(saved)
    }

    @Test fun workoutReviewCustomEditRequiresConfirmationAndClearsOnSignOut() {
        val fixture=ReviewFixture();openReview(fixture)
        compose.onNodeWithText("목표 직접 수정").performScrollTo().performClick()
        compose.onAllNodes(hasSetTextAction())[0].performScrollTo().performTextReplacement("75")
        compose.onAllNodes(hasSetTextAction())[1].performScrollTo().performTextReplacement("9")
        compose.onAllNodes(hasSetTextAction())[4].performScrollTo().performTextReplacement("이번에는 가볍게")
        compose.onNodeWithText("수정한 목표 확인").performScrollTo().performClick()
        compose.onNodeWithText("적용 전 비교").assertExists()
        compose.runOnIdle { assertNull(fixture.command) }
        compose.onNodeWithText("확인한 목표 적용").performScrollTo().performClick()
        compose.waitUntil(5_000){reviews?.state?.value?.review?.status=="APPLIED"}
        compose.onNodeWithText("적용 완료").performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            assertEquals("CUSTOM",fixture.command!!.choice)
            assertEquals(0,BigDecimal("75").compareTo(fixture.command!!.targets.first().weightKg))
            assertEquals(9,fixture.command!!.targets.first().reps)
            assertEquals("이번에는 가볍게",fixture.command!!.reason)
        }
        capture("qa-live-workout-review.png")
        compose.runOnIdle { source.expireFromAnotherFeature() }
        compose.waitUntil(5_000){session().route=="A01"}
        compose.runOnIdle { assertNull(reviews?.state?.value?.review);assertTrue(reviews!!.state.value.history.isEmpty()) }
    }

    @Test fun workoutReviewHoldKeepsChoiceInHistoryWithoutApplying() {
        val fixture=ReviewFixture();openReview(fixture)
        compose.onNodeWithText("이번에는 보류").performScrollTo().performClick()
        compose.waitUntil(5_000){reviews?.state?.value?.history?.isNotEmpty()==true}
        compose.onNodeWithText("지난 실제 수행치").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals("HOLD",fixture.command!!.decision);assertEquals("HELD",reviews!!.state.value.history.first().status) }
    }

    private class ReviewFixture:WorkoutReviewDataSource {
        var stored:WorkoutReviewDto?=null
        var command:ReviewDecision?=null
        override suspend fun getReview(owner:String,week:String)=stored
        override suspend fun prepareReview(owner:String,week:String,write:ReviewPrepare):WorkoutReviewDto {
            val planned=(1..2).map { ReviewTarget("set-$it",BigDecimal("100"),10) }
            return WorkoutReviewDto(week,"2026-09-28","2026-09-30","하체 A",ExerciseSnapshot("exercise","스미스 머신 스쿼트","스미스 머신","대퇴사두","WEIGHT_REPS","MACHINE"),
                planned,planned.map { it.copy(weightKg=BigDecimal("80"),reps=8) },"LAST_PERFORMANCE","GAIN",
                "지난 실제 수행치를 다음 한 번의 후보로 확인해요.",listOf("계획 100 kg × 10회 → 실제 80 kg × 8회"),listOf("한 날짜의 한 종목에만 적용해요."),emptyList(),"workout-review-1","DRAFT",0).also { stored=it }
        }
        override suspend fun decideReview(owner:String,week:String,write:ReviewDecision):WorkoutReviewDto {
            command=write
            return stored!!.copy(status=if(write.decision=="HOLD")"HELD" else "APPLIED",version=1,choice=write.choice,chosen=write.targets,decisionReason=write.reason).also { stored=it }
        }
        override suspend fun reviewHistory(owner:String)=listOfNotNull(stored)
    }

    @Test fun liveWeeklyReportShowsRecordedNutritionAndNeverSampleSuggestions() {
        val fixture=ReportFixture();start(reportFake=fixture)
        compose.onNodeWithText("리포트",useUnmergedTree=true).performClick()
        compose.waitUntil(5_000){reports?.state?.value?.report!=null}
        compose.onNodeWithText("한 주의 루틴").assertExists()
        compose.onNodeWithText("4321").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("17,640").assertDoesNotExist()
        compose.onNodeWithText("다음 주에는 무엇을 바꿀까요?").assertDoesNotExist()
        capture("qa-live-weekly-report.png")
        compose.runOnIdle { session().go("R02") }
        compose.onNodeWithText("확인된 섭취량").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("4321 kcal").assertExists()
        compose.runOnIdle { session().go("R04") }
        compose.onNodeWithText("걸음 수").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("미수집").assertCountEquals(7)
        compose.runOnIdle { source.expireFromAnotherFeature() }
        compose.waitUntil(5_000){session().route=="A01"}
        compose.runOnIdle { assertNull(reports?.state?.value?.report) }
    }

    @Test fun reportFailureCanRetryWithoutShowingSampleHealthValues() {
        val fixture=ReportFixture().apply { fail=true };start(reportFake=fixture)
        compose.onNodeWithText("리포트",useUnmergedTree=true).performClick()
        compose.onNodeWithText("리포트 다시 불러오기").assertExists()
        compose.onNodeWithText("17,640").assertDoesNotExist()
        compose.runOnIdle { fixture.fail=false }
        compose.onNodeWithText("리포트 다시 불러오기").performClick()
        compose.waitUntil(5_000){reports?.state?.value?.report!=null}
        compose.onNodeWithText("이전 주").assertExists()
    }

    private class ReportFixture:ReportDataSource {
        var fail=false
        override suspend fun weeklyReport(owner:String,week:String?):WeeklyReport {
            if(fail)throw AccountException(AccountErrorKind.NETWORK,"연결을 확인해주세요.")
            val from=LocalDate.parse(week ?: "2026-09-21")
            val zero=NutrientTotal(BigDecimal.ZERO,0,0)
            val totals=NutritionTotals(zero,zero,zero,zero,zero)
            val average=ReportAverage(null,0,null,0,null)
            val recorded=totals.copy(kcal=NutrientTotal(BigDecimal("4321"),1,0))
            val meal=MealDto("meal",from.toString(),"slot","기록한 식사","EATEN",listOf(
                LoggedMealItem("item","food","테스트 음식",basisGrams=BigDecimal("100"),nutrition=NutritionValues(kcal=BigDecimal("4321")),preparation="AS_SOLD",grams=BigDecimal("100"))),version=0,totals=recorded)
            return WeeklyReport(from.toString(),from.plusDays(6).toString(),from.plusDays(6).toString(),"2026-09-28","Asia/Seoul","2026-09-30T00:00:00Z",
                listOf(ReportNutrient("kcal",BigDecimal("16800"),7,BigDecimal("4321"),1,0)),
                (0L..6L).map { MealDayDto(from.plusDays(it).toString(),if(it==0L)listOf(meal) else emptyList(),if(it==0L)recorded else totals,null) },emptyList(),emptyList(),average,average,emptyList(),emptyList())
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

    @Test fun calendarShowsActualKindsAndOpensMealsAndWorkoutAtSelectedDate() {
        val fixture=CalendarFixture()
        start(mealFake=WrapperMealDataSource(),workoutFake=WrapperWorkoutDataSource(),calendarFake=fixture)
        compose.waitUntil(5_000){calendar?.state?.value?.calendar!=null}
        compose.onNodeWithText("기록 달력").performClick()
        compose.onNodeWithContentDescription("이전 달").performScrollTo().performClick()
        compose.waitUntil(5_000){calendar?.state?.value?.calendar?.month==fixture.previous.toString()}
        val date=fixture.previous.plusDays(2).toString()
        compose.onNodeWithContentDescription("$date · 5종류 기록").performScrollTo().performClick()
        capture("qa-record-calendar.png")
        compose.onNodeWithText("4끼").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("1끼").assertExists()
        compose.onNodeWithText("이날 식단 보기").performScrollTo().performClick()
        compose.waitUntil(5_000){meals?.state?.value?.date==date&&meals?.state?.value?.loaded==true}
        compose.runOnIdle { assertEquals("F01",session().route);session().go("H01") }
        compose.waitUntil(5_000){calendar?.state?.value?.calendar!=null}
        capture("qa-record-home.png")
        compose.onNodeWithText("운동 기록 이어가기").performScrollTo().performClick()
        compose.waitUntil(5_000){workouts?.state?.value?.date==date&&workouts?.state?.value?.loaded==true}
        compose.runOnIdle { assertEquals("W01",session().route) }
    }
    @Test fun calendarChangingDateRequiresDecisionForUnsentMealAndClearsAfterLogout() {
        val fixture=CalendarFixture()
        start(mealFake=WrapperMealDataSource(),calendarFake=fixture)
        compose.waitUntil(5_000){meals?.state?.value?.loaded==true&&calendar?.state?.value?.calendar!=null}
        compose.runOnIdle {
            val slot=meals!!.state.value.plan!!.slots.first();meals!!.beginMeal(slot.id,slot.label)
            session().set("home.date",fixture.previous.plusDays(2).toString())
        }
        compose.waitUntil(5_000){calendar?.state?.value?.calendar?.month==fixture.previous.toString()}
        compose.onNodeWithText("이날 식단 보기").performScrollTo().performClick()
        compose.onNodeWithText("작성 중인 식사가 있어요").assertIsDisplayed()
        compose.runOnIdle { assertNotNull(meals!!.state.value.draft) }
        compose.onNodeWithText("작성 중 식사 이어서").performClick()
        compose.runOnIdle { assertEquals("F03",session().route);assertNotNull(meals!!.state.value.draft);source.expireFromAnotherFeature() }
        compose.waitUntil(5_000){session().route=="A01"}
        compose.runOnIdle { assertNull(calendar!!.state.value.calendar);assertNull(calendar!!.state.value.owner) }
    }
    @Test fun calendarLoadFailureShowsRetryInsteadOfAnEmptySuccessfulMonth() {
        val fixture=CalendarFixture()
        start(calendarFake=fixture)
        compose.waitUntil(5_000){calendar?.state?.value?.calendar!=null}
        compose.runOnIdle { fixture.fail=true;session().set("home.date",fixture.previous.toString()) }
        compose.waitUntil(5_000){calendar?.state?.value?.error!=null}
        compose.onNodeWithText("기록 다시 불러오기").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("아직 확인한 식사가 없어요.").assertDoesNotExist()
        compose.runOnIdle { fixture.fail=false }
        compose.onNodeWithText("기록 다시 불러오기").performClick()
        compose.waitUntil(5_000){calendar?.state?.value?.calendar!=null}
        compose.onNodeWithText("아직 확인한 식사가 없어요.").performScrollTo().assertIsDisplayed()
    }
    private class CalendarFixture: RecordCalendarDataSource {
        val today=LocalDate.now(ZoneId.of("Asia/Seoul"))
        val previous=today.minusMonths(1).withDayOfMonth(1)
        var fail=false
        override suspend fun recordCalendar(owner:String,month:String):RecordCalendar {
            if(fail)throw networkError()
            val from=LocalDate.parse(month)
            val to=minOf(java.time.YearMonth.from(from).atEndOfMonth(),today)
            return RecordCalendar(month,today.toString(),"Asia/Seoul",from.datesUntil(to.plusDays(1)).map { day ->
                if(day==previous.plusDays(2))RecordDay(day.toString(),4,1,BigDecimal("83.2"),null,"IN_PROGRESS","하체 A",2,1,3,true,true,0L)
                else RecordDay(day.toString(),0,0,null,null,null,null,0,0,0,false,false,null)
            }.toList())
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
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

    @Test fun sessionExpiredByAnotherFeatureClearsAccountWithoutRefresh() {
        start()
        compose.runOnIdle { source.expireFromAnotherFeature() }
        compose.waitForIdle()
        compose.onNodeWithText("로그인 없이 둘러보기").assertExists()
        compose.runOnIdle {
            assertNull(account.state.value.userId)
            assertTrue(account.state.value.records.isEmpty())
            assertFalse(session().accountMode)
            assertTrue(session().values.isEmpty())
        }
    }

    @Test fun realAppMealTabRegistersFoodAndClearsMealStateWhenAccountExpires() {
        val mealSource = WrapperMealDataSource()
        start(mealFake = mealSource)
        val mealModel = requireNotNull(meals)
        // Use the real RoutineLogApp tab and router, not a directly mounted food screen.
        compose.onNodeWithText("식단", useUnmergedTree = true).performClick()
        compose.onNodeWithText("기록한 섭취량").assertExists()
        capture("qa-live-food-app.png")
        compose.onNodeWithText("첫 음식 등록").performScrollTo().performClick()
        compose.onNodeWithContentDescription("음식 이름").performScrollTo().performTextReplacement("통합 흐름 테스트 식품")
        compose.onNodeWithContentDescription("기준량").performScrollTo().performTextReplacement("80")
        compose.onNodeWithContentDescription("열량").performScrollTo().performTextReplacement("160")
        compose.onNodeWithText("내 음식에 저장").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText("먹은 음식 기록").onFirst().performScrollTo().performClick()
        compose.onNodeWithText("음식 선택하기").performScrollTo().performClick()
        compose.onNodeWithText("통합 흐름 테스트 식품").assertExists()
        compose.onNodeWithText("이 음식 추가").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals("F03", session().route)
            assertTrue(session().accountMode)
            assertFalse(session().previewMode)
            assertEquals(TEST_USER_ID, mealModel.state.value.userId)
            assertEquals(1, mealModel.state.value.foods.size)
            assertNotNull(mealModel.state.value.plan)
            assertNotNull(mealModel.state.value.day)
            assertEquals(source.today, mealModel.state.value.day?.date)
            assertEquals(1, mealModel.state.value.draft?.items?.size)
            source.expireFromAnotherFeature()
        }
        compose.waitForIdle()
        compose.onNodeWithText("로그인 없이 둘러보기").assertExists()
        compose.onNodeWithText("통합 흐름 테스트 식품").assertDoesNotExist()
        compose.runOnIdle {
            assertNull(account.state.value.userId)
            assertEquals("A01", session().route)
            assertFalse(session().accountMode)
            assertTrue(session().values.isEmpty())
            val cleared = mealModel.state.value
            assertNull(cleared.userId)
            assertTrue(cleared.foods.isEmpty())
            assertTrue(cleared.templates.isEmpty())
            assertNull(cleared.plan)
            assertNull(cleared.day)
            assertNull(cleared.draft)
            assertFalse(cleared.loaded)
            assertFalse(cleared.loading)
            assertFalse(cleared.busy)
            // Expiring a session clears the client; it must not delete persisted food data.
            assertEquals(1, mealSource.foods.size)
        }
    }

    @Test fun realAppWorkoutTabRegistersExerciseAndClearsDraftWhenAccountExpires() {
        val workoutSource = WrapperWorkoutDataSource()
        start(workoutFake = workoutSource)
        val workoutModel = requireNotNull(workouts)
        compose.onNodeWithText("운동", useUnmergedTree = true).performClick()
        compose.onNodeWithText("이번 주 일정").assertExists()
        capture("qa-live-workout-app.png")
        compose.onNodeWithText("첫 운동 등록").performScrollTo().performClick()
        compose.onNodeWithContentDescription("운동 이름").performScrollTo().performTextReplacement("통합 흐름 테스트 운동")
        compose.onNodeWithText("내 운동 저장").performScrollTo().performClick()
        compose.waitForIdle()
        // Wait for the transient saved-message overlay before touching a bottom-edge button.
        val navigation = compose.runOnIdle { session() }
        compose.waitUntil(10_000) { navigation.message == null }
        compose.onNodeWithText("새 루틴 만들기").performScrollTo().performClick()
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals("W04", session().route)
            assertNotNull(workoutModel.state.value.routineDraft)
        }
        compose.onNodeWithContentDescription("루틴 이름").performTextReplacement("저장하지 않은 내 루틴")
        compose.onNodeWithText("루틴에 운동 추가").performScrollTo().performClick()
        compose.onNodeWithText("루틴에 추가").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals("W04", session().route)
            assertTrue(session().accountMode)
            assertFalse(session().previewMode)
            assertEquals(TEST_USER_ID, workoutModel.state.value.userId)
            assertEquals(1, workoutModel.state.value.exercises.size)
            assertEquals("저장하지 않은 내 루틴", workoutModel.state.value.routineDraft?.name)
            assertEquals(1, workoutModel.state.value.routineDraft?.entries?.size)
            source.expireFromAnotherFeature()
        }
        compose.waitForIdle()
        compose.onNodeWithText("로그인 없이 둘러보기").assertExists()
        compose.onNodeWithText("통합 흐름 테스트 운동").assertDoesNotExist()
        compose.runOnIdle {
            assertNull(account.state.value.userId)
            assertEquals("A01", session().route)
            assertTrue(session().values.isEmpty())
            val cleared = workoutModel.state.value
            assertNull(cleared.userId)
            assertTrue(cleared.exercises.isEmpty())
            assertTrue(cleared.routines.isEmpty())
            assertTrue(cleared.days.isEmpty())
            assertTrue(cleared.history.isEmpty())
            assertNull(cleared.plan)
            assertNull(cleared.routineDraft)
            assertNull(cleared.restDeadlineElapsedMs)
            assertFalse(cleared.loaded)
            // Only local state is cleared by expiry; persisted definitions stay intact.
            assertEquals(1, workoutSource.exercises.size)
        }
    }

    private fun openCondition() {
        val navigation = compose.runOnIdle { session() }
        compose.waitUntil(10_000) { navigation.message == null }
        compose.onNodeWithText("오늘의 컨디션").performScrollTo().performClick()
        compose.onNodeWithText("지난밤 수면").assertExists()
    }

    @Test fun conditionScreenSavesEditsAndDeletesRealMinuteValues() {
        val stored = WrapperConditionDataSource()
        start(conditionFake = stored)
        openCondition()
        compose.onNodeWithContentDescription("수면 시간").performScrollTo().performTextReplacement("6")
        compose.onNodeWithContentDescription("수면 분").performTextReplacement("35")
        compose.onNodeWithText("지난밤 수면").performScrollTo()
        capture("qa-live-condition.png")
        compose.onNodeWithText("조금").performScrollTo().performClick()
        compose.onNodeWithContentDescription("뻐근한 부위 · 선택").performScrollTo().performTextReplacement("하체")
        compose.onNodeWithContentDescription("기억하고 싶은 변화").performScrollTo().performTextReplacement("어제보다 가벼워요")
        compose.onNodeWithText("컨디션 저장").performScrollTo().performClick()
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals("H01", session().route)
            assertEquals(395, stored.records.getValue(source.today).values.sleepMinutes)
            assertEquals("하체", stored.records.getValue(source.today).values.sorenessArea)
            assertEquals("어제보다 가벼워요", stored.records.getValue(source.today).values.memo)
        }
        openCondition()
        compose.onNodeWithContentDescription("수면 분").assertTextContains("35")
        compose.onNodeWithContentDescription("수면 분").performTextReplacement("45")
        compose.onNodeWithText("컨디션 저장").performScrollTo().performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(405, stored.records.getValue(source.today).values.sleepMinutes); assertEquals(1L, stored.records.getValue(source.today).version) }
        openCondition()
        compose.onNodeWithText("이날 컨디션 삭제").performScrollTo().performClick()
        compose.onNodeWithText("삭제", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals("H01", session().route);assertTrue(stored.records.isEmpty());assertNull(conditions?.state?.value?.record) }
    }

    @Test fun failedConditionSaveKeepsInputForRetry() {
        val stored = WrapperConditionDataSource()
        start(conditionFake = stored)
        openCondition()
        compose.onNodeWithText("없음").performScrollTo().performClick()
        compose.onNodeWithContentDescription("기억하고 싶은 변화").performScrollTo().performTextReplacement("지우면 안 되는 메모")
        compose.runOnIdle { stored.failure = AccountException(AccountErrorKind.NETWORK,"연결을 확인해주세요.") }
        compose.onNodeWithText("컨디션 저장").performScrollTo().performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals("H07",session().route);assertTrue(stored.records.isEmpty());assertEquals("지우면 안 되는 메모",conditions?.state?.value?.draft?.values?.memo) }
        compose.onNodeWithContentDescription("기억하고 싶은 변화").assertTextContains("지우면 안 되는 메모")
        compose.runOnIdle { stored.failure = null }
        compose.onNodeWithText("컨디션 저장").performScrollTo().performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertNull(stored.records.getValue(source.today).values.sleepMinutes);assertEquals("NONE",stored.records.getValue(source.today).values.soreness) }
    }

    @Test fun conditionDraftIsClearedWhenAccountExpires() {
        start(conditionFake = WrapperConditionDataSource())
        openCondition()
        compose.onNodeWithContentDescription("수면 시간").performScrollTo().performTextReplacement("8")
        compose.runOnIdle { source.expireFromAnotherFeature() }
        compose.waitForIdle()
        compose.onNodeWithText("로그인 없이 둘러보기").assertExists()
        compose.runOnIdle { assertNull(conditions?.state?.value?.userId);assertTrue(requireNotNull(conditions).state.value.drafts.isEmpty());assertTrue(requireNotNull(conditions).state.value.records.isEmpty()) }
    }

    private class WrapperConditionDataSource : ConditionDataSource {
        val records = linkedMapOf<String,ConditionDto>()
        var failure: AccountException? = null
        override suspend fun listConditions(from:String,to:String)=records.values.filter { it.date in from..to }.sortedByDescending { it.date }
        override suspend fun saveCondition(date:String,write:ConditionWrite):ConditionDto {
            failure?.let { throw it }
            check(records[date]?.version == write.version)
            return ConditionDto(date,write.values,(write.version ?: -1)+1).also { records[date]=it }
        }
        override suspend fun deleteCondition(date:String,version:Long) { check(records[date]?.version == version);records.remove(date) }
    }

    @Test fun phoneStepsOptInSyncDisconnectAndAccountExpiry() {
        val fixture=StepFixture()
        start(stepFake=fixture)
        compose.onNodeWithText("걸음 기록").performScrollTo().performClick()
        compose.onNodeWithText("아직 가져온 걸음 기록이 없어요.").assertExists()
        compose.onNodeWithText("이 휴대폰 걸음 연결").performScrollTo().performClick()
        compose.onNodeWithText("허용하고 연결").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("이 휴대폰 연결됨").performScrollTo().assertExists()
        compose.onNodeWithText("3,200",useUnmergedTree=true).assertExists()
        compose.runOnIdle { assertEquals(1,fixture.starts);assertTrue(requireNotNull(steps).state.value.connected) }
        capture("qa-live-steps.png")
        compose.onNodeWithText("지금 새로고침").performScrollTo().performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(3200L,requireNotNull(steps).state.value.days.first { it.date==source.today }.steps) }
        compose.onNodeWithText("걸음 연결 해제").performScrollTo().performClick()
        compose.onNodeWithText("해제",useUnmergedTree=true).performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertFalse(requireNotNull(steps).state.value.connected);assertTrue(fixture.records.isNotEmpty());source.expireFromAnotherFeature() }
        compose.waitForIdle()
        compose.onNodeWithText("로그인 없이 둘러보기").assertExists()
        compose.runOnIdle { assertNull(steps?.state?.value?.owner);assertTrue(requireNotNull(steps).state.value.days.isEmpty()) }
    }
    @Test fun unsupportedPhoneShowsMissingInsteadOfInventedStepCounts() {
        val fixture=StepFixture().apply { supported=false }
        start(stepFake=fixture)
        compose.onNodeWithText("걸음 기록").performScrollTo().performClick()
        compose.onNodeWithText("아직 가져온 걸음 기록이 없어요.").assertExists()
        compose.onNodeWithText("이 휴대폰 걸음 연결").assertDoesNotExist()
        compose.onNodeWithText("이 기기에서는 걸음 센서를 사용할 수 없어요. 식단과 운동은 계속 기록할 수 있어요.").assertExists()
        compose.runOnIdle { assertEquals(0,fixture.starts);assertNull(fixture.binding) }
    }
    private class StepFixture:StepDataSource,PhoneSteps,StepBindingStore {
        var supported=true;var starts=0;var binding:StepBinding?=null;var active:StepConnection?=null
        val records=linkedMapOf<String,StepDay>()
        fun engine(identity:()->String?)=StepEngine(this,this,this,object:StepSchedule {override fun start(){};override fun cancel(){}},identity)
        override fun availability()=if(supported)StepAvailability.READY else StepAvailability.UNSUPPORTED
        override suspend fun start(){starts++}
        override suspend fun stop(){}
        override suspend fun read(from:Instant,through:Instant):Long=3200
        override fun read()=binding
        override fun write(binding:StepBinding?){this.binding=binding}
        override suspend fun stepConnection(owner:String)=StepConnectionState(active,Instant.now().toString())
        override suspend fun connectSteps(owner:String,id:String):StepConnectionState {
            active=StepConnection(id,LocalDate.now().minusDays(3).atStartOfDay(ZoneId.systemDefault()).toInstant().toString(),ZoneId.systemDefault().id)
            return stepConnection(owner)
        }
        override suspend fun disconnectSteps(owner:String,id:String){active=null}
        override suspend fun saveSteps(owner:String,id:String,batch:StepBatch){batch.items.forEach { records[it.date]=StepDay(it.date,it.steps,it.from,it.through,listOf(ZoneId.systemDefault().id),1) }}
        override suspend fun listSteps(owner:String,from:String,to:String)=records.values.filter { it.date in from..to }.sortedByDescending { it.date }
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

        fun expireFromAnotherFeature() { identityState.value = null }

        private fun failIfRequested(failure: AccountException?) {
            if (failure != null) {
                if (failure.kind == AccountErrorKind.EXPIRED) identityState.value = null
                throw failure
            }
        }
    }

    /** Only methods exercised by this wrapper test are implemented; no real account or HTTP. */
    private class WrapperMealDataSource : MealDataSource {
        val foods = mutableListOf<FoodDto>()
        var plannedDate:String?=null
        var planned=emptyList<PlannedMeal>()
        private val plan = MealPlanDto(listOf(
            MealSlot("00000000-0000-0000-0000-000000000001", "아침"),
            MealSlot("00000000-0000-0000-0000-000000000002", "점심"),
            MealSlot("00000000-0000-0000-0000-000000000003", "저녁"),
        ))
        override suspend fun listFoods() = foods.toList()
        override suspend fun listMealTemplates() = emptyList<MealTemplateDto>()
        override suspend fun getMealPlan() = plan
        override suspend fun getMealDay(date: String) = MealDayDto(date, emptyList(), NutritionMath.totals(emptyList()), NutritionValues(kcal = BigDecimal("2300")),if(date==plannedDate)planned else emptyList())
        override suspend fun deleteMealDayPlan(date:String,slotId:String,version:Long) { planned=planned.filterNot { it.slotId==slotId } }
        override suspend fun saveFood(id: String, food: FoodWrite): FoodDto {
            check(food.version == null && foods.none { it.id == id })
            return FoodDto(id, food.name, food.brand, food.basisGrams, food.nutrition, food.preparation, food.sourceNote, version = 1).also { foods += it }
        }
        override suspend fun deleteFood(id: String, version: Long) = error("Not exercised")
        override suspend fun saveMealTemplate(id: String, template: MealTemplateWrite): MealTemplateDto = error("Not exercised")
        override suspend fun deleteMealTemplate(id: String, version: Long) = error("Not exercised")
        override suspend fun saveMealPlan(plan: MealPlanWrite): MealPlanDto = error("Not exercised")
        override suspend fun saveMeal(id: String, meal: MealWrite): MealDto = error("Not exercised")
        override suspend fun deleteMeal(id: String, version: Long) = error("Not exercised")
    }

    private class WrapperWorkoutDataSource : WorkoutDataSource {
        val exercises = mutableListOf<ExerciseDto>()
        override suspend fun listExercises() = exercises.toList()
        override suspend fun listRoutines() = emptyList<RoutineDto>()
        override suspend fun getWorkoutPlan() = WorkoutPlanDto((1..7).map { WorkoutPlanSlot(it) })
        override suspend fun getWorkoutDays(from: String, to: String): List<WorkoutDayDto> =
            LocalDate.parse(from).datesUntil(LocalDate.parse(to).plusDays(1)).map { WorkoutDayDto(it.toString()) }.toList()
        override suspend fun saveExercise(id: String, exercise: ExerciseWrite): ExerciseDto {
            check(exercise.version == null && exercises.none { it.id == id })
            return ExerciseDto(id, exercise.name, exercise.equipment, exercise.target, exercise.recordType, exercise.loadConvention, 0).also { exercises += it }
        }
        override suspend fun getWorkoutHistory(exerciseId: String, before: String) = emptyList<WorkoutHistoryItem>()
        override suspend fun deleteExercise(id: String, version: Long): Unit = error("Not exercised")
        override suspend fun saveRoutine(id: String, routine: RoutineWrite): RoutineDto = error("Not exercised")
        override suspend fun deleteRoutine(id: String, version: Long): Unit = error("Not exercised")
        override suspend fun saveWorkoutPlan(plan: WorkoutPlanWrite): WorkoutPlanDto = error("Not exercised")
        override suspend fun saveWorkoutOverride(date: String, override: WorkoutOverrideWrite): WorkoutOverrideDto = error("Not exercised")
        override suspend fun deleteWorkoutOverride(date: String, version: Long): Unit = error("Not exercised")
        override suspend fun startWorkoutSession(id: String, start: WorkoutStartWrite): WorkoutSessionDto = error("Not exercised")
        override suspend fun saveWorkoutSession(id: String, session: WorkoutSessionWrite): WorkoutSessionDto = error("Not exercised")
        override suspend fun deleteWorkoutSession(id: String, version: Long): Unit = error("Not exercised")
    }

    private companion object {
        const val TEST_USER_ID = "a27eaa24-4cb9-4a96-a818-a730323f18dd"
        const val NETWORK_MESSAGE = "연결하지 못했어요. 입력한 내용을 확인하고 다시 시도해주세요."
        const val EXPIRED_MESSAGE = "로그인이 만료되었어요. 다시 로그인해주세요."
        fun networkError() = AccountException(AccountErrorKind.NETWORK, NETWORK_MESSAGE)
    }
}
