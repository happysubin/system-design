package com.firstcomecoupon.coupon.application

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "coupon.fallback")
class CouponFallbackProperties {
    var sqlOnlyEnabled: Boolean = false
    var maxConcurrentClaims: Int = 2
}
