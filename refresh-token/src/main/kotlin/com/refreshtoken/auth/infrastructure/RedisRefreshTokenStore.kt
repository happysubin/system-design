package com.refreshtoken.auth.infrastructure

import com.refreshtoken.auth.domain.RefreshTokenStore
import com.refreshtoken.auth.domain.RefreshTokenStoreUnavailableException
import java.time.Duration
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository

@Repository
class RedisRefreshTokenStore(
    private val redisTemplate: StringRedisTemplate,
    private val redisKeyNamespace: RedisKeyNamespace,
) : RefreshTokenStore {
    override fun save(refreshToken: String, subject: String, ttl: Duration) {
        try {
            redisTemplate.opsForValue().set(key(refreshToken), subject, ttl)
        } catch (_: RuntimeException) {
            throw RefreshTokenStoreUnavailableException()
        }
    }

    override fun exists(refreshToken: String): Boolean =
        try {
            redisTemplate.hasKey(key(refreshToken)) == true
        } catch (_: RuntimeException) {
            throw RefreshTokenStoreUnavailableException()
        }

    override fun delete(refreshToken: String) {
        try {
            redisTemplate.delete(key(refreshToken))
        } catch (_: RuntimeException) {
            throw RefreshTokenStoreUnavailableException()
        }
    }

    private fun key(refreshToken: String): String = redisKeyNamespace.refreshTokenKey(refreshToken)
}
