package com.firstcomecoupon.serivce

import com.firstcomecoupon.domain.Coupon
import com.firstcomecoupon.domain.Member
import com.firstcomecoupon.repository.CouponRepository
import com.firstcomecoupon.repository.MemberRepository
import org.springframework.stereotype.Component
import java.time.LocalDateTime

sealed interface CouponClaimEligibilityResult {
    data class Eligible(
        val coupon: Coupon,
        val member: Member,
    ) : CouponClaimEligibilityResult

    data class Ineligible(
        val result: CouponClaimResult,
    ) : CouponClaimEligibilityResult
}

@Component
class CouponClaimEligibilityChecker(
    private val couponRepository: CouponRepository,
    private val memberRepository: MemberRepository,
) {

    fun check(couponId: Long, memberId: Long): CouponClaimEligibilityResult {
        val coupon = couponRepository.findById(couponId).orElse(null)
            ?: return CouponClaimEligibilityResult.Ineligible(CouponClaimResult.CouponNotFound)

        val member = memberRepository.findById(memberId).orElse(null)
            ?: return CouponClaimEligibilityResult.Ineligible(CouponClaimResult.MemberNotFound)

        val now = LocalDateTime.now()
        if (now.isBefore(coupon.issueStartAt) || now.isAfter(coupon.issueEndAt)) {
            return CouponClaimEligibilityResult.Ineligible(CouponClaimResult.NotInIssueWindow)
        }

        return CouponClaimEligibilityResult.Eligible(coupon, member)
    }
}
