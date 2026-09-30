package com.bapegg.routinlog.auth

import com.bapegg.routinlog.security.AuthenticatedUser
import jakarta.validation.Valid
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class AccountDeletionController(private val reauthentication: GoogleReauthenticationService, private val sessions: AuthSessionService) {
    @DeleteMapping("/api/v1/me")
    fun deleteAccount(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @Valid @RequestBody request: GoogleLoginRequest,
    ): ResponseEntity<Void> {
        val verified = try { reauthentication.verify(request) } catch (failure: AuthFailure) {
            if (failure.status == HttpStatus.UNAUTHORIZED) throw proofFailure(failure)
            throw failure
        }
        try { sessions.deleteAccount(user, request.challengeId, verified) } catch (failure: AuthFailure) {
            if (failure.code == "AUTH_CHALLENGE_INVALID") throw proofFailure(failure)
            throw failure
        }
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build()
    }

    private fun proofFailure(failure: AuthFailure): AuthFailure = AuthFailure(
        if (failure.code == "AUTH_CHALLENGE_INVALID") failure.code else "AUTH_REAUTH_REQUIRED",
        HttpStatus.FORBIDDEN, "계정 삭제를 위해 현재 Google 계정으로 다시 확인해 주세요.",
    )
}
