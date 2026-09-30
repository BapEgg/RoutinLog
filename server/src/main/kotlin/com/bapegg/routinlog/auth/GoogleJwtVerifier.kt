package com.bapegg.routinlog.auth

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.security.oauth2.jwt.JwtTimestampValidator
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.ResourceAccessException
import org.springframework.http.client.SimpleClientHttpRequestFactory
import java.time.Duration
import java.time.Instant

@Configuration(proxyBeanMethods = false)
class GoogleVerificationConfiguration {
    @Bean
    fun googleJwtDecoder(): JwtDecoder {
        val requests = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(5000)
            setReadTimeout(5000)
        }
        // Keys are fetched lazily from this fixed Google URL, never a token-supplied URL.
        return NimbusJwtDecoder.withJwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
            .jwsAlgorithm(SignatureAlgorithm.RS256)
            .restOperations(RestTemplate(requests))
            .build().apply { setJwtValidator(JwtTimestampValidator(Duration.ZERO)) }
    }
}

@Component
class GoogleJwtVerifier(
    private val decoder: JwtDecoder,
    @Value("\${routinlog.auth.google-web-client-id:}") private val clientId: String,
) : GoogleTokenVerifier {
    override fun requireConfigured() {
        if (clientId.isBlank()) throw AuthFailure(
            "AUTH_NOT_CONFIGURED", HttpStatus.SERVICE_UNAVAILABLE,
            "Google 로그인 설정이 준비되지 않았어요. 잠시 후 다시 시도해 주세요.",
        )
    }

    override fun verify(idToken: String, expectedNonceHash: String): VerifiedGoogleIdentity {
        requireConfigured()
        if (idToken.length !in 1..16384) throw AuthFailure()
        val token = try { decoder.decode(idToken) } catch (failure: JwtException) {
            if (generateSequence<Throwable>(failure) { it.cause }.take(12).any { it is ResourceAccessException || it is java.io.IOException }) {
                throw AuthFailure("AUTH_PROVIDER_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE, "Google 로그인을 확인할 수 없어요. 잠시 후 다시 시도해 주세요.")
            }
            throw AuthFailure()
        }
        val now = Instant.now()
        val issuer = token.getClaimAsString("iss")
        val subject = token.subject
        val nonce = token.getClaimAsString("nonce")
        if (issuer !in setOf("accounts.google.com", "https://accounts.google.com") ||
            token.audience?.contains(clientId) != true || token.expiresAt == null || !token.expiresAt!!.isAfter(now) ||
            token.issuedAt == null || token.issuedAt!!.isAfter(now.plusSeconds(30)) ||
            subject.isNullOrBlank() || subject.length > 255 ||
            nonce.isNullOrBlank() || nonce.length > 256 || !TokenSecrets.matchesHash(nonce, expectedNonceHash)
        ) throw AuthFailure()
        // No email/name/avatar is needed or stored: the verified sub is the account key.
        return VerifiedGoogleIdentity(subject)
    }
}
