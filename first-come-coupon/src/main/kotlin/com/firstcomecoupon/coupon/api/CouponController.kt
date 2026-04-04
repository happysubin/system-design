package com.firstcomecoupon.coupon.api

import com.firstcomecoupon.coupon.api.dto.CreateCouponRequest
import com.firstcomecoupon.coupon.api.dto.CreateCouponResponse
import com.firstcomecoupon.coupon.api.dto.IssueCouponRequest
import com.firstcomecoupon.coupon.api.dto.IssueCouponResponse
import com.firstcomecoupon.coupon.application.CouponApplicationService
import com.firstcomecoupon.coupon.application.CouponClaimApplicationService
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
    private val couponApplicationService: CouponApplicationService,
    private val couponClaimApplicationService: CouponClaimApplicationService,
    private val couponClaimResponseMapper: CouponClaimResponseMapper,
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createCoupon(@RequestBody request: CreateCouponRequest): CreateCouponResponse {
        return couponApplicationService.createCoupon(request)
    }

    @PostMapping("/{couponId}/claim")
    fun claimCoupon(
        @PathVariable couponId: Long,
        @RequestBody request: IssueCouponRequest,
    ): ResponseEntity<IssueCouponResponse> {
        val result = try {
            couponClaimApplicationService.claimCoupon(couponId, request)
        } catch (_: RuntimeException) {
            com.firstcomecoupon.coupon.domain.CouponClaimResult.InternalFailure("unexpected coupon claim failure")
        }

        return couponClaimResponseMapper.toResponse(couponId, request.memberId, result)
    }

}
