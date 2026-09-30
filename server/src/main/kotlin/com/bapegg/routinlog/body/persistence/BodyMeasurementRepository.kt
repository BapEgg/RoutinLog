package com.bapegg.routinlog.body.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate
import java.util.UUID

interface BodyMeasurementRepository : JpaRepository<BodyMeasurementEntity, UUID> {
    fun findByUserIdAndMeasuredOn(userId: UUID, date: LocalDate): BodyMeasurementEntity?
    fun findByUserIdAndMeasuredOnBetweenOrderByMeasuredOnAsc(userId: UUID, from: LocalDate, to: LocalDate): List<BodyMeasurementEntity>
}
