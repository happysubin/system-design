package com.firstcomecoupon.coupon

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class CouponLayerStructureTest {

    @Test
    fun `쿠폰 기능이 4계층 패키지 구조로 분리되어 있다`() {
        assertNotNull(Class.forName("com.firstcomecoupon.coupon.api.CouponController"))
        assertNotNull(Class.forName("com.firstcomecoupon.coupon.application.CouponApplicationService"))
        assertNotNull(Class.forName("com.firstcomecoupon.coupon.application.CouponClaimApplicationService"))
        assertNotNull(Class.forName("com.firstcomecoupon.coupon.domain.Coupon"))
        assertNotNull(Class.forName("com.firstcomecoupon.coupon.domain.CouponClaimResult"))
        assertNotNull(Class.forName("com.firstcomecoupon.coupon.infrastructure.redis.CouponClaimRedisGate"))
        assertNotNull(Class.forName("com.firstcomecoupon.coupon.infrastructure.persistence.CouponRepository"))
    }
}
