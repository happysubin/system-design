package com.paymentlab.payment.application

import com.paymentlab.inventory.domain.InventoryHoldStatus
import com.paymentlab.inventory.infrastructure.persistence.InventoryHoldRepository
import com.paymentlab.payment.domain.PaymentAttempt
import com.paymentlab.payment.domain.PaymentStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PaymentFinalizationService(
    private val inventoryHoldRepository: InventoryHoldRepository,
) {
    @Transactional
    fun finalizeInventoryHold(paymentAttempt: PaymentAttempt, paymentStatus: PaymentStatus) {
        val nextInventoryStatus = when (paymentStatus) {
            PaymentStatus.DONE -> InventoryHoldStatus.CONFIRMED
            PaymentStatus.FAILED, PaymentStatus.CANCELLED -> InventoryHoldStatus.RELEASED
            else -> return
        }

        val inventoryHoldId = paymentAttempt.inventoryHoldId
            ?: throw IllegalStateException("inventory hold is not linked for paymentAttemptId: ${paymentAttempt.id}")

        val hold = inventoryHoldRepository.findById(inventoryHoldId)
            .orElseThrow { IllegalStateException("inventory hold not found: $inventoryHoldId") }

        if (hold.orderId != paymentAttempt.orderId) {
            throw IllegalStateException(
                "inventory hold $inventoryHoldId does not belong to orderId: ${paymentAttempt.orderId}",
            )
        }

        val updated = inventoryHoldRepository.updateStatusIfCurrentStatus(
            inventoryHoldId,
            InventoryHoldStatus.HELD,
            nextInventoryStatus,
        )
        if (updated == 0) {
            throw IllegalStateException("inventory hold is no longer held: $inventoryHoldId")
        }
    }
}
