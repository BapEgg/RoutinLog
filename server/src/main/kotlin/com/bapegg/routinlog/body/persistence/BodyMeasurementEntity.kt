package com.bapegg.routinlog.body.persistence

import com.bapegg.routinlog.account.persistence.UserAccountEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import jakarta.persistence.Version
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(
    name = "body_measurements",
    uniqueConstraints = [UniqueConstraint(name = "uq_body_measurement_user_day", columnNames = ["user_id", "measured_on"])],
)
class BodyMeasurementEntity(
    @Id
    @Column(nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    val user: UserAccountEntity,

    @Column(name = "measured_on", nullable = false)
    var measuredOn: LocalDate,

    @Column(name = "weight_kg", precision = 7, scale = 3)
    var weightKg: BigDecimal? = null,

    @Column(name = "waist_cm", precision = 6, scale = 2)
    var waistCm: BigDecimal? = null,

    @Column(length = 1000)
    var memo: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = createdAt,

    @Version
    @Column(nullable = false)
    var version: Long = 0,
)
