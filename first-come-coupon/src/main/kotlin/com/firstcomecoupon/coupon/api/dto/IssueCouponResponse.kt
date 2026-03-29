package com.firstcomecoupon.coupon.api.dto

import java.time.LocalDateTime

data class IssueCouponResponse(
    val result: String,
    val couponId: Long,
    val memberId: Long,
    val issueId: Long? = null,
    val issuedAt: LocalDateTime? = null,
    val message: String? = null,
)
