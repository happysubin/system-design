package com.firstcomecoupon.serivce

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component

@Component
class CouponClaimCompensationHandler(
    private val couponClaimFinalizer: CouponClaimFinalizer,
    private val couponClaimRedisGate: CouponClaimRedisGate,
) {

    fun finalizeClaim(couponId: Long, memberId: Long): CouponClaimResult {
        return try {
            val issue = couponClaimFinalizer.finalizeClaim(couponId, memberId)
            CouponClaimResult.Issued(
                issueId = issue.id,
                couponId = couponId,
                memberId = memberId,
                issuedAt = issue.issuedAt,
            )
        } catch (_: DataIntegrityViolationException) {
            couponClaimRedisGate.rollback(couponId, memberId)
            CouponClaimResult.AlreadyClaimed
        } catch (exception: RuntimeException) {
            couponClaimRedisGate.rollback(couponId, memberId)
            throw exception
        }
    }
}
