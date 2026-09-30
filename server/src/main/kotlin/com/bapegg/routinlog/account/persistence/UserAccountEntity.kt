package com.bapegg.routinlog.account.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "user_accounts")
class UserAccountEntity(
    @Id
    @Column(nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var status: AccountStatus = AccountStatus.ACTIVE,

    @Column(name = "time_zone", nullable = false, length = 64)
    var timeZone: String = "Asia/Seoul",

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = createdAt,

    @Version
    @Column(nullable = false)
    var version: Long = 0,
)

enum class AccountStatus { ACTIVE, DELETION_REQUESTED }
