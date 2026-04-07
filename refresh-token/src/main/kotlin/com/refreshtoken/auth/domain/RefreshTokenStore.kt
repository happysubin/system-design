package com.refreshtoken.auth.domain

import java.time.Duration

interface RefreshTokenStore {
    fun save(refreshToken: String, subject: String, ttl: Duration)

    fun exists(refreshToken: String): Boolean

    fun delete(refreshToken: String)
}

class RefreshTokenStoreUnavailableException : RuntimeException("Refresh token store unavailable")
