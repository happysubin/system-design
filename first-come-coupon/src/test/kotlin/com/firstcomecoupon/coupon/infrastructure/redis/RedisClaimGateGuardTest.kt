package com.firstcomecoupon.coupon.infrastructure.redis

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import java.time.Duration

@ExtendWith(MockitoExtension::class)
class RedisClaimGateGuardTest {

    @Mock
    lateinit var couponClaimRedisGate: CouponClaimRedisGate

    @Test
    fun `연속 실패가 임계치를 넘기면 breaker가 열리고 이후 요청은 즉시 실패한다`() {
        val guard = RedisClaimGateGuard(
            couponClaimRedisGate,
            RedisCircuitBreakerProperties().apply {
                failureRateThreshold = 100f
                slidingWindowSize = 2
                minimumNumberOfCalls = 2
                openDuration = Duration.ofMillis(100)
                permittedCallsInHalfOpenState = 1
            },
        )

        given(couponClaimRedisGate.tryClaim(1L, 1L)).willThrow(IllegalStateException("redis down"))

        assertThrows(RedisGateUnavailableException::class.java) { guard.tryClaim(1L, 1L) }
        assertThrows(RedisGateUnavailableException::class.java) { guard.tryClaim(1L, 1L) }
        assertEquals(CircuitBreaker.State.OPEN, guard.currentState())

        assertThrows(RedisGateUnavailableException::class.java) { guard.tryClaim(1L, 1L) }
        verify(couponClaimRedisGate, times(2)).tryClaim(1L, 1L)
    }

    @Test
    fun `open 기간이 지나면 half open probe를 허용하고 성공 시 breaker를 닫는다`() {
        val guard = RedisClaimGateGuard(
            couponClaimRedisGate,
            RedisCircuitBreakerProperties().apply {
                failureRateThreshold = 100f
                slidingWindowSize = 1
                minimumNumberOfCalls = 1
                openDuration = Duration.ofMillis(50)
                permittedCallsInHalfOpenState = 1
            },
        )

        given(couponClaimRedisGate.tryClaim(1L, 1L))
            .willThrow(IllegalStateException("redis down"))
            .willReturn(CouponClaimGateResult.PASSED)

        assertThrows(RedisGateUnavailableException::class.java) { guard.tryClaim(1L, 1L) }
        Thread.sleep(80)

        val result = guard.tryClaim(1L, 1L)

        assertEquals(CouponClaimGateResult.PASSED, result)
        assertEquals(CircuitBreaker.State.CLOSED, guard.currentState())
        verify(couponClaimRedisGate, times(2)).tryClaim(1L, 1L)
    }
}
