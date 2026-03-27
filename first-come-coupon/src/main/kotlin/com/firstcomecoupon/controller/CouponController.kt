package com.firstcomecoupon.controller

import com.firstcomecoupon.controller.dto.CreateCouponRequest
import com.firstcomecoupon.controller.dto.CreateCouponResponse
import com.firstcomecoupon.controller.dto.IssueCouponRequest
import com.firstcomecoupon.controller.dto.IssueCouponResponse
import com.firstcomecoupon.serivce.CouponClaimResult
import com.firstcomecoupon.serivce.CouponClaimService
import com.firstcomecoupon.serivce.CouponService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

@RestController
@RequestMapping("/api/v1/coupons")
class CouponController(
    private val couponService: CouponService,
    private val couponClaimService: CouponClaimService,
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createCoupon(@RequestBody request: CreateCouponRequest): CreateCouponResponse {
        return couponService.createCoupon(request)
    }

    @PostMapping("/{couponId}/claim")
    fun claimCoupon(
        @PathVariable couponId: Long,
        @RequestBody request: IssueCouponRequest,
    ): ResponseEntity<IssueCouponResponse> {
        return when (val result = couponClaimService.claimCoupon(couponId, request)) {
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
                    memberId = request.memberId,
                    message = "member already claimed this coupon",
                ),
            )

            CouponClaimResult.SoldOut -> ResponseEntity.status(HttpStatus.CONFLICT).body(
                IssueCouponResponse(
                    result = "SOLD_OUT",
                    couponId = couponId,
                    memberId = request.memberId,
                    message = "coupon is sold out",
                ),
            )

            CouponClaimResult.CouponNotFound -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                IssueCouponResponse(
                    result = "COUPON_NOT_FOUND",
                    couponId = couponId,
                    memberId = request.memberId,
                    message = "coupon not found",
                ),
            )

            CouponClaimResult.MemberNotFound -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                IssueCouponResponse(
                    result = "MEMBER_NOT_FOUND",
                    couponId = couponId,
                    memberId = request.memberId,
                    message = "member not found",
                ),
            )

            CouponClaimResult.NotInIssueWindow -> ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
                IssueCouponResponse(
                    result = "NOT_IN_ISSUE_WINDOW",
                    couponId = couponId,
                    memberId = request.memberId,
                    message = "coupon is not currently claimable",
                ),
            )

            CouponClaimResult.InternalFailure -> ResponseEntity.internalServerError().body(
                IssueCouponResponse(
                    result = "INTERNAL_FAILURE",
                    couponId = couponId,
                    memberId = request.memberId,
                    message = "coupon stock is not initialized",
                ),
            )
        }
    }

}
