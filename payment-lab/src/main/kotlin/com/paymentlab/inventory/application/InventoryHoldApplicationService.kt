package com.paymentlab.inventory.application

import com.paymentlab.inventory.domain.InventoryHold
import com.paymentlab.inventory.domain.InventoryHoldStatus
import com.paymentlab.inventory.infrastructure.persistence.InventoryHoldRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class InventoryHoldApplicationService(
    private val inventoryHoldRepository: InventoryHoldRepository,
    @Value("\${inventory.hold.ttl-seconds:300}") private val ttlSeconds: Long = 300,
) {
    @Transactional
    fun reserveOrReuse(orderId: Long): InventoryHold {
        val now = LocalDateTime.now()
        val activeHold = inventoryHoldRepository.findFirstByOrderIdAndStatusAndExpiresAtAfter(
            orderId,
            InventoryHoldStatus.HELD,
            now,
        )
        if (activeHold != null) {
            return activeHold
        }

        return inventoryHoldRepository.save(
            InventoryHold(
                orderId = orderId,
                status = InventoryHoldStatus.HELD,
                expiresAt = now.plusSeconds(ttlSeconds),
                createdAt = now,
            ),
        )
    }

    @Transactional
    fun expireStaleHolds() {
        val now = LocalDateTime.now()
        inventoryHoldRepository.findAllByStatusAndExpiresAtBefore(InventoryHoldStatus.HELD, now)
            .forEach { hold ->
                runCatching {
                    inventoryHoldRepository.updateStatusIfCurrentStatusAndExpired(
                        hold.id,
                        InventoryHoldStatus.HELD,
                        InventoryHoldStatus.EXPIRED,
                        now,
                    )
                }
            }
    }
}
