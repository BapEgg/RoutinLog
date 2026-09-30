package com.bapegg.routinlog.profile

import java.math.BigDecimal
import java.time.LocalDate

enum class ProfileSex { MALE, FEMALE, UNSPECIFIED }
enum class ProfileGoal { MAINTAIN, GAIN, LOSE }
enum class ActivityLevel { SEDENTARY, LIGHT, MODERATE, HIGH }
enum class ExerciseExperience { BEGINNER, INTERMEDIATE, ADVANCED }
enum class DisplayUnits { METRIC, IMPERIAL }
enum class NutritionMode { AUTO, BODY_WEIGHT, MANUAL, NONE }
enum class RecentExerciseType { STRENGTH, CARDIO, MIXED, UNKNOWN }
enum class RecentExerciseIntensity { LOW, MODERATE, HIGH, UNKNOWN }

/** Canonical kg/cm values and the user's accepted draft targets; no new clinical calculation occurs here. */
data class ProfileDto(
    val age: Int,
    val sex: ProfileSex,
    val heightCm: BigDecimal,
    val initialWeightKg: BigDecimal,
    val initialWaistCm: BigDecimal? = null,
    val goal: ProfileGoal,
    val targetWeightKg: BigDecimal? = null,
    val activityLevel: ActivityLevel,
    val exerciseDays: List<Int>,
    val exerciseMinutes: Int,
    val experience: ExerciseExperience,
    val units: DisplayUnits,
    val nutritionMode: NutritionMode,
    val dailyCalories: Int? = null,
    val carbohydrateG: BigDecimal? = null,
    val proteinG: BigDecimal? = null,
    val fatG: BigDecimal? = null,
    val fiberG: BigDecimal? = null,
    val termsVersion: String,
    val privacyVersion: String,
    val healthConsentVersion: String,
    val timeZone: String,
    val effectiveFrom: LocalDate,
    val version: Long? = null,
    val recentExerciseDays: Int? = null,
    val recentExerciseMinutes: Int? = null,
    val recentExerciseType: RecentExerciseType = RecentExerciseType.UNKNOWN,
    val recentExerciseIntensity: RecentExerciseIntensity = RecentExerciseIntensity.UNKNOWN,
    val scheduleFlexible: Boolean = false,
    val weeklyFrequency: Int = 0,
    val targetFormulaVersion: String? = null,
)
