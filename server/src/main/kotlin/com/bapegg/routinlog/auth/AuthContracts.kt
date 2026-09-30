package com.bapegg.routinlog.auth

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import java.util.UUID

class GoogleLoginRequest(
    @field:NotBlank @field:Size(max = 16384) val idToken: String,
    val challengeId: UUID,
)

class RefreshRequest(@field:NotBlank @field:Size(max = 128) val refreshToken: String)
class LogoutRequest(@field:Size(max = 128) val refreshToken: String? = null)
class ChallengeResponse(val challengeId: UUID, val nonce: String)
class SessionTokens(val accessToken: String, val refreshToken: String, val expiresIn: Long, val userId: UUID)
class AuthErrorResponse(val code: String, val message: String)

class AuthFailure(
    val code: String = "AUTH_INVALID",
    val status: HttpStatus = HttpStatus.UNAUTHORIZED,
    override val message: String = "로그인 정보를 확인할 수 없어요. 다시 로그인해 주세요.",
) : RuntimeException(message)

data class VerifiedGoogleIdentity(val subject: String)

interface GoogleTokenVerifier {
    fun requireConfigured()
    fun verify(idToken: String, expectedNonceHash: String): VerifiedGoogleIdentity
}
