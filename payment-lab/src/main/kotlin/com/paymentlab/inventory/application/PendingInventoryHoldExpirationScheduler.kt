package com.paymentlab.inventory.application

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class PendingInventoryHoldExpirationScheduler(
    private val inventoryHoldApplicationService: InventoryHoldApplicationService,
) {
    @Scheduled(fixedDelay = 300000)
    fun expirePendingInventoryHolds() {
        inventoryHoldApplicationService.expireStaleHolds()
    }
}
