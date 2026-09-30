package com.bapegg.routinlog.auth

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.HexFormat

internal object TokenSecrets {
    private val random = SecureRandom()
    fun random(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))
    fun access(): String = "rl_at_${random()}"
    fun refresh(): String = "rl_rt_${random()}"
    fun hash(value: String): String = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8)))
    fun matchesHash(value: String, expectedHash: String): Boolean = MessageDigest.isEqual(
        hash(value).toByteArray(StandardCharsets.US_ASCII), expectedHash.toByteArray(StandardCharsets.US_ASCII),
    )
    fun isAccess(value: String): Boolean = value.matches(Regex("rl_at_[A-Za-z0-9_-]{43}"))
    fun isRefresh(value: String): Boolean = value.matches(Regex("rl_rt_[A-Za-z0-9_-]{43}"))
}
