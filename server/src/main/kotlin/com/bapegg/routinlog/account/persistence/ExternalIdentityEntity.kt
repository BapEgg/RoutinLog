package com.bapegg.routinlog.account.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "external_identities",
    uniqueConstraints = [
        UniqueConstraint(name = "uq_external_identity_subject", columnNames = ["provider", "provider_subject"]),
        UniqueConstraint(name = "uq_external_identity_user_provider", columnNames = ["user_id", "provider"]),
    ],
)
class ExternalIdentityEntity(
    @Id
    @Column(nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    val user: UserAccountEntity,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32, updatable = false)
    val provider: IdentityProvider = IdentityProvider.GOOGLE,

    // The verified Google subject identifies an account; email is not the identity key.
    @Column(name = "provider_subject", nullable = false, length = 255, updatable = false)
    val providerSubject: String,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)

enum class IdentityProvider { GOOGLE }
