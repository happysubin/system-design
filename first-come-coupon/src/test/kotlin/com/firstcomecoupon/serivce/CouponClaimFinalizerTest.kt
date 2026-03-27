package com.firstcomecoupon.serivce

import com.firstcomecoupon.domain.Coupon
import com.firstcomecoupon.domain.CouponIssue
import com.firstcomecoupon.domain.Member
import com.firstcomecoupon.repository.CouponIssueRepository
import com.firstcomecoupon.repository.CouponRepository
import com.firstcomecoupon.repository.MemberRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.junit.jupiter.MockitoExtension
import java.time.LocalDateTime
import kotlin.test.assertEquals

@ExtendWith(MockitoExtension::class)
class CouponClaimFinalizerTest {

    @Mock
    lateinit var couponIssueRepository: CouponIssueRepository

    @Mock
    lateinit var couponRepository: CouponRepository

    @Mock
    lateinit var memberRepository: MemberRepository

    @Test
    fun `finalizeClaim persists coupon issue without incrementing coupon counter`() {
        val coupon = Coupon(
            id = 1,
            name = "선착순 쿠폰",
            totalQuantity = 100,
            issuedQuantity = 0,
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
        val finalizer = CouponClaimFinalizer(couponIssueRepository, couponRepository, memberRepository)

        given(couponRepository.getReferenceById(coupon.id)).willReturn(coupon)
        given(memberRepository.getReferenceById(member.id)).willReturn(member)
        given(couponIssueRepository.saveAndFlush(any(CouponIssue::class.java))).willReturn(savedIssue)

        val result = finalizer.finalizeClaim(coupon.id, member.id)

        assertEquals(savedIssue.id, result.id)
        verify(couponRepository).getReferenceById(coupon.id)
        verify(couponIssueRepository).saveAndFlush(any(CouponIssue::class.java))
        verifyNoMoreInteractions(couponRepository)
    }
}
