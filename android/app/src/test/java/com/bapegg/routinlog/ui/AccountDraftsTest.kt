package com.bapegg.routinlog.ui

import com.bapegg.routinlog.data.ProfileDto
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** JVM-only draft/transport regressions. Fixtures are fabricated user-entered values, not health advice. */
class AccountDraftsTest {
    private fun savedProfile() = ProfileDto(
        age = 41,
        sex = "FEMALE",
        heightCm = 190.0,
        initialWeightKg = 90.125,
        initialWaistCm = 86.72,
        goal = "MAINTAIN",
        targetWeightKg = 88.5,
        activityLevel = "MODERATE",
        exerciseDays = listOf(2, 5),
        exerciseMinutes = 60,
        experience = "ADVANCED",
        units = "IMPERIAL",
        nutritionMode = "BODY_WEIGHT",
        dailyCalories = 2600,
        carbohydrateG = 318.75,
        proteinG = 146.2,
        fatG = 64.3,
        fiberG = 28.9,
        termsVersion = CONSENT_VERSION,
        privacyVersion = CONSENT_VERSION,
        healthConsentVersion = CONSENT_VERSION,
        timeZone = "Asia/Seoul",
        effectiveFrom = LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(2).toString(),
        version = 7L,
        recentExerciseDays = 2,
        recentExerciseMinutes = 60,
        recentExerciseType = "MIXED",
        recentExerciseIntensity = "MODERATE",
        scheduleFlexible = false,
        weeklyFrequency = 2,
        targetFormulaVersion = "saved-bodyweight-1",
    )

    private fun loaded(profile: ProfileDto = savedProfile()) = PreviewSession().also { AccountDrafts.begin(it, profile) }

    private fun completedNewDraft() = PreviewSession().also { ui ->
        AccountDrafts.begin(ui, null)
        listOf("terms", "privacy", "health").forEach { ui.set("onb.$it", "true") }
        ui.set("onb.age", "29")
        ui.set("onb.heightCm", "167.4")
        ui.set("onb.weightKg", "61.75")
        ui.set("onb.sex", "여성")
        ui.set("target.method", "manual")
        ui.set("target.kcal", "2175")
        ui.set("target.carbs", "270.5")
        ui.set("target.protein", "114.25")
        ui.set("target.fat", "70.25")
    }

    @Test fun freshAccountStartsBlankAndCannotReuseGuestMeasurementsOrConsent() {
        val ui = loaded()
        ui.startPreview()
        ui.set("onb.weightKg", "123.4")
        ui.set("body.2026-09-15.weight", "123.4")
        ui.set("onb.health", "true")

        AccountDrafts.begin(ui, null)

        assertTrue(ui.accountMode)
        assertFalse(ui.previewMode)
        assertEquals("A02", ui.route)
        assertNull(ui.loadedProfile)
        listOf("age", "heightCm", "heightFt", "heightIn", "weightKg", "weightLb", "waistCm").forEach {
            assertEquals("A fresh account must not inherit the UI sample: $it", "", ui.get("onb.$it", "sample-fallback"))
        }
        assertEquals("", ui.get("body.2026-09-15.weight"))
        listOf("terms", "privacy", "health").forEach { assertFalse(ui.flag("onb.$it")) }
        assertThrows(IllegalArgumentException::class.java) { AccountDrafts.profile(ui) }
    }

    @Test fun imperialProfileHydratesEveryDisplayFieldWithoutChangingCanonicalMeasurements() {
        val p = savedProfile()
        val ui = loaded(p)

        assertEquals("H01", ui.route)
        assertEquals("imperial", ui.get("onb.units"))
        assertEquals("198.7", ui.get("onb.weightLb"))
        assertEquals("6", ui.get("onb.heightFt"))
        assertEquals("2.8", ui.get("onb.heightIn"))
        assertEquals("34.1", ui.get("onb.waistIn"))
        assertEquals("195.1", ui.get("onb.targetWeightLb"))

        val draft = AccountDrafts.profile(ui)
        assertEquals(190.0, draft.heightCm, 0.0)
        assertEquals(90.125, draft.initialWeightKg, 0.0)
        assertEquals(86.72, draft.initialWaistCm!!, 0.0)
        assertEquals(88.5, draft.targetWeightKg!!, 0.0)
        assertEquals("IMPERIAL", draft.units)
        assertEquals(7L, draft.version)
        assertEquals("Asia/Seoul", draft.timeZone)
    }

    @Test fun imperialHeightNearTheNextFootNeverDisplaysTwelveInches() {
        val ui = loaded(savedProfile().copy(heightCm = 182.87))
        assertEquals("6", ui.get("onb.heightFt"))
        assertEquals("0.0", ui.get("onb.heightIn"))
        assertEquals(182.87, AccountDrafts.profile(ui).heightCm, 0.0)
    }

    @Test fun floatingPointUnitConversionsAreRoundedToTheServerStoragePrecision() {
        val ui = loaded()
        ui.set("onb.heightCm", "178.054")
        ui.set("onb.weightKg", "83.18804065998486")
        ui.set("onb.waistCm", "80.126")
        ui.set("onb.targetWeightKg", "78.7655")
        ui.set("target.protein", "123.456")

        val draft = AccountDrafts.profile(ui)
        assertEquals(178.05, draft.heightCm, 0.0)
        assertEquals(83.188, draft.initialWeightKg, 0.0)
        assertEquals(80.13, draft.initialWaistCm!!, 0.0)
        assertEquals(78.766, draft.targetWeightKg!!, 0.0)
        assertEquals(123.46, draft.proteinG!!, 0.0)
    }

