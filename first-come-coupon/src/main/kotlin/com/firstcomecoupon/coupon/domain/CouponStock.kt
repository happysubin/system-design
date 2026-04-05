package com.firstcomecoupon.coupon.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "coupon_stocks")
class CouponStock(
    @Id
    @Column(name = "coupon_id", nullable = false)
    var couponId: Long = 0,

    @Column(name = "remaining_quantity", nullable = false)
    var remainingQuantity: Int = 0,

    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),
)
