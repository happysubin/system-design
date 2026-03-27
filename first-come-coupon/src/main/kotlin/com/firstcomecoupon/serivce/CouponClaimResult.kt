package com.firstcomecoupon.serivce

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
    data object InternalFailure : CouponClaimResult
}
