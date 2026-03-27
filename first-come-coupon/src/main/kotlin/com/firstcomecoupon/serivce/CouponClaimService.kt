package com.firstcomecoupon.serivce

import com.firstcomecoupon.controller.dto.IssueCouponRequest
import org.springframework.stereotype.Service

@Service
class CouponClaimService(
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

        return when (couponClaimRedisGate.tryClaim(couponId, eligible.member.id)) {
            CouponClaimGateResult.ALREADY_CLAIMED -> CouponClaimResult.AlreadyClaimed
            CouponClaimGateResult.SOLD_OUT -> CouponClaimResult.SoldOut
            CouponClaimGateResult.NOT_INITIALIZED -> CouponClaimResult.InternalFailure
            CouponClaimGateResult.PASSED -> couponClaimCompensationHandler.finalizeClaim(couponId, eligible.member.id)
        }
    }
}
