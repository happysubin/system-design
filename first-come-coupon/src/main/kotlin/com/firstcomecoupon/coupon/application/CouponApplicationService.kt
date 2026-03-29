package com.firstcomecoupon.coupon.application

import com.firstcomecoupon.coupon.api.dto.CreateCouponRequest
import com.firstcomecoupon.coupon.api.dto.CreateCouponResponse
import com.firstcomecoupon.coupon.domain.Coupon
import com.firstcomecoupon.coupon.infrastructure.persistence.CouponRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.data.redis.core.StringRedisTemplate

@Service
class CouponApplicationService(
    private val couponRepository: CouponRepository,
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
