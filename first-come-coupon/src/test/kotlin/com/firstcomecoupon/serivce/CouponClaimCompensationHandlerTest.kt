package com.firstcomecoupon.serivce

import com.firstcomecoupon.domain.Coupon
import com.firstcomecoupon.domain.CouponIssue
import com.firstcomecoupon.domain.Member
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.dao.DataIntegrityViolationException
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExtendWith(MockitoExtension::class)
class CouponClaimCompensationHandlerTest {

    @Mock
    lateinit var couponClaimFinalizer: CouponClaimFinalizer

    @Mock
    lateinit var couponClaimRedisGate: CouponClaimRedisGate

    @Test
    fun `returns issued result when finalizer succeeds`() {
        val coupon = activeCoupon()
        val member = member()
        val issue = CouponIssue(id = 10L, coupon = coupon, member = member)
        val handler = CouponClaimCompensationHandler(couponClaimFinalizer, couponClaimRedisGate)

        given(couponClaimFinalizer.finalizeClaim(coupon.id, member.id)).willReturn(issue)

        val result = handler.finalizeClaim(coupon.id, member.id)

        assertTrue(result is CouponClaimResult.Issued)
        result as CouponClaimResult.Issued
        assertEquals(issue.id, result.issueId)
        verify(couponClaimRedisGate, never()).rollback(coupon.id, member.id)
    }

    @Test
    fun `rolls back redis and returns already claimed on duplicate sql`() {
        val handler = CouponClaimCompensationHandler(couponClaimFinalizer, couponClaimRedisGate)

        given(couponClaimFinalizer.finalizeClaim(1L, 1L)).willThrow(DataIntegrityViolationException("duplicate"))

        val result = handler.finalizeClaim(1L, 1L)

        assertTrue(result is CouponClaimResult.AlreadyClaimed)
        verify(couponClaimRedisGate).rollback(1L, 1L)
    }

    @Test
    fun `rolls back redis and rethrows unexpected runtime exception`() {
        val handler = CouponClaimCompensationHandler(couponClaimFinalizer, couponClaimRedisGate)

        given(couponClaimFinalizer.finalizeClaim(1L, 1L)).willThrow(IllegalStateException("boom"))

        assertThrows<IllegalStateException> {
            handler.finalizeClaim(1L, 1L)
        }

        verify(couponClaimRedisGate).rollback(1L, 1L)
    }

    private fun activeCoupon(): Coupon = Coupon(
        id = 1L,
        name = "선착순 쿠폰",
        totalQuantity = 100,
        issuedQuantity = 0,
        issueStartAt = LocalDateTime.now().minusHours(1),
        issueEndAt = LocalDateTime.now().plusHours(1),
    )

    private fun member(): Member = Member(
        id = 1L,
        email = "member@test.com",
        name = "tester",
    )
}
