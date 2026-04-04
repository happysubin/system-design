package com.firstcomecoupon.coupon.application

import com.firstcomecoupon.coupon.api.dto.IssueCouponRequest
import com.firstcomecoupon.coupon.domain.CouponClaimResult
import com.firstcomecoupon.coupon.infrastructure.redis.CouponClaimGateResult
import com.firstcomecoupon.coupon.infrastructure.redis.CouponClaimRedisGate
import org.springframework.stereotype.Service

@Service
class CouponClaimApplicationService(
    private val couponClaimEligibilityChecker: CouponClaimEligibilityChecker,
    private val couponClaimRedisGate: CouponClaimRedisGate,
    private val couponClaimCompensationHandler: CouponClaimCompensationHandler,
) {

    fun claimCoupon(couponId: Long, request: IssueCouponRequest): CouponClaimResult {
        val eligibility = couponClaimEligibilityChecker.check(couponId, request.memberId)

        if (eligibility is CouponClaimEligibilityResult.Ineligible) {
            return eligibility.result
        }

        val eligible = eligibility as CouponClaimEligibilityResult.Eligible

        return try {
            when (couponClaimRedisGate.tryClaim(couponId, eligible.member.id)) {
                CouponClaimGateResult.ALREADY_CLAIMED -> CouponClaimResult.AlreadyClaimed
                CouponClaimGateResult.SOLD_OUT -> CouponClaimResult.SoldOut
                CouponClaimGateResult.NOT_INITIALIZED -> CouponClaimResult.InternalFailure("coupon stock is not initialized")
                CouponClaimGateResult.PASSED -> couponClaimCompensationHandler.finalizeClaim(couponId, eligible.member.id)
            }
        } catch (_: RuntimeException) {
            CouponClaimResult.InternalFailure("unexpected coupon claim failure")
        }
    }
}
