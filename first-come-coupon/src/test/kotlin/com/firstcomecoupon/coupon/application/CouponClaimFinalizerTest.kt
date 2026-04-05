package com.firstcomecoupon.coupon.application

import com.firstcomecoupon.coupon.application.CouponClaimFinalizer
import com.firstcomecoupon.coupon.domain.Coupon
import com.firstcomecoupon.coupon.domain.CouponIssue
import com.firstcomecoupon.coupon.domain.CouponSoldOutException
import com.firstcomecoupon.coupon.domain.Member
import com.firstcomecoupon.coupon.infrastructure.persistence.CouponIssueRepository
import com.firstcomecoupon.coupon.infrastructure.persistence.CouponRepository
import com.firstcomecoupon.coupon.infrastructure.persistence.CouponStockRepository
import com.firstcomecoupon.coupon.infrastructure.persistence.MemberRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.junit.jupiter.MockitoExtension
import java.time.LocalDateTime
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals

@ExtendWith(MockitoExtension::class)
class CouponClaimFinalizerTest {

    @Mock
    lateinit var couponIssueRepository: CouponIssueRepository

    @Mock
    lateinit var couponRepository: CouponRepository

    @Mock
    lateinit var memberRepository: MemberRepository

    @Mock
    lateinit var couponStockRepository: CouponStockRepository

    @Test
    fun `finalizeClaim decrements coupon stock and persists coupon issue`() {
        val coupon = Coupon(
            id = 1,
            name = "선착순 쿠폰",
            totalQuantity = 100,
            issueStartAt = LocalDateTime.now().minusHours(1),
            issueEndAt = LocalDateTime.now().plusHours(1),
        )
        val member = Member(
            id = 1,
            email = "member@test.com",
            name = "tester",
        )
        val savedIssue = CouponIssue(
            id = 5,
            coupon = coupon,
            member = member,
        )
        val finalizer = CouponClaimFinalizer(couponIssueRepository, couponRepository, memberRepository, couponStockRepository)

        given(couponStockRepository.decrementIfAvailable(coupon.id)).willReturn(1)
        given(couponRepository.findById(coupon.id)).willReturn(java.util.Optional.of(coupon))
        given(memberRepository.getReferenceById(member.id)).willReturn(member)
        given(couponIssueRepository.saveAndFlush(any(CouponIssue::class.java))).willReturn(savedIssue)

        val result = finalizer.finalizeClaim(coupon.id, member.id)

        assertEquals(savedIssue.id, result.id)
        verify(couponStockRepository).decrementIfAvailable(coupon.id)
        verify(couponIssueRepository).saveAndFlush(any(CouponIssue::class.java))
        verifyNoMoreInteractions(couponRepository)
    }

    @Test
    fun `finalizeClaim rejects claim when coupon stock is exhausted`() {
        val coupon = Coupon(
            id = 1,
            name = "선착순 쿠폰",
            totalQuantity = 1,
            issueStartAt = LocalDateTime.now().minusHours(1),
            issueEndAt = LocalDateTime.now().plusHours(1),
        )
        val member = Member(
            id = 2,
            email = "member2@test.com",
            name = "tester2",
        )
        val finalizer = CouponClaimFinalizer(couponIssueRepository, couponRepository, memberRepository, couponStockRepository)

        given(couponStockRepository.decrementIfAvailable(coupon.id)).willReturn(0)
        given(couponStockRepository.findByCouponId(coupon.id)).willReturn(
            com.firstcomecoupon.coupon.domain.CouponStock(couponId = coupon.id, remainingQuantity = 0),
        )

        assertFailsWith<CouponSoldOutException> {
            finalizer.finalizeClaim(coupon.id, member.id)
        }

        verify(couponStockRepository).decrementIfAvailable(coupon.id)
        verify(couponIssueRepository, never()).saveAndFlush(any(CouponIssue::class.java))
    }

    @Test
    fun `finalizeClaim throws when coupon exists but stock row is missing`() {
        val coupon = Coupon(
            id = 1,
            name = "선착순 쿠폰",
            totalQuantity = 1,
            issueStartAt = LocalDateTime.now().minusHours(1),
            issueEndAt = LocalDateTime.now().plusHours(1),
        )
        val member = Member(
            id = 2,
            email = "member2@test.com",
            name = "tester2",
        )
        val finalizer = CouponClaimFinalizer(couponIssueRepository, couponRepository, memberRepository, couponStockRepository)

        given(couponStockRepository.decrementIfAvailable(coupon.id)).willReturn(0)
        given(couponStockRepository.findByCouponId(coupon.id)).willReturn(null)

        assertFailsWith<NoSuchElementException> {
            finalizer.finalizeClaim(coupon.id, member.id)
        }

        verify(couponStockRepository).decrementIfAvailable(coupon.id)
        verify(couponStockRepository).findByCouponId(coupon.id)
        verify(couponIssueRepository, never()).saveAndFlush(any(CouponIssue::class.java))
    }
}
