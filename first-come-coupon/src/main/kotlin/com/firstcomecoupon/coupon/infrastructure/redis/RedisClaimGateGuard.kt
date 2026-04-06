package com.firstcomecoupon.coupon.infrastructure.redis

import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class RedisClaimGateGuard(
    private val couponClaimRedisGate: CouponClaimRedisGate,
    private val properties: RedisCircuitBreakerProperties,
) {
    private val circuitBreaker: CircuitBreaker = CircuitBreaker.of("redisClaimGate", circuitBreakerConfig())

    fun tryClaim(couponId: Long, memberId: Long): CouponClaimGateResult {
        return try {
            circuitBreaker.executeSupplier {
                couponClaimRedisGate.tryClaim(couponId, memberId)
            }
        } catch (exception: CallNotPermittedException) {
            logger.warn("Redis claim gate circuit is OPEN")
            onCircuitOpen(couponId, memberId)
            throw RedisGateUnavailableException()
        } catch (exception: RuntimeException) {
            logger.warn("Redis claim gate call failed", exception)
            onRedisGateFailure(couponId, memberId, exception)
            throw RedisGateUnavailableException()
        }
    }

    private fun circuitBreakerConfig(): CircuitBreakerConfig =
        CircuitBreakerConfig.custom()
            .failureRateThreshold(properties.failureRateThreshold)
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(properties.slidingWindowSize)
            .minimumNumberOfCalls(properties.minimumNumberOfCalls)
            .waitDurationInOpenState(properties.openDuration)
            .permittedNumberOfCallsInHalfOpenState(properties.permittedCallsInHalfOpenState)
            .recordExceptions(RuntimeException::class.java)
            .build()

    private fun onCircuitOpen(couponId: Long, memberId: Long) {
        // TODO: Redis gate circuit OPEN 시 운영 알람(PagerDuty/Slack 등) 연동
    }

    private fun onRedisGateFailure(couponId: Long, memberId: Long, exception: RuntimeException) {
        // TODO: Redis gate 호출 실패 누적 시 운영 알람/메트릭 연동
    }

    internal fun currentState(): CircuitBreaker.State = circuitBreaker.state

    companion object {
        private val logger = LoggerFactory.getLogger(RedisClaimGateGuard::class.java)
    }
}
