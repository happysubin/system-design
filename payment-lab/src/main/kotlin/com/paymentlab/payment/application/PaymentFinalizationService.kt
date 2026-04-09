package com.paymentlab.payment.application

import com.paymentlab.inventory.application.InventoryHoldApplicationService
import com.paymentlab.payment.domain.PaymentAttempt
import com.paymentlab.payment.domain.PaymentStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PaymentFinalizationService(
    private val inventoryHoldApplicationService: InventoryHoldApplicationService,
) {
    @Transactional
    fun finalizeInventoryHold(paymentAttempt: PaymentAttempt, paymentStatus: PaymentStatus) {
        when (paymentStatus) {
            PaymentStatus.DONE -> {
                confirmLinkedHold(paymentAttempt)
                return
            }
            PaymentStatus.FAILED, PaymentStatus.CANCELLED -> {
                releaseLinkedHold(paymentAttempt)
                return
            }
            else -> return
        }
    }

    private fun confirmLinkedHold(paymentAttempt: PaymentAttempt) {
        val inventoryHoldId = paymentAttempt.inventoryHoldId
            ?: throw IllegalStateException("inventory hold is not linked for paymentAttemptId: ${paymentAttempt.id}")

        inventoryHoldApplicationService.confirmHeldHold(
            orderId = paymentAttempt.orderId,
            inventoryHoldId = inventoryHoldId,
        )
    }

    private fun releaseLinkedHold(paymentAttempt: PaymentAttempt) {
        val inventoryHoldId = paymentAttempt.inventoryHoldId
            ?: throw IllegalStateException("inventory hold is not linked for paymentAttemptId: ${paymentAttempt.id}")

        inventoryHoldApplicationService.releaseHeldHold(
            orderId = paymentAttempt.orderId,
            inventoryHoldId = inventoryHoldId,
        )
    }
}
