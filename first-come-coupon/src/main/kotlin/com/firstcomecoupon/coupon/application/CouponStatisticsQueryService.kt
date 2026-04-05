package com.firstcomecoupon.coupon.application

import com.firstcomecoupon.coupon.infrastructure.persistence.CouponIssueRepository
import com.firstcomecoupon.coupon.infrastructure.persistence.CouponRepository
import com.firstcomecoupon.coupon.infrastructure.persistence.CouponStockRepository
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
    private val couponStockRepository: CouponStockRepository,
) {

    fun getCouponStatistics(couponId: Long): CouponStatistics {
        val coupon = couponRepository.findById(couponId).orElseThrow()
        val stock = couponStockRepository.findByCouponId(couponId) ?: throw NoSuchElementException()
        val remainingQuantity = stock.remainingQuantity.toLong()
        val issuedCount = coupon.totalQuantity.toLong() - remainingQuantity

        return CouponStatistics(
            couponId = coupon.id,
            totalQuantity = coupon.totalQuantity,
            issuedCount = issuedCount,
            remainingQuantity = remainingQuantity,
        )
    }
}
