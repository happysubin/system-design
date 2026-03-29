package com.firstcomecoupon.coupon.infrastructure.reconciliation

import com.firstcomecoupon.coupon.infrastructure.persistence.CouponIssueRepository
import com.firstcomecoupon.coupon.infrastructure.persistence.CouponRepository
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class CouponStockReconciliationService(
    private val couponRepository: CouponRepository,
    private val couponIssueRepository: CouponIssueRepository,
    private val stringRedisTemplate: StringRedisTemplate,
) {

    fun reconcileActiveCouponStocks(referenceTime: LocalDateTime = LocalDateTime.now()) {
        val valueOperations = stringRedisTemplate.opsForValue()
        couponRepository.findActiveCoupons(referenceTime).forEach { coupon ->
            val issuedCount = couponIssueRepository.countByCouponId(coupon.id)
            val remainingQuantity = (coupon.totalQuantity.toLong() - issuedCount).coerceAtLeast(0)
            valueOperations.set(stockKey(coupon.id), remainingQuantity.toString())
        }
    }

    fun reconcileCouponStock(couponId: Long) {
        val coupon = couponRepository.findById(couponId).orElseThrow()
        val issuedCount = couponIssueRepository.countByCouponId(coupon.id)
        val remainingQuantity = (coupon.totalQuantity.toLong() - issuedCount).coerceAtLeast(0)
        stringRedisTemplate.opsForValue().set(stockKey(coupon.id), remainingQuantity.toString())
    }

    private fun stockKey(couponId: Long): String = "coupon:stock:$couponId"
}
