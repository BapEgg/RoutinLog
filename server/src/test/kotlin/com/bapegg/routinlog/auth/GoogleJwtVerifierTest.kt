package com.bapegg.routinlog.auth

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import java.time.Instant
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GoogleJwtVerifierTest {
    private val key = RSAKeyGenerator(2048).generate()
    private val nonce = "a-cryptographically-random-server-nonce"
    private val client = "test-web-client.apps.googleusercontent.com"
    private val verifier = GoogleJwtVerifier(NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build(), client)

    @Test
    fun `accepts a signature and claims bound to this web client and challenge`() {
        assertEquals("google-subject", verifier.verify(token(), TokenSecrets.hash(nonce)).subject)
        assertEquals("google-subject", verifier.verify(token { issuer("accounts.google.com") }, TokenSecrets.hash(nonce)).subject)
    }

    @Test
    fun `rejects signature from an untrusted key`() {
        val other = RSAKeyGenerator(2048).generate()
        val signed = SignedJWT(JWSHeader(JWSAlgorithm.RS256), claims()).apply { sign(RSASSASigner(other)) }.serialize()
        assertFailsWith<AuthFailure> { verifier.verify(signed, TokenSecrets.hash(nonce)) }
    }

    @Test
    fun `rejects wrong audience issuer expired or missing expiration and missing subject`() {
        val invalid = listOf(
            token { audience("another-client") },
            token { claim("aud", null) },
            token { issuer("https://attacker.example") },
            token { expirationTime(Date.from(Instant.now().minusSeconds(1))) },
            token { expirationTime(null) },
            token { subject(null) },
            token { issueTime(Date.from(Instant.now().plusSeconds(120))) },
        )
        invalid.forEach { raw -> assertFailsWith<AuthFailure> { verifier.verify(raw, TokenSecrets.hash(nonce)) } }
    }

    @Test
    fun `rejects missing nonce or a token for another challenge`() {
        assertFailsWith<AuthFailure> { verifier.verify(token { claim("nonce", null) }, TokenSecrets.hash(nonce)) }
        assertFailsWith<AuthFailure> { verifier.verify(token(), TokenSecrets.hash("another-challenge")) }
    }

    @Test
    fun `rejects unsigned malformed and oversized tokens`() {
        listOf("not-a-jwt", "eyJhbGciOiJub25lIn0.e30.", "a".repeat(16385)).forEach {
            assertFailsWith<AuthFailure> { verifier.verify(it, TokenSecrets.hash(nonce)) }
        }
    }

    @Test
    fun `missing configuration returns 503 without attempting remote verification`() {
        val unconfigured = GoogleJwtVerifier(JwtDecoder { error("Must not fetch keys") }, "")
        val failure = assertFailsWith<AuthFailure> { unconfigured.verify("anything", TokenSecrets.hash(nonce)) }
        assertEquals(503, failure.status.value())
        assertEquals("AUTH_NOT_CONFIGURED", failure.code)
    }

    private fun claims(change: JWTClaimsSet.Builder.() -> Unit = {}): JWTClaimsSet = JWTClaimsSet.Builder()
        .issuer("https://accounts.google.com").audience(client).subject("google-subject")
        .issueTime(Date.from(Instant.now().minusSeconds(5))).expirationTime(Date.from(Instant.now().plusSeconds(300)))
        .claim("nonce", nonce).apply(change).build()

    private fun token(change: JWTClaimsSet.Builder.() -> Unit = {}): String =
        SignedJWT(JWSHeader(JWSAlgorithm.RS256), claims(change)).apply { sign(RSASSASigner(key)) }.serialize()
}
