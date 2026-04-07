package com.refreshtoken.auth.infrastructure

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import org.springframework.stereotype.Component

@Component
class RedisKeyNamespace(
    private val properties: RedisKeyProperties,
) {
    fun refreshTokenKey(refreshToken: String): String {
        val tokenHash = sha256(refreshToken)
        return "${properties.environment}:${properties.appName}:auth:refresh-token:v1:$tokenHash"
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString(separator = "") { "%02x".format(it) }
    }
}
