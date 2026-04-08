package com.refreshtoken.auth.infrastructure

import java.time.Duration
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations

class ResilientRefreshTokenStoreTest {

    @Test
    fun `raw redis store propagates runtime exception without resilience translation`() {
        val redisTemplate = mock(StringRedisTemplate::class.java)
        val valueOperations = mock(ValueOperations::class.java) as ValueOperations<String, String>
        given(redisTemplate.opsForValue()).willReturn(valueOperations)
        given(valueOperations.set(anyString(), anyString(), any(Duration::class.java))).willAnswer {
            throw RuntimeException("redis down")
        }

        val store = RedisRefreshTokenStore(
            redisTemplate = redisTemplate,
            redisKeyNamespace = RedisKeyNamespace(RedisKeyProperties("test", "refresh-token")),
        )

        assertFailsWith<RuntimeException> {
            store.save("token-1", "demo", Duration.ofDays(14))
        }
    }
}
