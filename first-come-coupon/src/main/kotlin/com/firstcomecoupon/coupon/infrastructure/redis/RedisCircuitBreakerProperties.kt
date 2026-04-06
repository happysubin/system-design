package com.firstcomecoupon.coupon.infrastructure.redis

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import java.time.Duration

@Component
@ConfigurationProperties(prefix = "coupon.redis.circuit-breaker")
class RedisCircuitBreakerProperties {
    var failureRateThreshold: Float = 50f
    var slidingWindowSize: Int = 10
    var minimumNumberOfCalls: Int = 5
    var openDuration: Duration = Duration.ofSeconds(30)
    var permittedCallsInHalfOpenState: Int = 1
}
