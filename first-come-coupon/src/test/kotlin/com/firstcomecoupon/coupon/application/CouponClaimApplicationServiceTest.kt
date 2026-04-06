package com.firstcomecoupon.coupon.application

import com.firstcomecoupon.coupon.api.dto.IssueCouponRequest
import com.firstcomecoupon.coupon.domain.Coupon
import com.firstcomecoupon.coupon.domain.CouponClaimResult
import com.firstcomecoupon.coupon.domain.Member
import com.firstcomecoupon.coupon.infrastructure.redis.CouponClaimGateResult
import com.firstcomecoupon.coupon.infrastructure.redis.RedisClaimGateGuard
import com.firstcomecoupon.coupon.infrastructure.redis.RedisGateUnavailableException
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
class CouponClaimApplicationServiceTest {

    @Mock
    lateinit var couponClaimEligibilityChecker: CouponClaimEligibilityChecker

    @Mock
    lateinit var redisClaimGateGuard: RedisClaimGateGuard

    @Mock
    lateinit var couponClaimCompensationHandler: CouponClaimCompensationHandler

    @Test
    fun `issues coupon when redis gate passes and compensation handler succeeds`() {
        val coupon = activeCoupon()
        val member = member()
        val request = IssueCouponRequest(memberId = member.id)
        val service = CouponClaimApplicationService(couponClaimEligibilityChecker, redisClaimGateGuard, couponClaimCompensationHandler)
        val issuedResult = CouponClaimResult.Issued(
            issueId = 10,
            couponId = coupon.id,
            memberId = member.id,
            issuedAt = LocalDateTime.now(),
        )

        given(couponClaimEligibilityChecker.check(coupon.id, member.id)).willReturn(
            CouponClaimEligibilityResult.Eligible(coupon, member),
        )
        given(redisClaimGateGuard.tryClaim(coupon.id, member.id)).willReturn(CouponClaimGateResult.PASSED)
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
        val service = CouponClaimApplicationService(couponClaimEligibilityChecker, redisClaimGateGuard, couponClaimCompensationHandler)

        given(couponClaimEligibilityChecker.check(coupon.id, member.id)).willReturn(
            CouponClaimEligibilityResult.Eligible(coupon, member),
        )
        given(redisClaimGateGuard.tryClaim(coupon.id, member.id)).willReturn(CouponClaimGateResult.ALREADY_CLAIMED)

        val result = service.claimCoupon(coupon.id, IssueCouponRequest(member.id))

        assertTrue(result is CouponClaimResult.AlreadyClaimed)
        verify(couponClaimCompensationHandler, never()).finalizeClaim(coupon.id, member.id)
    }

    @Test
    fun `returns sold out when redis gate rejects stock`() {
        val coupon = activeCoupon()
        val member = member()
        val service = CouponClaimApplicationService(couponClaimEligibilityChecker, redisClaimGateGuard, couponClaimCompensationHandler)

        given(couponClaimEligibilityChecker.check(coupon.id, member.id)).willReturn(
            CouponClaimEligibilityResult.Eligible(coupon, member),
        )
        given(redisClaimGateGuard.tryClaim(coupon.id, member.id)).willReturn(CouponClaimGateResult.SOLD_OUT)

        val result = service.claimCoupon(coupon.id, IssueCouponRequest(member.id))

        assertTrue(result is CouponClaimResult.SoldOut)
        verify(couponClaimCompensationHandler, never()).finalizeClaim(coupon.id, member.id)
    }

    @Test
    fun `returns ineligible result immediately when eligibility checker fails`() {
        val service = CouponClaimApplicationService(couponClaimEligibilityChecker, redisClaimGateGuard, couponClaimCompensationHandler)

        given(couponClaimEligibilityChecker.check(1L, 1L)).willReturn(
            CouponClaimEligibilityResult.Ineligible(CouponClaimResult.CouponNotFound),
        )

        val result = service.claimCoupon(1L, IssueCouponRequest(1L))

        assertTrue(result is CouponClaimResult.CouponNotFound)
        verify(redisClaimGateGuard, never()).tryClaim(1L, 1L)
        verify(couponClaimCompensationHandler, never()).finalizeClaim(1L, 1L)
    }

    @Test
    fun `delegates passed gate to compensation handler`() {
        val coupon = activeCoupon()
        val member = member()
        val service = CouponClaimApplicationService(couponClaimEligibilityChecker, redisClaimGateGuard, couponClaimCompensationHandler)
        val issuedResult = CouponClaimResult.Issued(
            issueId = 10,
            couponId = coupon.id,
            memberId = member.id,
            issuedAt = LocalDateTime.now(),
        )

        given(couponClaimEligibilityChecker.check(coupon.id, member.id)).willReturn(
            CouponClaimEligibilityResult.Eligible(coupon, member),
        )
        given(redisClaimGateGuard.tryClaim(coupon.id, member.id)).willReturn(CouponClaimGateResult.PASSED)
        given(couponClaimCompensationHandler.finalizeClaim(coupon.id, member.id)).willReturn(issuedResult)

        val result = service.claimCoupon(coupon.id, IssueCouponRequest(member.id))

        assertEquals(issuedResult, result)
        verify(couponClaimCompensationHandler).finalizeClaim(coupon.id, member.id)
    }

    @Test
    fun `returns internal failure when redis gate guard reports redis unavailable`() {
        val coupon = activeCoupon()
        val member = member()
        val service = CouponClaimApplicationService(couponClaimEligibilityChecker, redisClaimGateGuard, couponClaimCompensationHandler)

        given(couponClaimEligibilityChecker.check(coupon.id, member.id)).willReturn(
            CouponClaimEligibilityResult.Eligible(coupon, member),
        )
        given(redisClaimGateGuard.tryClaim(coupon.id, member.id)).willThrow(RedisGateUnavailableException())

        val result = service.claimCoupon(coupon.id, IssueCouponRequest(member.id))

        assertTrue(result is CouponClaimResult.InternalFailure)
        result as CouponClaimResult.InternalFailure
        assertEquals("redis service unavailable", result.message)
        verify(couponClaimCompensationHandler, never()).finalizeClaim(coupon.id, member.id)
    }

    private fun activeCoupon(): Coupon = Coupon(
        id = 1,
        name = "선착순 쿠폰",
        totalQuantity = 100,
        issueStartAt = LocalDateTime.now().minusHours(1),
        issueEndAt = LocalDateTime.now().plusHours(1),
    )

    private fun member(): Member = Member(
        id = 1,
        email = "member@test.com",
        name = "tester",
    )
}
