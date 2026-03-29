package com.firstcomecoupon.coupon.infrastructure.reconciliation

import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class CouponStockReconciliationScheduler(
    private val couponStockReconciliationService: CouponStockReconciliationService,
) {

    @EventListener(ApplicationReadyEvent::class)
    fun reconcileOnStartup() {
        couponStockReconciliationService.reconcileActiveCouponStocks()
    }

    @Scheduled(fixedDelayString = "\${coupon.reconciliation.fixed-delay:60000}")
    fun reconcilePeriodically() {
        couponStockReconciliationService.reconcileActiveCouponStocks()
    }
}
