package com.firstcomecoupon.serivce

import com.firstcomecoupon.controller.dto.IssueCouponRequest
import com.firstcomecoupon.domain.Coupon
import com.firstcomecoupon.domain.Member
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExtendWith(MockitoExtension::class)
class CouponClaimServiceTest {

    @Mock
    lateinit var couponClaimEligibilityChecker: CouponClaimEligibilityChecker

    @Mock
    lateinit var couponClaimRedisGate: CouponClaimRedisGate

    @Mock
    lateinit var couponClaimCompensationHandler: CouponClaimCompensationHandler

    @Test
    fun `issues coupon when redis gate passes and compensation handler succeeds`() {
        val coupon = activeCoupon()
        val member = member()
        val request = IssueCouponRequest(memberId = member.id)
        val service = CouponClaimService(couponClaimEligibilityChecker, couponClaimRedisGate, couponClaimCompensationHandler)
        val issuedResult = CouponClaimResult.Issued(
            issueId = 10,
            couponId = coupon.id,
            memberId = member.id,
            issuedAt = LocalDateTime.now(),
        )

        given(couponClaimEligibilityChecker.check(coupon.id, member.id)).willReturn(
            CouponClaimEligibilityResult.Eligible(coupon, member),
        )
        given(couponClaimRedisGate.tryClaim(coupon.id, member.id)).willReturn(CouponClaimGateResult.PASSED)
        given(couponClaimCompensationHandler.finalizeClaim(coupon.id, member.id)).willReturn(issuedResult)

        val result = service.claimCoupon(coupon.id, request)

        assertTrue(result is CouponClaimResult.Issued)
        result as CouponClaimResult.Issued
        assertEquals(issuedResult.issueId, result.issueId)
        assertEquals(coupon.id, result.couponId)
        assertEquals(member.id, result.memberId)
        verify(couponClaimCompensationHandler).finalizeClaim(coupon.id, member.id)
    }

    @Test
    fun `returns already claimed when redis gate rejects duplicate member`() {
        val coupon = activeCoupon()
        val member = member()
        val service = CouponClaimService(couponClaimEligibilityChecker, couponClaimRedisGate, couponClaimCompensationHandler)

        given(couponClaimEligibilityChecker.check(coupon.id, member.id)).willReturn(
            CouponClaimEligibilityResult.Eligible(coupon, member),
        )
        given(couponClaimRedisGate.tryClaim(coupon.id, member.id)).willReturn(CouponClaimGateResult.ALREADY_CLAIMED)

        val result = service.claimCoupon(coupon.id, IssueCouponRequest(member.id))

        assertTrue(result is CouponClaimResult.AlreadyClaimed)
        verify(couponClaimCompensationHandler, never()).finalizeClaim(coupon.id, member.id)
    }

    @Test
    fun `returns sold out when redis gate rejects stock`() {
        val coupon = activeCoupon()
        val member = member()
        val service = CouponClaimService(couponClaimEligibilityChecker, couponClaimRedisGate, couponClaimCompensationHandler)

        given(couponClaimEligibilityChecker.check(coupon.id, member.id)).willReturn(
            CouponClaimEligibilityResult.Eligible(coupon, member),
        )
        given(couponClaimRedisGate.tryClaim(coupon.id, member.id)).willReturn(CouponClaimGateResult.SOLD_OUT)

        val result = service.claimCoupon(coupon.id, IssueCouponRequest(member.id))

        assertTrue(result is CouponClaimResult.SoldOut)
        verify(couponClaimCompensationHandler, never()).finalizeClaim(coupon.id, member.id)
    }

    @Test
    fun `returns ineligible result immediately when eligibility checker fails`() {
        val service = CouponClaimService(couponClaimEligibilityChecker, couponClaimRedisGate, couponClaimCompensationHandler)

        given(couponClaimEligibilityChecker.check(1L, 1L)).willReturn(
            CouponClaimEligibilityResult.Ineligible(CouponClaimResult.CouponNotFound),
        )

        val result = service.claimCoupon(1L, IssueCouponRequest(1L))

        assertTrue(result is CouponClaimResult.CouponNotFound)
        verify(couponClaimRedisGate, never()).tryClaim(1L, 1L)
        verify(couponClaimCompensationHandler, never()).finalizeClaim(1L, 1L)
    }

    @Test
    fun `delegates passed gate to compensation handler`() {
        val coupon = activeCoupon()
        val member = member()
        val service = CouponClaimService(couponClaimEligibilityChecker, couponClaimRedisGate, couponClaimCompensationHandler)
        val issuedResult = CouponClaimResult.Issued(
            issueId = 10,
            couponId = coupon.id,
            memberId = member.id,
            issuedAt = LocalDateTime.now(),
        )

        given(couponClaimEligibilityChecker.check(coupon.id, member.id)).willReturn(
            CouponClaimEligibilityResult.Eligible(coupon, member),
        )
        given(couponClaimRedisGate.tryClaim(coupon.id, member.id)).willReturn(CouponClaimGateResult.PASSED)
        given(couponClaimCompensationHandler.finalizeClaim(coupon.id, member.id)).willReturn(issuedResult)

        val result = service.claimCoupon(coupon.id, IssueCouponRequest(member.id))

        assertEquals(issuedResult, result)
        verify(couponClaimCompensationHandler).finalizeClaim(coupon.id, member.id)
    }

    private fun activeCoupon(): Coupon = Coupon(
        id = 1,
        name = "선착순 쿠폰",
        totalQuantity = 100,
        issuedQuantity = 0,
        issueStartAt = LocalDateTime.now().minusHours(1),
        issueEndAt = LocalDateTime.now().plusHours(1),
    )

    private fun member(): Member = Member(
        id = 1,
        email = "member@test.com",
        name = "tester",
    )
}