    @Test fun fractionalCaloriesAreRejectedInsteadOfTruncatedButAnIntegerDecimalIsAllowed() {
        val ui = completedNewDraft()
        ui.set("target.kcal", "2400.9")
        assertThrows(IllegalArgumentException::class.java) { AccountDrafts.profile(ui) }
        assertEquals("2400.9", ui.get("target.kcal"))

        ui.set("target.kcal", "2400.00")
        assertEquals(2400, AccountDrafts.profile(ui).dailyCalories)
    }

    @Test fun noNutritionGoalSendsOnlyNullsEvenWhenOldTargetValuesRemainInTheDraft() {
        val ui = loaded()
        ui.set("target.method", "none")

        val draft = AccountDrafts.profile(ui)
        assertEquals("NONE", draft.nutritionMode)
        assertNull(draft.dailyCalories)
        assertNull(draft.carbohydrateG)
        assertNull(draft.proteinG)
        assertNull(draft.fatG)
        assertNull(draft.fiberG)
        assertNull(draft.targetFormulaVersion)
        assertEquals(90.125, draft.initialWeightKg, 0.0)
    }

    @Test fun flexibleAvailabilityDoesNotSendStaleFixedWeekdaysOrOverwriteRecentExercise() {
        val ui = loaded()
        ui.set("onb.schedule", "매주 달라요")
        ui.set("onb.availableCount", "주 3일")
        ui.set("onb.availableDays", "월|수|금|토")

        val draft = AccountDrafts.profile(ui)
        assertTrue(draft.scheduleFlexible)
        assertTrue(draft.exerciseDays.isEmpty())
        assertEquals(3, draft.weeklyFrequency)
        assertEquals(2, draft.recentExerciseDays)
        assertEquals(60, draft.recentExerciseMinutes)
    }

    @Test fun unchangedProfilePreservesExactDurationAndWireEnumsNotRepresentableByOneChip() {
        val p = savedProfile()
        val ui = loaded(p)

        val draft = AccountDrafts.profile(ui)
        assertEquals(60, draft.exerciseMinutes)
        assertEquals(60, draft.recentExerciseMinutes)
        assertEquals("MODERATE", draft.activityLevel)
        assertEquals("BODY_WEIGHT", draft.nutritionMode)
        assertEquals("saved-bodyweight-1", draft.targetFormulaVersion)
        assertEquals(listOf(2, 5), draft.exerciseDays)
        assertEquals(2, draft.weeklyFrequency)
        assertEquals(p.dailyCalories, draft.dailyCalories)
        assertEquals(p.carbohydrateG, draft.carbohydrateG)
        assertEquals(p.proteinG, draft.proteinG)
        assertEquals(p.fatG, draft.fatG)
        assertEquals(p.fiberG, draft.fiberG)
    }

    @Test fun deliberateChipChangesReplacePreservedValues() {
        val ui = loaded()
        ui.set("onb.availableDuration", "30분 미만")
        ui.set("onb.exerciseDuration", "30–60분")
        ui.set("onb.activity", "몸을 많이 쓰는 편")
        ui.set("target.method", "manual")

        val draft = AccountDrafts.profile(ui)
        assertEquals(20, draft.exerciseMinutes)
        assertEquals(45, draft.recentExerciseMinutes)
        assertEquals("HIGH", draft.activityLevel)
        assertEquals("MANUAL", draft.nutritionMode)
        assertNull(draft.targetFormulaVersion)
    }

    @Test fun untouchedRecentExerciseChoicesMatchWhatTheNewAccountFormShows() {
        val ui = completedNewDraft()
        ui.set("onb.exerciseDays", "3일")
        val active = AccountDrafts.profile(ui)
        assertEquals(3, active.recentExerciseDays)
        assertEquals(45, active.recentExerciseMinutes)
        assertEquals("MIXED", active.recentExerciseType)
        assertEquals("MODERATE", active.recentExerciseIntensity)

        ui.set("onb.exerciseDays", "0일 · 아직 안 해요")
        val inactive = AccountDrafts.profile(ui)
        assertEquals(0, inactive.recentExerciseDays)
        assertEquals(0, inactive.recentExerciseMinutes)
        assertEquals("UNKNOWN", inactive.recentExerciseType)
        assertEquals("UNKNOWN", inactive.recentExerciseIntensity)

        ui.set("onb.exerciseDays", "잘 모르겠어요")
        ui.set("onb.exerciseDuration", "잘 모르겠어요")
        assertNull(AccountDrafts.profile(ui).recentExerciseDays)
        assertNull(AccountDrafts.profile(ui).recentExerciseMinutes)
    }

    @Test fun eachRequiredConsentMustBeConfirmedBeforeProducingAnApiRequest() {
        listOf("terms", "privacy", "health").forEach { missing ->
            val ui = completedNewDraft()
            ui.set("onb.$missing", "false")
            assertThrows("Missing $missing consent must block submission", IllegalArgumentException::class.java) { AccountDrafts.profile(ui) }
        }
        val accepted = AccountDrafts.profile(completedNewDraft())
        assertEquals(CONSENT_VERSION, accepted.termsVersion)
        assertEquals(CONSENT_VERSION, accepted.privacyVersion)
        assertEquals(CONSENT_VERSION, accepted.healthConsentVersion)
    }
}
