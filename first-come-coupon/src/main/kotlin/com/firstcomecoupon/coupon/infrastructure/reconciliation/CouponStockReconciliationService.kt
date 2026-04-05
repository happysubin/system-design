package com.firstcomecoupon.coupon.infrastructure.reconciliation

import com.firstcomecoupon.coupon.infrastructure.persistence.CouponIssueRepository
import com.firstcomecoupon.coupon.infrastructure.persistence.CouponRepository
import com.firstcomecoupon.coupon.infrastructure.persistence.CouponStockRepository
import com.firstcomecoupon.coupon.domain.CouponStock
import org.springframework.data.redis.core.ScanOptions
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime

@Service
class CouponStockReconciliationService(
    private val couponRepository: CouponRepository,
    private val couponIssueRepository: CouponIssueRepository,
    private val couponStockRepository: CouponStockRepository,
    private val stringRedisTemplate: StringRedisTemplate,
) {

    fun reconcileActiveCouponStocks(referenceTime: LocalDateTime = LocalDateTime.now()) {
        val valueOperations = stringRedisTemplate.opsForValue()
        couponRepository.findActiveCoupons(referenceTime).forEach { coupon ->
            val stock = couponStockRepository.findByCouponId(coupon.id) ?: createMissingStock(coupon)
            valueOperations.set(stockKey(coupon.id), stock.remainingQuantity.toString())
            cleanupOrphanClaimMarkers(coupon.id)
        }
    }

    fun reconcileCouponStock(couponId: Long) {
        val coupon = couponRepository.findById(couponId).orElseThrow()
        val stock = couponStockRepository.findByCouponId(coupon.id) ?: createMissingStock(coupon)
        stringRedisTemplate.opsForValue().set(stockKey(coupon.id), stock.remainingQuantity.toString())
        cleanupOrphanClaimMarkers(coupon.id)
    }

    private fun stockKey(couponId: Long): String = "coupon:stock:$couponId"

    private fun cleanupOrphanClaimMarkers(couponId: Long) {
        scanClaimKeys(couponId).forEach { key ->
            val memberId = extractMemberId(key) ?: return@forEach
            if (!couponIssueRepository.existsByCouponIdAndMemberId(couponId, memberId)) {
                stringRedisTemplate.delete(key)
            }
        }
    }

    private fun scanClaimKeys(couponId: Long): Set<String> =
        stringRedisTemplate.execute { connection ->
            val scanOptions = ScanOptions.scanOptions()
                .match(claimKeyPattern(couponId))
                .count(500)
                .build()

            connection.keyCommands().scan(scanOptions).use { cursor ->
                buildSet {
                    while (cursor.hasNext()) {
                        add(String(cursor.next(), StandardCharsets.UTF_8))
                    }
                }
            }
        } ?: emptySet()

    private fun claimKeyPattern(couponId: Long): String = "coupon:claim:$couponId:*"

    private fun extractMemberId(claimKey: String): Long? = claimKey.substringAfterLast(':').toLongOrNull()

    private fun createMissingStock(coupon: com.firstcomecoupon.coupon.domain.Coupon): CouponStock {
        val issuedCount = couponIssueRepository.countByCouponId(coupon.id)
        val remainingQuantity = (coupon.totalQuantity.toLong() - issuedCount).coerceAtLeast(0).toInt()
        return couponStockRepository.save(
            CouponStock(
                couponId = coupon.id,
                remainingQuantity = remainingQuantity,
            ),
        )
    }
}
