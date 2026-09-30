package com.bapegg.routinlog.profile

import com.bapegg.routinlog.account.persistence.AccountStatus
import com.bapegg.routinlog.account.persistence.UserAccountEntity
import com.bapegg.routinlog.profile.persistence.UserProfileRepository
import com.bapegg.routinlog.profile.persistence.UserProfileRevisionEntity
import jakarta.persistence.EntityManager
import jakarta.persistence.LockModeType
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

const val CURRENT_POLICY_VERSION = "2026-09-30"

@Service
class ProfileService(private val profiles: UserProfileRepository, private val entities: EntityManager) {
    @Transactional
    fun current(userId: UUID): ProfileDto {
        // Keep the effective snapshot and the write-version token from the same locked state.
        val user = activeAccount(userId, lock = true)
        val today = LocalDate.now(ZoneId.of(user.timeZone))
        val current = profiles.findFirstByUserIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDescRevisionDesc(userId, today)
            ?: throw RecordApiException(HttpStatus.NOT_FOUND, "PROFILE_NOT_FOUND", "먼저 시작 정보를 입력해주세요.")
        return current.toDto(profiles.findFirstByUserIdOrderByRevisionDesc(userId)!!.revision)
    }

    @Transactional
    fun save(userId: UUID, dto: ProfileDto): ProfileDto {
        validate(dto)
        // Serializes first-create and append races for this user; other users retain separate locks.
        val user = activeAccount(userId, lock = true)
        val latest = profiles.findFirstByUserIdOrderByRevisionDesc(userId)
        if ((latest == null && dto.version != null) || (latest != null && dto.version != latest.revision)) versionConflict()
        val next = latest?.revision?.plus(1) ?: 0L
        val revision = profiles.saveAndFlush(UserProfileRevisionEntity.from(user, next, dto))
        user.timeZone = dto.timeZone
        user.updatedAt = Instant.now()
        return revision.toDto()
    }

    private fun activeAccount(userId: UUID, lock: Boolean = false): UserAccountEntity {
        val account = if (lock) entities.find(UserAccountEntity::class.java, userId, LockModeType.PESSIMISTIC_WRITE)
            else entities.find(UserAccountEntity::class.java, userId)
        if (account == null || account.status != AccountStatus.ACTIVE) {
            throw RecordApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "다시 로그인해주세요.")
        }
        return account
    }

    private fun validate(dto: ProfileDto) {
        if (dto.age !in 19..120) invalidRecord("현재 서비스는 만 19세 이상을 대상으로 해요. 나이를 확인해주세요.")
        requireDecimal(dto.heightCm, "300", 2, "키")
        requireDecimal(dto.initialWeightKg, "1000", 3, "체중")
        requireDecimal(dto.initialWaistCm, "500", 2, "허리둘레", optional = true)
        requireDecimal(dto.targetWeightKg, "1000", 3, "목표 체중", optional = true)
        if (dto.exerciseDays.size > 7 || dto.exerciseDays.distinct().size != dto.exerciseDays.size || dto.exerciseDays.any { it !in 1..7 }) invalidRecord("운동 요일을 중복 없이 선택해주세요.")
        if (dto.exerciseMinutes !in 0..1440) invalidRecord("운동 시간을 확인해주세요.")
        if (dto.recentExerciseDays != null && dto.recentExerciseDays !in 0..7) invalidRecord("최근 운동 횟수를 확인해주세요.")
        if (dto.recentExerciseMinutes != null && dto.recentExerciseMinutes !in 0..1440) invalidRecord("최근 운동 시간을 확인해주세요.")
        if (dto.weeklyFrequency !in 0..7) invalidRecord("계획한 운동 횟수를 확인해주세요.")
        if (dto.targetFormulaVersion != null && !dto.targetFormulaVersion.matches(Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,63}"))) invalidRecord("목표 계산 버전을 확인해주세요.")
        if (dto.nutritionMode == NutritionMode.NONE) {
            if (listOf(dto.dailyCalories, dto.carbohydrateG, dto.proteinG, dto.fatG, dto.fiberG).any { it != null }) invalidRecord("목표 없이 시작할 때는 영양 목표를 비워주세요.")
        } else {
            if (dto.dailyCalories == null || dto.dailyCalories !in 1..50000) invalidRecord("하루 열량 목표를 확인해주세요.")
            listOf("탄수화물" to dto.carbohydrateG, "단백질" to dto.proteinG, "지방" to dto.fatG)
                .forEach { (label, value) -> requireDecimal(value, "10000", 2, label, zeroAllowed = true) }
            requireDecimal(dto.fiberG, "10000", 2, "식이섬유", optional = true, zeroAllowed = true)
        }
        if (dto.version != null && dto.version < 0) invalidRecord("기록 버전을 확인해주세요.")
        if (dto.timeZone.length > 64 || dto.timeZone !in ZoneId.getAvailableZoneIds()) invalidRecord("시간대를 확인해주세요.")
        val today = LocalDate.now(ZoneId.of(dto.timeZone))
        if (dto.effectiveFrom < LocalDate.of(1900, 1, 1) || dto.effectiveFrom > today) invalidRecord("적용일은 1900년 이후부터 오늘까지 선택해주세요.")
        if (listOf(dto.termsVersion, dto.privacyVersion, dto.healthConsentVersion).any { it != CURRENT_POLICY_VERSION }) {
            throw RecordApiException(HttpStatus.BAD_REQUEST, "POLICY_VERSION_REQUIRED", "현재 약관과 개인정보·건강정보 처리 내용을 확인하고 동의해주세요.")
        }
    }
}
