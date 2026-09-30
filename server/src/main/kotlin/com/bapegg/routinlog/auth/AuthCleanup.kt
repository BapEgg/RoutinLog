package com.bapegg.routinlog.auth

import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Configuration(proxyBeanMethods = false)
@EnableScheduling
class AuthCleanupConfiguration

@Component
class AuthCleanup(private val challenges: AuthChallengeRepository, private val sessions: AuthSessionRepository) {
    @Scheduled(fixedDelay = 3_600_000, initialDelay = 3_600_000)
    @Transactional
    fun deleteExpiredCredentials() {
        val now = Instant.now()
        challenges.deleteExpired(now)
        // Refresh hash history is deleted by the session FK cascade after its absolute expiration.
        sessions.deleteExpired(now)
    }
}
