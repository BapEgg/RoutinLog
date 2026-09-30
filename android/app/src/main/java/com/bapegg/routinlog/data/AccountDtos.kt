package com.bapegg.routinlog.data

import androidx.annotation.Keep

data class AccountIdentity(val userId: String)

/** Wire values are the server's explicit enums; measurements use kg/cm regardless of display units. */
@Keep data class ProfileDto(
    val age: Int,
    val sex: String,
    val heightCm: Double,
    val initialWeightKg: Double,
    val initialWaistCm: Double? = null,
    val goal: String,
    val targetWeightKg: Double? = null,
    val activityLevel: String,
    val exerciseDays: List<Int>,
    val exerciseMinutes: Int,
    val experience: String,
    val units: String,
    val nutritionMode: String,
    val dailyCalories: Int? = null,
    val carbohydrateG: Double? = null,
    val proteinG: Double? = null,
    val fatG: Double? = null,
    val fiberG: Double? = null,
    val termsVersion: String,
    val privacyVersion: String,
    val healthConsentVersion: String,
    val timeZone: String,
    val effectiveFrom: String,
    val version: Long? = null,
    val recentExerciseDays: Int? = null,
    val recentExerciseMinutes: Int? = null,
    val recentExerciseType: String = "UNKNOWN",
    val recentExerciseIntensity: String = "UNKNOWN",
    val scheduleFlexible: Boolean = false,
    val weeklyFrequency: Int = 0,
    val targetFormulaVersion: String? = null,
) {
    override fun toString() = "ProfileDto(redacted)"
}

@Keep data class BodyMeasurementDto(val date: String, val weightKg: Double?, val waistCm: Double?, val version: Long, val memo: String? = null) {
    override fun toString() = "BodyMeasurementDto(redacted)"
}
@Keep data class BodyMeasurementWriteDto(val weightKg: Double?, val waistCm: Double?, val version: Long? = null, val memo: String? = null) {
    override fun toString() = "BodyMeasurementWriteDto(redacted)"
}
@Keep internal data class BodyListDto(val items: List<BodyMeasurementDto> = emptyList())
@Keep internal data class GoogleChallengeDto(val challengeId: String?, val nonce: String?)
@Keep internal data class GoogleLoginDto(val idToken: String, val challengeId: String) {
    override fun toString() = "GoogleLoginDto(redacted)"
}
@Keep internal data class RefreshRequestDto(val refreshToken: String) {
    override fun toString() = "RefreshRequestDto(redacted)"
}
@Keep internal data class LogoutRequestDto(val refreshToken: String?) {
    override fun toString() = "LogoutRequestDto(redacted)"
}
@Keep internal data class AuthTokensDto(val accessToken: String?, val refreshToken: String?, val expiresIn: Long?, val userId: String?) {
    override fun toString() = "AuthTokensDto(redacted)"
}

enum class AccountErrorKind { CONFIGURATION, CANCELLED, CREDENTIAL, NETWORK, EXPIRED, VALIDATION, CONFLICT, NOT_FOUND, SERVER, STORAGE }

/** Never retain a raw response body, token, backend message, or exception cause for UI/logging. */
class AccountException(
    val kind: AccountErrorKind,
    val userMessage: String,
    val code: String? = null,
    val httpStatus: Int? = null,
) : Exception(userMessage)
