package com.firstcomecoupon.coupon.application

import io.github.resilience4j.bulkhead.Bulkhead
import io.github.resilience4j.bulkhead.BulkheadConfig
import io.github.resilience4j.bulkhead.BulkheadFullException
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class CouponClaimFallbackRateLimiter(
    properties: CouponFallbackProperties,
) {
    private val bulkhead = Bulkhead.of(
        "couponSqlFallback",
        BulkheadConfig.custom()
            .maxConcurrentCalls(properties.maxConcurrentClaims)
            .maxWaitDuration(Duration.ZERO)
            .build(),
    )

    fun tryAcquire(): Boolean =
        try {
            bulkhead.acquirePermission()
            true
        } catch (_: BulkheadFullException) {
            false
        }

    fun release() {
        bulkhead.onComplete()
    }
}
