package com.bapegg.routinlog.auth

import org.springframework.stereotype.Service

@Service
class GoogleReauthenticationService(private val verifier: GoogleTokenVerifier, private val sessions: AuthSessionService) {
    fun verify(request: GoogleLoginRequest): VerifiedGoogleIdentity {
        verifier.requireConfigured()
        val hash = sessions.nonceHash(request.challengeId)
        return verifier.verify(request.idToken, hash)
    }
}
