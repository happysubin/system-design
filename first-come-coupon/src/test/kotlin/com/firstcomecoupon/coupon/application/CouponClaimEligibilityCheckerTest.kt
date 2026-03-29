package com.firstcomecoupon.coupon.application

import com.firstcomecoupon.coupon.application.CouponClaimEligibilityChecker
import com.firstcomecoupon.coupon.application.CouponClaimEligibilityResult
import com.firstcomecoupon.coupon.domain.Coupon
import com.firstcomecoupon.coupon.domain.CouponClaimResult
import com.firstcomecoupon.coupon.domain.Member
import com.firstcomecoupon.coupon.infrastructure.persistence.CouponRepository
import com.firstcomecoupon.coupon.infrastructure.persistence.MemberRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import java.time.LocalDateTime
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExtendWith(MockitoExtension::class)
class CouponClaimEligibilityCheckerTest {

    @Mock
    lateinit var couponRepository: CouponRepository

    @Mock
    lateinit var memberRepository: MemberRepository

    @Test
    fun `returns coupon not found when coupon does not exist`() {
        val checker = CouponClaimEligibilityChecker(couponRepository, memberRepository)

        given(couponRepository.findById(1L)).willReturn(Optional.empty())

        val result = checker.check(1L, 1L)

        assertTrue(result is CouponClaimEligibilityResult.Ineligible)
        result as CouponClaimEligibilityResult.Ineligible
        assertTrue(result.result is CouponClaimResult.CouponNotFound)
    }

    @Test
    fun `returns member not found when member does not exist`() {
        val checker = CouponClaimEligibilityChecker(couponRepository, memberRepository)
        val coupon = activeCoupon()

        given(couponRepository.findById(coupon.id)).willReturn(Optional.of(coupon))
        given(memberRepository.findById(1L)).willReturn(Optional.empty())

        val result = checker.check(coupon.id, 1L)

        assertTrue(result is CouponClaimEligibilityResult.Ineligible)
        result as CouponClaimEligibilityResult.Ineligible
        assertTrue(result.result is CouponClaimResult.MemberNotFound)
    }

    @Test
    fun `returns not in issue window when coupon is not claimable now`() {
        val checker = CouponClaimEligibilityChecker(couponRepository, memberRepository)
        val coupon = Coupon(
            id = 1L,
            name = "future coupon",
            totalQuantity = 100,
            issueStartAt = LocalDateTime.now().plusHours(1),
            issueEndAt = LocalDateTime.now().plusHours(2),
        )
        val member = member()

        given(couponRepository.findById(coupon.id)).willReturn(Optional.of(coupon))
        given(memberRepository.findById(member.id)).willReturn(Optional.of(member))

        val result = checker.check(coupon.id, member.id)

        assertTrue(result is CouponClaimEligibilityResult.Ineligible)
        result as CouponClaimEligibilityResult.Ineligible
        assertTrue(result.result is CouponClaimResult.NotInIssueWindow)
    }

    @Test
    fun `returns eligible context when coupon and member are valid`() {
        val checker = CouponClaimEligibilityChecker(couponRepository, memberRepository)
        val coupon = activeCoupon()
        val member = member()

        given(couponRepository.findById(coupon.id)).willReturn(Optional.of(coupon))
        given(memberRepository.findById(member.id)).willReturn(Optional.of(member))

        val result = checker.check(coupon.id, member.id)

        assertTrue(result is CouponClaimEligibilityResult.Eligible)
        result as CouponClaimEligibilityResult.Eligible
        assertEquals(coupon.id, result.coupon.id)
        assertEquals(member.id, result.member.id)
    }

    private fun activeCoupon(): Coupon = Coupon(
        id = 1L,
        name = "선착순 쿠폰",
        totalQuantity = 100,
        issueStartAt = LocalDateTime.now().minusHours(1),
        issueEndAt = LocalDateTime.now().plusHours(1),
    )

    private fun member(): Member = Member(
        id = 1L,
        email = "member@test.com",
        name = "tester",
    )
}
