package com.firstcomecoupon.coupon.infrastructure.reconciliation

import com.firstcomecoupon.coupon.domain.Coupon
import com.firstcomecoupon.coupon.infrastructure.persistence.CouponIssueRepository
import com.firstcomecoupon.coupon.infrastructure.persistence.CouponRepository
import com.firstcomecoupon.coupon.infrastructure.reconciliation.CouponStockReconciliationService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class CouponStockReconciliationServiceTest {

    @Mock
    lateinit var couponRepository: CouponRepository

    @Mock
    lateinit var couponIssueRepository: CouponIssueRepository

    @Mock
    lateinit var stringRedisTemplate: StringRedisTemplate

    @Mock
    lateinit var valueOperations: ValueOperations<String, String>

    @Test
    fun `reconciles active coupon stocks from coupon issues`() {
        val now = LocalDateTime.of(2026, 3, 28, 12, 0)
        val coupon1 = coupon(id = 1L, totalQuantity = 10)
        val coupon2 = coupon(id = 2L, totalQuantity = 5)
        val service = CouponStockReconciliationService(couponRepository, couponIssueRepository, stringRedisTemplate)

        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations)
        given(couponRepository.findActiveCoupons(now)).willReturn(listOf(coupon1, coupon2))
        given(couponIssueRepository.countByCouponId(coupon1.id)).willReturn(3L)
        given(couponIssueRepository.countByCouponId(coupon2.id)).willReturn(1L)

        service.reconcileActiveCouponStocks(now)

        verify(valueOperations).set("coupon:stock:${coupon1.id}", "7")
        verify(valueOperations).set("coupon:stock:${coupon2.id}", "4")
    }

    @Test
    fun `reconciles single coupon stock from coupon issues`() {
        val coupon = coupon(id = 3L, totalQuantity = 20)
        val service = CouponStockReconciliationService(couponRepository, couponIssueRepository, stringRedisTemplate)

        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations)
        given(couponRepository.findById(coupon.id)).willReturn(java.util.Optional.of(coupon))
        given(couponIssueRepository.countByCouponId(coupon.id)).willReturn(8L)

        service.reconcileCouponStock(coupon.id)

        verify(valueOperations).set("coupon:stock:${coupon.id}", "12")
    }

    private fun coupon(id: Long, totalQuantity: Int): Coupon = Coupon(
        id = id,
        name = "선착순 쿠폰",
        totalQuantity = totalQuantity,
        issueStartAt = LocalDateTime.now().minusHours(1),
        issueEndAt = LocalDateTime.now().plusHours(1),
    )
}
