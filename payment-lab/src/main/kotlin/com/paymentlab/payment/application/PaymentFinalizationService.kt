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
                // 결제 성공일 때만 판매를 확정하므로 linked hold를 confirm한다.
                confirmLinkedHold(paymentAttempt)
                return
            }
            PaymentStatus.FAILED, PaymentStatus.CANCELLED -> {
                // 실패/취소는 돈이 들어오지 않은 케이스이므로 hold를 release해서 reserved만 되돌린다.
                releaseLinkedHold(paymentAttempt)
                return
            }
            else -> return
        }
    }

    private fun confirmLinkedHold(paymentAttempt: PaymentAttempt) {
        val inventoryHoldId = paymentAttempt.inventoryHoldId
            ?: throw IllegalStateException("inventory hold is not linked for paymentAttemptId: ${paymentAttempt.id}")

        // exact hold id를 타게 하는 이유는,
        // 같은 주문에 과거 hold나 다른 상태의 hold가 있어도 현재 결제 시도와 연결된 hold만 확정해야 하기 때문이다.
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
