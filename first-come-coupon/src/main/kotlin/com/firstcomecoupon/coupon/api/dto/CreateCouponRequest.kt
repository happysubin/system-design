package com.firstcomecoupon.coupon.api.dto

import java.time.LocalDateTime

data class CreateCouponRequest(
    val name: String,
    val totalQuantity: Int,
    val issueStartAt: LocalDateTime,
    val issueEndAt: LocalDateTime,
)
