package com.firstcomecoupon.coupon.domain

import java.time.LocalDateTime

sealed interface CouponClaimResult {
    data class Issued(
        val issueId: Long,
        val couponId: Long,
        val memberId: Long,
        val issuedAt: LocalDateTime,
    ) : CouponClaimResult

    data object AlreadyClaimed : CouponClaimResult
    data object SoldOut : CouponClaimResult
    data object CouponNotFound : CouponClaimResult
    data object MemberNotFound : CouponClaimResult
    data object NotInIssueWindow : CouponClaimResult
    data class InternalFailure(
        val message: String,
    ) : CouponClaimResult
}
