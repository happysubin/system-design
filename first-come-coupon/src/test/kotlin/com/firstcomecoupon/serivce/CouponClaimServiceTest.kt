package com.firstcomecoupon.serivce

import com.firstcomecoupon.controller.dto.IssueCouponRequest
import com.firstcomecoupon.domain.Coupon
import com.firstcomecoupon.domain.CouponIssue
import com.firstcomecoupon.domain.Member
import com.firstcomecoupon.repository.CouponRepository
import com.firstcomecoupon.repository.MemberRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.dao.DataIntegrityViolationException
import java.time.LocalDateTime
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExtendWith(MockitoExtension::class)
class CouponClaimServiceTest {

    @Mock
    lateinit var couponRepository: CouponRepository

    @Mock
    lateinit var memberRepository: MemberRepository

    @Mock
    lateinit var couponClaimRedisGate: CouponClaimRedisGate

    @Mock
    lateinit var couponClaimFinalizer: CouponClaimFinalizer

    @Test
    fun `issues coupon when redis gate passes and finalizer succeeds`() {
        val coupon = activeCoupon()
        val member = member()
        val request = IssueCouponRequest(memberId = member.id)
        val savedIssue = CouponIssue(id = 10, coupon = coupon, member = member)
        val service = CouponClaimService(couponRepository, memberRepository, couponClaimRedisGate, couponClaimFinalizer)

        given(couponRepository.findById(coupon.id)).willReturn(Optional.of(coupon))
        given(memberRepository.findById(member.id)).willReturn(Optional.of(member))
        given(couponClaimRedisGate.tryClaim(coupon.id, member.id)).willReturn(CouponClaimGateResult.PASSED)
        given(couponClaimFinalizer.finalizeClaim(coupon.id, member.id)).willReturn(savedIssue)

        val result = service.claimCoupon(coupon.id, request)

        assertTrue(result is CouponClaimResult.Issued)
        result as CouponClaimResult.Issued
        assertEquals(savedIssue.id, result.issueId)
        assertEquals(coupon.id, result.couponId)
        assertEquals(member.id, result.memberId)
        verify(couponClaimFinalizer).finalizeClaim(coupon.id, member.id)
        verify(couponClaimRedisGate, never()).rollback(coupon.id, member.id)
    }

    @Test
    fun `returns already claimed when redis gate rejects duplicate member`() {
        val coupon = activeCoupon()
        val member = member()
        val service = CouponClaimService(couponRepository, memberRepository, couponClaimRedisGate, couponClaimFinalizer)

        given(couponRepository.findById(coupon.id)).willReturn(Optional.of(coupon))
        given(memberRepository.findById(member.id)).willReturn(Optional.of(member))
        given(couponClaimRedisGate.tryClaim(coupon.id, member.id)).willReturn(CouponClaimGateResult.ALREADY_CLAIMED)

        val result = service.claimCoupon(coupon.id, IssueCouponRequest(member.id))

        assertTrue(result is CouponClaimResult.AlreadyClaimed)
        verify(couponClaimFinalizer, never()).finalizeClaim(coupon.id, member.id)
    }

    @Test
    fun `returns sold out when redis gate rejects stock`() {
        val coupon = activeCoupon()
        val member = member()
        val service = CouponClaimService(couponRepository, memberRepository, couponClaimRedisGate, couponClaimFinalizer)

        given(couponRepository.findById(coupon.id)).willReturn(Optional.of(coupon))
        given(memberRepository.findById(member.id)).willReturn(Optional.of(member))
        given(couponClaimRedisGate.tryClaim(coupon.id, member.id)).willReturn(CouponClaimGateResult.SOLD_OUT)

        val result = service.claimCoupon(coupon.id, IssueCouponRequest(member.id))

        assertTrue(result is CouponClaimResult.SoldOut)
        verify(couponClaimFinalizer, never()).finalizeClaim(coupon.id, member.id)
    }

    @Test
    fun `compensates redis when sql finalization detects duplicate issuance`() {
        val coupon = activeCoupon()
        val member = member()
        val service = CouponClaimService(couponRepository, memberRepository, couponClaimRedisGate, couponClaimFinalizer)

        given(couponRepository.findById(coupon.id)).willReturn(Optional.of(coupon))
        given(memberRepository.findById(member.id)).willReturn(Optional.of(member))
        given(couponClaimRedisGate.tryClaim(coupon.id, member.id)).willReturn(CouponClaimGateResult.PASSED)
        given(couponClaimFinalizer.finalizeClaim(coupon.id, member.id)).willThrow(DataIntegrityViolationException("duplicate"))

        val result = service.claimCoupon(coupon.id, IssueCouponRequest(member.id))

        assertTrue(result is CouponClaimResult.AlreadyClaimed)
        verify(couponClaimRedisGate).rollback(coupon.id, member.id)
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
