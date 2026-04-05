package com.firstcomecoupon.coupon.application

import com.firstcomecoupon.coupon.application.CouponClaimCompensationHandler
import com.firstcomecoupon.coupon.application.CouponClaimFinalizer
import com.firstcomecoupon.coupon.domain.Coupon
import com.firstcomecoupon.coupon.domain.CouponClaimResult
import com.firstcomecoupon.coupon.domain.CouponIssue
import com.firstcomecoupon.coupon.domain.CouponStock
import com.firstcomecoupon.coupon.domain.CouponSoldOutException
import com.firstcomecoupon.coupon.domain.Member
import com.firstcomecoupon.coupon.infrastructure.persistence.CouponStockRepository
import com.firstcomecoupon.coupon.infrastructure.redis.CouponClaimRedisGate
import com.firstcomecoupon.coupon.infrastructure.reconciliation.CouponStockReconciliationService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.hibernate.exception.ConstraintViolationException
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.dao.DataIntegrityViolationException
import java.sql.SQLException
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExtendWith(MockitoExtension::class)
class CouponClaimCompensationHandlerTest {

    @Mock
    lateinit var couponClaimFinalizer: CouponClaimFinalizer

    @Mock
    lateinit var couponClaimRedisGate: CouponClaimRedisGate

    @Mock
    lateinit var couponStockReconciliationService: CouponStockReconciliationService

    @Mock
    lateinit var couponStockRepository: CouponStockRepository

    @Test
    fun `returns issued result when finalizer succeeds`() {
        val coupon = activeCoupon()
        val member = member()
        val issue = CouponIssue(id = 10L, coupon = coupon, member = member)
        val handler = CouponClaimCompensationHandler(couponClaimFinalizer, couponClaimRedisGate, couponStockReconciliationService, couponStockRepository)

        given(couponClaimFinalizer.finalizeClaim(coupon.id, member.id)).willReturn(issue)

        val result = handler.finalizeClaim(coupon.id, member.id)

        assertTrue(result is CouponClaimResult.Issued)
        result as CouponClaimResult.Issued
        assertEquals(issue.id, result.issueId)
        verify(couponClaimRedisGate, never()).rollback(coupon.id, member.id)
        verify(couponStockReconciliationService, never()).reconcileCouponStock(coupon.id)
        verify(couponStockRepository, never()).saveAndFlush(any(CouponStock::class.java))
    }

    @Test
    fun `rolls back redis and returns already claimed on duplicate sql`() {
        val handler = CouponClaimCompensationHandler(couponClaimFinalizer, couponClaimRedisGate, couponStockReconciliationService, couponStockRepository)

        given(couponClaimFinalizer.finalizeClaim(1L, 1L)).willThrow(
            DataIntegrityViolationException(
                "duplicate",
                ConstraintViolationException("duplicate", SQLException("duplicate"), "uk_coupon_issue_coupon_member"),
            ),
        )
        given(couponStockRepository.findByCouponId(1L)).willReturn(CouponStock(couponId = 1L, remainingQuantity = 99))

        val result = handler.finalizeClaim(1L, 1L)

        assertTrue(result is CouponClaimResult.AlreadyClaimed)
        verify(couponClaimRedisGate).rollback(1L, 1L)
        verify(couponStockReconciliationService, never()).reconcileCouponStock(1L)
        verify(couponStockRepository).findByCouponId(1L)
        verify(couponStockRepository).saveAndFlush(any(CouponStock::class.java))
    }

    @Test
    fun `rolls back redis and rethrows non duplicate integrity failures`() {
        val handler = CouponClaimCompensationHandler(couponClaimFinalizer, couponClaimRedisGate, couponStockReconciliationService, couponStockRepository)

        given(couponClaimFinalizer.finalizeClaim(1L, 1L)).willThrow(
            DataIntegrityViolationException(
                "unexpected integrity failure",
                ConstraintViolationException("fk failure", SQLException("fk failure"), "fk_coupon_issue_coupon"),
            ),
        )

        assertThrows<DataIntegrityViolationException> {
            handler.finalizeClaim(1L, 1L)
        }

        verify(couponClaimRedisGate).rollback(1L, 1L)
        verify(couponStockReconciliationService).reconcileCouponStock(1L)
        verify(couponStockRepository, never()).saveAndFlush(any(CouponStock::class.java))
    }

    @Test
    fun `rolls back redis and returns sold out when sql capacity is exhausted`() {
        val handler = CouponClaimCompensationHandler(couponClaimFinalizer, couponClaimRedisGate, couponStockReconciliationService, couponStockRepository)

        given(couponClaimFinalizer.finalizeClaim(1L, 1L)).willThrow(CouponSoldOutException())

        val result = handler.finalizeClaim(1L, 1L)

        assertTrue(result is CouponClaimResult.SoldOut)
        verify(couponClaimRedisGate).rollback(1L, 1L)
        verify(couponStockReconciliationService).reconcileCouponStock(1L)
        verify(couponStockRepository, never()).saveAndFlush(any(CouponStock::class.java))
    }

    @Test
    fun `rolls back redis and rethrows unexpected runtime exception`() {
        val handler = CouponClaimCompensationHandler(couponClaimFinalizer, couponClaimRedisGate, couponStockReconciliationService, couponStockRepository)

        given(couponClaimFinalizer.finalizeClaim(1L, 1L)).willThrow(IllegalStateException("boom"))

        assertThrows<IllegalStateException> {
            handler.finalizeClaim(1L, 1L)
        }

        verify(couponClaimRedisGate).rollback(1L, 1L)
        verify(couponStockReconciliationService).reconcileCouponStock(1L)
        verify(couponStockRepository, never()).saveAndFlush(any(CouponStock::class.java))
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
