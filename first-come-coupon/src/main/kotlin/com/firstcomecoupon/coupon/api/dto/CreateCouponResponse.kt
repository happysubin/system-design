package com.firstcomecoupon.coupon.api.dto

import java.time.LocalDateTime

data class CreateCouponResponse(
    val id: Long,
    val name: String,
    val totalQuantity: Int,
    val issueStartAt: LocalDateTime,
    val issueEndAt: LocalDateTime,
    val createdAt: LocalDateTime,
)
