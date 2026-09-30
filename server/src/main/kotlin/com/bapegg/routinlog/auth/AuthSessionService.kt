package com.bapegg.routinlog.auth

import com.bapegg.routinlog.account.persistence.*
import com.bapegg.routinlog.security.AuthenticatedUser
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Service
class AuthSessionService(
    private val challenges: AuthChallengeRepository,
    private val sessions: AuthSessionRepository,
    private val refreshTokens: AuthRefreshTokenRepository,
    private val accounts: UserAccountRepository,
    private val identities: ExternalIdentityRepository,
) {
    @Transactional
    fun createChallenge(): ChallengeResponse {
        val now = Instant.now()
        val nonce = TokenSecrets.random()
        val challenge = challenges.save(AuthChallengeEntity(nonceHash = TokenSecrets.hash(nonce), createdAt = now, expiresAt = now.plusSeconds(300)))
        return ChallengeResponse(challenge.id, nonce)
    }

    @Transactional(readOnly = true)
    fun nonceHash(challengeId: UUID): String {
        val challenge = challenges.findById(challengeId).orElseThrow { AuthFailure("AUTH_CHALLENGE_INVALID") }
        if (challenge.consumedAt != null || !challenge.expiresAt.isAfter(Instant.now())) throw AuthFailure("AUTH_CHALLENGE_INVALID")
        return challenge.nonceHash
    }

    @Transactional
    fun signIn(challengeId: UUID, verified: VerifiedGoogleIdentity): SessionTokens {
        val now = Instant.now()
        consumeChallenge(challengeId, now)
        val existing = identities.findByProviderAndProviderSubject(IdentityProvider.GOOGLE, verified.subject)
        val account = existing?.user ?: accounts.saveAndFlush(UserAccountEntity()).also {
            identities.saveAndFlush(ExternalIdentityEntity(user = it, providerSubject = verified.subject))
        }
        if (account.status != AccountStatus.ACTIVE) throw AuthFailure("ACCOUNT_UNAVAILABLE")
        val access = TokenSecrets.access()
        val refresh = TokenSecrets.refresh()
        val session = sessions.saveAndFlush(AuthSessionEntity(
            user = account, accessTokenHash = TokenSecrets.hash(access),
            accessExpiresAt = now.plusSeconds(3600), expiresAt = now.plus(Duration.ofDays(30)), createdAt = now,
        ))
        refreshTokens.save(AuthRefreshTokenEntity(session = session, tokenHash = TokenSecrets.hash(refresh), createdAt = now))
        return SessionTokens(access, refresh, 3600, account.id)
    }

    @Transactional(readOnly = true)
    fun authenticate(accessToken: String): AuthenticatedUser? {
        if (!TokenSecrets.isAccess(accessToken)) return null
        val session = sessions.findByAccessTokenHash(TokenSecrets.hash(accessToken)) ?: return null
        val now = Instant.now()
        if (!usable(session, now) || !session.accessExpiresAt.isAfter(now)) return null
        return AuthenticatedUser(session.user.id, session.id)
    }

    @Transactional
    fun refresh(refreshToken: String): SessionTokens? {
        if (!TokenSecrets.isRefresh(refreshToken)) return null
        val tokenHash = TokenSecrets.hash(refreshToken)
        val sessionId = refreshTokens.findSessionIdByHash(tokenHash) ?: return null
        val session = sessions.lockById(sessionId) ?: return null
        val now = Instant.now()
        if (!usable(session, now)) return null
        // First entity read occurs after acquiring the lock; no stale first-level-cache copy.
        val spent = refreshTokens.findByTokenHash(tokenHash) ?: return null
        return rotateLocked(session, spent, now)
    }

    private fun rotateLocked(session: AuthSessionEntity, token: AuthRefreshTokenEntity, now: Instant): SessionTokens? {
        if (token.usedAt != null) {
            session.revokedAt = now
            // Returning instead of throwing ensures replay revocation commits before a 401 response.
            return null
        }
        token.usedAt = now
        val access = TokenSecrets.access()
        val refresh = TokenSecrets.refresh()
        session.accessTokenHash = TokenSecrets.hash(access)
        session.accessExpiresAt = minOf(now.plusSeconds(3600), session.expiresAt)
        refreshTokens.save(AuthRefreshTokenEntity(session = session, tokenHash = TokenSecrets.hash(refresh), createdAt = now))
        return SessionTokens(access, refresh, Duration.between(now, session.accessExpiresAt).seconds, session.user.id)
    }

    @Transactional
    fun logout(user: AuthenticatedUser, refreshToken: String?) {
        val sessionId = user.sessionId ?: throw AuthFailure()
        val session = sessions.lockById(sessionId) ?: throw AuthFailure()
        if (session.user.id != user.userId) throw AuthFailure()
        if (refreshToken != null) {
            if (!TokenSecrets.isRefresh(refreshToken)) throw AuthFailure()
            val refresh = refreshTokens.findByTokenHash(TokenSecrets.hash(refreshToken)) ?: throw AuthFailure()
            if (refresh.session.id != session.id) throw AuthFailure()
        }
        session.revokedAt = Instant.now()
    }

    @Transactional
    fun deleteAccount(user: AuthenticatedUser, challengeId: UUID, verified: VerifiedGoogleIdentity) {
        val now = Instant.now()
        consumeChallenge(challengeId, now)
        val session = user.sessionId?.let(sessions::lockById) ?: throw AuthFailure()
        if (session.user.id != user.userId || !usable(session, now) || !session.accessExpiresAt.isAfter(now)) throw AuthFailure()
        val identity = identities.findByProviderAndProviderSubject(IdentityProvider.GOOGLE, verified.subject)
        if (identity?.user?.id != user.userId) throw AuthFailure(
            "AUTH_ACCOUNT_MISMATCH", org.springframework.http.HttpStatus.FORBIDDEN,
            "현재 로그인한 Google 계정으로 다시 확인해 주세요.",
        )
        // V1/V2/V3 foreign keys delete identities, all sessions/tokens, body records and profile revisions.
        accounts.deleteById(user.userId)
        accounts.flush()
    }

    private fun consumeChallenge(challengeId: UUID, now: Instant) {
        // Recheck and lock after remote signature verification; only one request can consume it.
        val challenge = challenges.lockById(challengeId) ?: throw AuthFailure("AUTH_CHALLENGE_INVALID")
        if (challenge.consumedAt != null || !challenge.expiresAt.isAfter(now)) throw AuthFailure("AUTH_CHALLENGE_INVALID")
        challenge.consumedAt = now
    }

    private fun usable(session: AuthSessionEntity, now: Instant): Boolean =
        session.revokedAt == null && session.expiresAt.isAfter(now) && session.user.status == AccountStatus.ACTIVE
}
