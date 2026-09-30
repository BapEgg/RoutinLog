package com.bapegg.routinlog.profile.persistence

import com.bapegg.routinlog.account.persistence.UserAccountEntity
import com.bapegg.routinlog.profile.*
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** Append-only snapshots keep the goal and nutrient policy that belonged to each effective period. */
@Entity
@Table(name = "user_profile_revisions", uniqueConstraints = [UniqueConstraint(name = "uq_profile_user_revision", columnNames = ["user_id", "revision"])])
class UserProfileRevisionEntity(
    @Id @Column(nullable = false, updatable = false) val id: UUID = UUID.randomUUID(),
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false) val user: UserAccountEntity,
    @Column(nullable = false, updatable = false) val revision: Long,
    @Column(name = "effective_from", nullable = false, updatable = false) val effectiveFrom: LocalDate,
    @Column(nullable = false) val age: Int,
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) val sex: ProfileSex,
    @Column(name = "height_cm", nullable = false, precision = 5, scale = 2) val heightCm: BigDecimal,
    @Column(name = "initial_weight_kg", nullable = false, precision = 7, scale = 3) val initialWeightKg: BigDecimal,
    @Column(name = "initial_waist_cm", precision = 6, scale = 2) val initialWaistCm: BigDecimal?,
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) val goal: ProfileGoal,
    @Column(name = "target_weight_kg", precision = 7, scale = 3) val targetWeightKg: BigDecimal?,
    @Enumerated(EnumType.STRING) @Column(name = "activity_level", nullable = false, length = 16) val activityLevel: ActivityLevel,
    @Column(name = "exercise_days", nullable = false, length = 13) val exerciseDays: String,
    @Column(name = "exercise_minutes", nullable = false) val exerciseMinutes: Int,
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) val experience: ExerciseExperience,
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) val units: DisplayUnits,
    @Enumerated(EnumType.STRING) @Column(name = "nutrition_mode", nullable = false, length = 16) val nutritionMode: NutritionMode,
    @Column(name = "daily_calories") val dailyCalories: Int?,
    @Column(name = "carbohydrate_g", precision = 7, scale = 2) val carbohydrateG: BigDecimal?,
    @Column(name = "protein_g", precision = 7, scale = 2) val proteinG: BigDecimal?,
    @Column(name = "fat_g", precision = 7, scale = 2) val fatG: BigDecimal?,
    @Column(name = "fiber_g", precision = 7, scale = 2) val fiberG: BigDecimal?,
    @Column(name = "terms_version", nullable = false, length = 32) val termsVersion: String,
    @Column(name = "privacy_version", nullable = false, length = 32) val privacyVersion: String,
    @Column(name = "health_consent_version", nullable = false, length = 32) val healthConsentVersion: String,
    @Column(name = "time_zone", nullable = false, length = 64) val timeZone: String,
    @Column(name = "accepted_at", nullable = false, updatable = false) val acceptedAt: Instant = Instant.now(),
    @Column(name = "recent_exercise_days") val recentExerciseDays: Int? = null,
    @Column(name = "recent_exercise_minutes") val recentExerciseMinutes: Int? = null,
    @Enumerated(EnumType.STRING) @Column(name = "recent_exercise_type", nullable = false, length = 16) val recentExerciseType: RecentExerciseType = RecentExerciseType.UNKNOWN,
    @Enumerated(EnumType.STRING) @Column(name = "recent_exercise_intensity", nullable = false, length = 16) val recentExerciseIntensity: RecentExerciseIntensity = RecentExerciseIntensity.UNKNOWN,
    @Column(name = "schedule_flexible", nullable = false) val scheduleFlexible: Boolean = false,
    @Column(name = "weekly_frequency", nullable = false) val weeklyFrequency: Int = 0,
    @Column(name = "target_formula_version", length = 64) val targetFormulaVersion: String? = null,
) {
    fun toDto(versionToken: Long = revision) = ProfileDto(
        age, sex, heightCm, initialWeightKg, initialWaistCm, goal, targetWeightKg, activityLevel,
        exerciseDays.split(',').filter(String::isNotBlank).map(String::toInt), exerciseMinutes, experience,
        units, nutritionMode, dailyCalories, carbohydrateG, proteinG, fatG, fiberG,
        termsVersion, privacyVersion, healthConsentVersion, timeZone, effectiveFrom, versionToken,
        recentExerciseDays, recentExerciseMinutes, recentExerciseType, recentExerciseIntensity, scheduleFlexible, weeklyFrequency, targetFormulaVersion,
    )

    companion object {
        fun from(user: UserAccountEntity, revision: Long, dto: ProfileDto) = UserProfileRevisionEntity(
            user = user, revision = revision, effectiveFrom = dto.effectiveFrom, age = dto.age, sex = dto.sex,
            heightCm = dto.heightCm, initialWeightKg = dto.initialWeightKg, initialWaistCm = dto.initialWaistCm,
            goal = dto.goal, targetWeightKg = dto.targetWeightKg, activityLevel = dto.activityLevel,
            exerciseDays = dto.exerciseDays.sorted().joinToString(","), exerciseMinutes = dto.exerciseMinutes,
            experience = dto.experience, units = dto.units, nutritionMode = dto.nutritionMode,
            dailyCalories = dto.dailyCalories, carbohydrateG = dto.carbohydrateG, proteinG = dto.proteinG,
            fatG = dto.fatG, fiberG = dto.fiberG, termsVersion = dto.termsVersion,
            privacyVersion = dto.privacyVersion, healthConsentVersion = dto.healthConsentVersion, timeZone = dto.timeZone,
            recentExerciseDays = dto.recentExerciseDays, recentExerciseMinutes = dto.recentExerciseMinutes,
            recentExerciseType = dto.recentExerciseType, recentExerciseIntensity = dto.recentExerciseIntensity,
            scheduleFlexible = dto.scheduleFlexible, weeklyFrequency = dto.weeklyFrequency, targetFormulaVersion = dto.targetFormulaVersion,
        )
    }
}
