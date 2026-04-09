package com.paymentlab.inventory.application

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class PendingInventoryHoldExpirationScheduler(
    private val inventoryHoldApplicationService: InventoryHoldApplicationService,
) {
    @Scheduled(fixedDelay = 300000)
    fun expirePendingInventoryHolds() {
        val now = LocalDateTime.now()
        inventoryHoldApplicationService.findExpiredHeldHoldIds(now)
            .forEach { holdId ->
                runCatching {
                    inventoryHoldApplicationService.expireHeldHold(holdId, now)
                }
            }
    }
}
