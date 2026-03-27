package com.firstcomecoupon.controller.dto

import java.time.LocalDateTime

data class CreateCouponResponse(
    val id: Long,
    val name: String,
    val totalQuantity: Int,
    val issuedQuantity: Int,
    val issueStartAt: LocalDateTime,
    val issueEndAt: LocalDateTime,
    val createdAt: LocalDateTime,
)
