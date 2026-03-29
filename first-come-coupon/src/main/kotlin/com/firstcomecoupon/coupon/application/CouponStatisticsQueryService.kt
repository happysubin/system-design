package com.firstcomecoupon.coupon.application

import com.firstcomecoupon.coupon.infrastructure.persistence.CouponIssueRepository
import com.firstcomecoupon.coupon.infrastructure.persistence.CouponRepository
import org.springframework.stereotype.Service

data class CouponStatistics(
    val couponId: Long,
    val totalQuantity: Int,
    val issuedCount: Long,
    val remainingQuantity: Long,
)

@Service
class CouponStatisticsQueryService(
    private val couponRepository: CouponRepository,
    private val couponIssueRepository: CouponIssueRepository,
) {

    fun getCouponStatistics(couponId: Long): CouponStatistics {
        val coupon = couponRepository.findById(couponId).orElseThrow()
        val issuedCount = couponIssueRepository.countByCouponId(couponId)

        return CouponStatistics(
            couponId = coupon.id,
            totalQuantity = coupon.totalQuantity,
            issuedCount = issuedCount,
            remainingQuantity = coupon.totalQuantity.toLong() - issuedCount,
        )
    }
}
