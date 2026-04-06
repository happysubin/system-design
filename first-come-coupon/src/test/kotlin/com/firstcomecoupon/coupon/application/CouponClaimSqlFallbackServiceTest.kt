package com.firstcomecoupon.coupon.application

import com.firstcomecoupon.coupon.domain.CouponIssue
import com.firstcomecoupon.coupon.domain.CouponSoldOutException
import com.firstcomecoupon.coupon.domain.Coupon
import com.firstcomecoupon.coupon.domain.Member
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExtendWith(MockitoExtension::class)
class CouponClaimSqlFallbackServiceTest {

    @Mock
    lateinit var couponClaimFinalizer: CouponClaimFinalizer

    @Test
    fun `returns sold out when finalizer reports stock exhaustion`() {
        val service = CouponClaimSqlFallbackService(couponClaimFinalizer)

        given(couponClaimFinalizer.finalizeClaim(1L, 1L)).willThrow(CouponSoldOutException())

        val result = service.claimWithoutRedis(1L, 1L)

        assertTrue(result is com.firstcomecoupon.coupon.domain.CouponClaimResult.SoldOut)
    }

    @Test
    fun `rethrows when fallback finalizer reports missing coupon stock`() {
        val service = CouponClaimSqlFallbackService(couponClaimFinalizer)

        given(couponClaimFinalizer.finalizeClaim(1L, 1L)).willThrow(NoSuchElementException("coupon stock not found"))

        val exception = assertThrows<NoSuchElementException> {
            service.claimWithoutRedis(1L, 1L)
        }

        assertEquals("coupon stock not found", exception.message)
    }

    @Test
    fun `returns issued when finalizer succeeds`() {
        val service = CouponClaimSqlFallbackService(couponClaimFinalizer)
        val issue = CouponIssue(
            id = 10L,
            coupon = Coupon(
                id = 1L,
                name = "coupon",
                totalQuantity = 10,
                issueStartAt = LocalDateTime.now().minusHours(1),
                issueEndAt = LocalDateTime.now().plusHours(1),
            ),
            member = Member(id = 1L, email = "a@test.com", name = "a"),
        )

        given(couponClaimFinalizer.finalizeClaim(1L, 1L)).willReturn(issue)

        val result = service.claimWithoutRedis(1L, 1L)

        assertTrue(result is com.firstcomecoupon.coupon.domain.CouponClaimResult.Issued)
    }
}
