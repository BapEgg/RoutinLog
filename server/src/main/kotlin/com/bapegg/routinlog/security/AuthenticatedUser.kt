package com.bapegg.routinlog.security

import java.util.UUID

data class AuthenticatedUser(val userId: UUID, val sessionId: UUID? = null)
