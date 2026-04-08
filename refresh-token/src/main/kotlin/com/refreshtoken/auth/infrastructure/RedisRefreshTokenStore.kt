package com.refreshtoken.auth.infrastructure

import com.refreshtoken.auth.domain.RefreshTokenStore
import java.time.Duration
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository

@Repository
class RedisRefreshTokenStore(
    private val redisTemplate: StringRedisTemplate,
    private val redisKeyNamespace: RedisKeyNamespace,
) : RefreshTokenStore {
    override fun save(refreshToken: String, subject: String, ttl: Duration) {
        redisTemplate.opsForValue().set(key(refreshToken), subject, ttl)
    }

    override fun exists(refreshToken: String): Boolean =
        redisTemplate.hasKey(key(refreshToken)) == true

    override fun delete(refreshToken: String) {
        redisTemplate.delete(key(refreshToken))
    }

    private fun key(refreshToken: String): String = redisKeyNamespace.refreshTokenKey(refreshToken)
}
