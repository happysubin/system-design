package com.refreshtoken.auth.infrastructure

import com.refreshtoken.auth.domain.RefreshTokenStore
import com.refreshtoken.auth.domain.RefreshTokenStoreUnavailableException
import java.time.Duration
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.StringRedisTemplate
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.springframework.data.redis.core.ValueOperations
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean

@SpringBootTest(
    properties = [
        "resilience4j.circuitbreaker.instances.redisRefreshTokenStore.slidingWindowSize=2",
        "resilience4j.circuitbreaker.instances.redisRefreshTokenStore.minimumNumberOfCalls=2",
        "resilience4j.circuitbreaker.instances.redisRefreshTokenStore.failureRateThreshold=50",
        "resilience4j.circuitbreaker.instances.redisRefreshTokenStore.waitDurationInOpenState=60s",
    ],
)
@ActiveProfiles("test")
class RedisCircuitBreakerTest {

    @Autowired
    private lateinit var store: RefreshTokenStore

    @MockitoBean
    private lateinit var redisTemplate: StringRedisTemplate

    @Test
    fun `opens circuit after repeated failures and then fast fails without redis call`() {
        val valueOperations = org.mockito.Mockito.mock(ValueOperations::class.java) as ValueOperations<String, String>
        given(redisTemplate.opsForValue()).willReturn(valueOperations)
        given(valueOperations.set(anyString(), anyString(), any(Duration::class.java))).willAnswer {
            throw RuntimeException("redis down")
        }

        assertFailsWith<RefreshTokenStoreUnavailableException> {
            store.save("token-1", "demo", Duration.ofDays(14))
        }
        assertFailsWith<RefreshTokenStoreUnavailableException> {
            store.save("token-2", "demo", Duration.ofDays(14))
        }
        assertFailsWith<RefreshTokenStoreUnavailableException> {
            store.save("token-3", "demo", Duration.ofDays(14))
        }

        assertFailsWith<RefreshTokenStoreUnavailableException> {
            store.save("token-4", "demo", Duration.ofDays(14))
        }

        verify(valueOperations, times(2)).set(anyString(), anyString(), any(Duration::class.java))
    }
}
