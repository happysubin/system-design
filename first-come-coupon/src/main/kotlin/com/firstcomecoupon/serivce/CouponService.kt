package com.firstcomecoupon.serivce

import com.firstcomecoupon.controller.dto.CreateCouponRequest
import com.firstcomecoupon.controller.dto.CreateCouponResponse
import com.firstcomecoupon.domain.Coupon
import com.firstcomecoupon.repository.CouponRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.data.redis.core.StringRedisTemplate

@Service
class CouponService(
    private val couponRepository: CouponRepository,
    private val stringRedisTemplate: StringRedisTemplate,
) {

    @Transactional
    fun createCoupon(request: CreateCouponRequest): CreateCouponResponse {
        val savedCoupon = couponRepository.save(
            Coupon(
                name = request.name,
                totalQuantity = request.totalQuantity,
                issuedQuantity = 0,
                issueStartAt = request.issueStartAt,
                issueEndAt = request.issueEndAt,
            ),
        )

        stringRedisTemplate.opsForValue().set("coupon:stock:${savedCoupon.id}", savedCoupon.totalQuantity.toString())

        return CreateCouponResponse(
            id = savedCoupon.id,
            name = savedCoupon.name,
            totalQuantity = savedCoupon.totalQuantity,
            issuedQuantity = savedCoupon.issuedQuantity,
            issueStartAt = savedCoupon.issueStartAt,
            issueEndAt = savedCoupon.issueEndAt,
            createdAt = savedCoupon.createdAt,
        )
    }
}
