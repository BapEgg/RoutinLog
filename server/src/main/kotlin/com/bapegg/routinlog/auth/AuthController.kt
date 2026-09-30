package com.bapegg.routinlog.auth

import com.bapegg.routinlog.security.AuthenticatedUser
import jakarta.validation.Valid
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val verifier: GoogleTokenVerifier,
    private val reauthentication: GoogleReauthenticationService,
    private val sessions: AuthSessionService,
) {
    @PostMapping("/google/challenge")
    fun challenge(): ResponseEntity<ChallengeResponse> {
        verifier.requireConfigured()
        return privateResponse(sessions.createChallenge())
    }

    @PostMapping("/google")
    fun google(@Valid @RequestBody request: GoogleLoginRequest): ResponseEntity<SessionTokens> {
        val verified = reauthentication.verify(request)
        return privateResponse(sessions.signIn(request.challengeId, verified))
    }

    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody request: RefreshRequest): ResponseEntity<SessionTokens> =
        privateResponse(sessions.refresh(request.refreshToken) ?: throw AuthFailure())

    @PostMapping("/logout")
    fun logout(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @Valid @RequestBody(required = false) request: LogoutRequest?,
    ): ResponseEntity<Void> {
        sessions.logout(user, request?.refreshToken)
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build()
    }

    private fun <T : Any> privateResponse(body: T): ResponseEntity<T> =
        ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body)
}

@RestControllerAdvice(assignableTypes = [AuthController::class, AccountDeletionController::class])
class AuthExceptionHandler {
    @ExceptionHandler(AuthFailure::class)
    fun authentication(failure: AuthFailure): ResponseEntity<AuthErrorResponse> = ResponseEntity.status(failure.status)
        .cacheControl(CacheControl.noStore()).body(AuthErrorResponse(failure.code, failure.message))

    @ExceptionHandler(MethodArgumentNotValidException::class, HttpMessageNotReadableException::class)
    fun invalidInput(): ResponseEntity<AuthErrorResponse> = ResponseEntity.badRequest()
        .cacheControl(CacheControl.noStore()).body(AuthErrorResponse("INVALID_REQUEST", "요청 형식을 확인해 주세요."))

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun concurrentSignIn(): ResponseEntity<AuthErrorResponse> = ResponseEntity.status(HttpStatus.CONFLICT)
        .cacheControl(CacheControl.noStore()).body(AuthErrorResponse("AUTH_RETRY", "로그인을 다시 시도해 주세요."))
}
