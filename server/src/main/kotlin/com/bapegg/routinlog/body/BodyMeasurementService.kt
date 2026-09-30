package com.bapegg.routinlog.body

import com.bapegg.routinlog.account.persistence.AccountStatus
import com.bapegg.routinlog.account.persistence.UserAccountEntity
import com.bapegg.routinlog.body.persistence.BodyMeasurementEntity
import com.bapegg.routinlog.body.persistence.BodyMeasurementRepository
import com.bapegg.routinlog.profile.*
import jakarta.persistence.EntityManager
import jakarta.persistence.LockModeType
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class BodyMeasurementService(private val records: BodyMeasurementRepository, private val entities: EntityManager) {
    @Transactional(readOnly = true)
    fun list(userId: UUID, from: LocalDate, to: LocalDate): BodyMeasurementList {
        val user = account(userId)
        checkDate(user, from); checkDate(user, to)
        if (from > to || ChronoUnit.DAYS.between(from, to) >= 366) invalidRecord("조회 기간은 순서대로 최대 366일을 선택해주세요.")
        return BodyMeasurementList(records.findByUserIdAndMeasuredOnBetweenOrderByMeasuredOnAsc(userId, from, to).map { it.dto() })
    }

    @Transactional
    fun put(userId: UUID, date: LocalDate, request: PutBodyMeasurement): BodyMeasurementDto {
        val user = account(userId, lock = true)
        checkDate(user, date)
        if (request.weightKg == null && request.waistCm == null) invalidRecord("체중 또는 허리둘레를 한 가지 이상 입력해주세요.")
        requireDecimal(request.weightKg, "1000", 3, "체중", optional = true)
        requireDecimal(request.waistCm, "500", 2, "허리둘레", optional = true)
        if (request.memo != null && request.memo.length > 1000) invalidRecord("메모는 1,000자 이내로 입력해주세요.")
        if (request.memo?.contains('\u0000') == true) invalidRecord("메모 내용을 확인해주세요.")
        if (request.version != null && request.version < 0) invalidRecord("기록 버전을 확인해주세요.")
        val existing = records.findByUserIdAndMeasuredOn(userId, date)
        if (existing == null) {
            if (request.version != null) versionConflict()
            return records.saveAndFlush(BodyMeasurementEntity(user = user, measuredOn = date, weightKg = request.weightKg, waistCm = request.waistCm, memo = request.memo?.trim()?.ifEmpty { null })).dto()
        }
        if (request.version != existing.version) versionConflict()
        existing.weightKg = request.weightKg
        existing.waistCm = request.waistCm
        existing.memo = request.memo?.trim()?.ifEmpty { null }
        existing.updatedAt = Instant.now()
        records.flush()
        return existing.dto()
    }

    @Transactional
    fun delete(userId: UUID, date: LocalDate, version: Long) {
        val user = account(userId, lock = true)
        checkDate(user, date)
        if (version < 0) invalidRecord("기록 버전을 확인해주세요.")
        val record = records.findByUserIdAndMeasuredOn(userId, date)
            ?: throw RecordApiException(HttpStatus.NOT_FOUND, "MEASUREMENT_NOT_FOUND", "해당 날짜의 기록을 찾을 수 없어요.")
        if (version != record.version) versionConflict()
        records.delete(record)
        records.flush()
    }

    private fun checkDate(user: UserAccountEntity, date: LocalDate) {
        if (date < LocalDate.of(1900, 1, 1) || date > LocalDate.now(ZoneId.of(user.timeZone))) invalidRecord("기록 날짜는 1900년 이후부터 오늘까지 선택해주세요.")
    }

    private fun account(userId: UUID, lock: Boolean = false): UserAccountEntity {
        val user = if (lock) entities.find(UserAccountEntity::class.java, userId, LockModeType.PESSIMISTIC_WRITE)
            else entities.find(UserAccountEntity::class.java, userId)
        if (user == null || user.status != AccountStatus.ACTIVE) throw RecordApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "다시 로그인해주세요.")
        return user
    }

    private fun BodyMeasurementEntity.dto() = BodyMeasurementDto(measuredOn, weightKg, waistCm, version, memo)
}
