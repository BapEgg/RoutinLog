package com.bapegg.routinlog.auth

import com.bapegg.routinlog.account.persistence.UserAccountEntity
import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "auth_challenges")
class AuthChallengeEntity(
    @Id val id: UUID = UUID.randomUUID(),
    @Column(name = "nonce_hash", nullable = false, length = 64) val nonceHash: String,
    @Column(name = "created_at", nullable = false) val createdAt: Instant,
    @Column(name = "expires_at", nullable = false) val expiresAt: Instant,
    @Column(name = "consumed_at") var consumedAt: Instant? = null,
)

@Entity
@Table(name = "auth_sessions")
class AuthSessionEntity(
    @Id val id: UUID = UUID.randomUUID(),
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false) val user: UserAccountEntity,
    @Column(name = "access_token_hash", nullable = false, unique = true, length = 64) var accessTokenHash: String,
    @Column(name = "access_expires_at", nullable = false) var accessExpiresAt: Instant,
    @Column(name = "expires_at", nullable = false) val expiresAt: Instant,
    @Column(name = "created_at", nullable = false) val createdAt: Instant,
    @Column(name = "revoked_at") var revokedAt: Instant? = null,
    @Version @Column(nullable = false) var version: Long = 0,
)

@Entity
@Table(name = "auth_refresh_tokens")
class AuthRefreshTokenEntity(
    @Id val id: UUID = UUID.randomUUID(),
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false) val session: AuthSessionEntity,
    @Column(name = "token_hash", nullable = false, unique = true, length = 64) val tokenHash: String,
    @Column(name = "created_at", nullable = false) val createdAt: Instant,
    @Column(name = "used_at") var usedAt: Instant? = null,
)

interface AuthChallengeRepository : JpaRepository<AuthChallengeEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from AuthChallengeEntity c where c.id = :id")
    fun lockById(@Param("id") id: UUID): AuthChallengeEntity?

    @Modifying
    @Query("delete from AuthChallengeEntity c where c.expiresAt < :before")
    fun deleteExpired(@Param("before") before: Instant): Int
}

interface AuthSessionRepository : JpaRepository<AuthSessionEntity, UUID> {
    fun findByAccessTokenHash(accessTokenHash: String): AuthSessionEntity?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AuthSessionEntity s where s.id = :id")
    fun lockById(@Param("id") id: UUID): AuthSessionEntity?

    @Modifying
    @Query(value = "delete from auth_sessions where expires_at < :before", nativeQuery = true)
    fun deleteExpired(@Param("before") before: Instant): Int
}

interface AuthRefreshTokenRepository : JpaRepository<AuthRefreshTokenEntity, UUID> {
    fun findByTokenHash(tokenHash: String): AuthRefreshTokenEntity?

    @Query("select r.session.id from AuthRefreshTokenEntity r where r.tokenHash = :hash")
    fun findSessionIdByHash(@Param("hash") hash: String): UUID?
}
