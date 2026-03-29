package com.firstcomecoupon.coupon.api

import com.firstcomecoupon.coupon.api.dto.IssueCouponResponse
import com.firstcomecoupon.coupon.domain.CouponClaimResult
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component

@Component
class CouponClaimResponseMapper {

    fun toResponse(couponId: Long, memberId: Long, result: CouponClaimResult): ResponseEntity<IssueCouponResponse> {
        return when (result) {
            is CouponClaimResult.Issued -> ResponseEntity.status(HttpStatus.CREATED).body(
                IssueCouponResponse(
                    result = "ISSUED",
                    couponId = result.couponId,
                    memberId = result.memberId,
                    issueId = result.issueId,
                    issuedAt = result.issuedAt,
                ),
            )

            CouponClaimResult.AlreadyClaimed -> ResponseEntity.status(HttpStatus.CONFLICT).body(
                IssueCouponResponse(
                    result = "ALREADY_CLAIMED",
                    couponId = couponId,
                    memberId = memberId,
                    message = "member already claimed this coupon",
                ),
            )

            CouponClaimResult.SoldOut -> ResponseEntity.status(HttpStatus.CONFLICT).body(
                IssueCouponResponse(
                    result = "SOLD_OUT",
                    couponId = couponId,
                    memberId = memberId,
                    message = "coupon is sold out",
                ),
            )

            CouponClaimResult.CouponNotFound -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                IssueCouponResponse(
                    result = "COUPON_NOT_FOUND",
                    couponId = couponId,
                    memberId = memberId,
                    message = "coupon not found",
                ),
            )

            CouponClaimResult.MemberNotFound -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                IssueCouponResponse(
                    result = "MEMBER_NOT_FOUND",
                    couponId = couponId,
                    memberId = memberId,
                    message = "member not found",
                ),
            )

            CouponClaimResult.NotInIssueWindow -> ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
                IssueCouponResponse(
                    result = "NOT_IN_ISSUE_WINDOW",
                    couponId = couponId,
                    memberId = memberId,
                    message = "coupon is not currently claimable",
                ),
            )

            CouponClaimResult.InternalFailure -> ResponseEntity.internalServerError().body(
                IssueCouponResponse(
                    result = "INTERNAL_FAILURE",
                    couponId = couponId,
                    memberId = memberId,
                    message = "coupon stock is not initialized",
                ),
            )
        }
    }
}
