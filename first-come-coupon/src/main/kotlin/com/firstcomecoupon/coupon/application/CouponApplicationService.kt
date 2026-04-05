package com.firstcomecoupon.coupon.application

import com.firstcomecoupon.coupon.api.dto.CreateCouponRequest
import com.firstcomecoupon.coupon.api.dto.CreateCouponResponse
import com.firstcomecoupon.coupon.domain.Coupon
import com.firstcomecoupon.coupon.domain.CouponStock
import com.firstcomecoupon.coupon.infrastructure.persistence.CouponRepository
import com.firstcomecoupon.coupon.infrastructure.persistence.CouponStockRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.data.redis.core.StringRedisTemplate

@Service
class CouponApplicationService(
    private val couponRepository: CouponRepository,
    private val couponStockRepository: CouponStockRepository,
    private val stringRedisTemplate: StringRedisTemplate,
) {

    @Transactional
    fun createCoupon(request: CreateCouponRequest): CreateCouponResponse {
        val savedCoupon = couponRepository.save(
            Coupon(
                name = request.name,
                totalQuantity = request.totalQuantity,
                issueStartAt = request.issueStartAt,
                issueEndAt = request.issueEndAt,
            ),
        )

        couponStockRepository.save(
            CouponStock(
                couponId = savedCoupon.id,
                remainingQuantity = savedCoupon.totalQuantity,
            ),
        )

        stringRedisTemplate.opsForValue().set("coupon:stock:${savedCoupon.id}", savedCoupon.totalQuantity.toString())

        return CreateCouponResponse(
            id = savedCoupon.id,
            name = savedCoupon.name,
            totalQuantity = savedCoupon.totalQuantity,
            issueStartAt = savedCoupon.issueStartAt,
            issueEndAt = savedCoupon.issueEndAt,
            createdAt = savedCoupon.createdAt,
        )
    }
}
