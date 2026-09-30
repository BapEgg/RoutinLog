package com.bapegg.routinlog.auth

import androidx.annotation.Keep

/** Blocking storage operations; AccountRepository calls these from Dispatchers.IO. */
interface SessionStore {
    fun read(): StoredSession?
    fun write(session: StoredSession)
    fun clear()
}

@Keep data class StoredSession(val accessToken: String, val refreshToken: String, val expiresAtEpochSeconds: Long, val userId: String) {
    override fun toString() = "StoredSession(redacted)"
}
