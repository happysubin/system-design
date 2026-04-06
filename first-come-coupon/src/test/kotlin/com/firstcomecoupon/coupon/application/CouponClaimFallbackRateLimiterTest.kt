package com.firstcomecoupon.coupon.application

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CouponClaimFallbackRateLimiterTest {

    @Test
    fun `동시 허용량을 넘기면 acquire가 실패하고 release 후 다시 성공한다`() {
        val limiter = CouponClaimFallbackRateLimiter(
            CouponFallbackProperties().apply { maxConcurrentClaims = 1 },
        )

        assertTrue(limiter.tryAcquire())
        assertFalse(limiter.tryAcquire())

        limiter.release()

        assertTrue(limiter.tryAcquire())
    }
}
