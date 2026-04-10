package com.pglab.payment.authorization

import com.pglab.payment.allocation.PaymentAllocation
import com.pglab.payment.allocation.PaymentAllocationStatus
import com.pglab.payment.ledger.LedgerEntry
import com.pglab.payment.ledger.LedgerEntryType
import com.pglab.payment.order.PaymentOrder
import com.pglab.payment.order.PaymentOrderStatus
import com.pglab.payment.shared.Money
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RefundService(
    private val writer: RefundWriter,
) {
    @Transactional
    fun refund(command: RefundCommand): RefundResult {
        // 환불은 취소와 비슷해 보이지만 "남은 환불 가능 금액"을 기준으로 판단한다.
        // 따라서 먼저 authorization 단위 잔액을 줄여서 환불 가능 범위를 확정한다.
        refundAuthorization(command.authorization, command.refundAmount)

        // 환불 역시 기존 승인 기록을 수정하는 것이 아니라,
        // 별도의 REFUNDED 원장 사실로 남겨야 이후 정산 차감과 감사 추적이 가능하다.
        val ledgerEntry = LedgerEntry(
            paymentOrder = command.order,
            paymentAllocation = command.allocation,
            authorization = command.authorization,
            type = LedgerEntryType.REFUNDED,
            amount = command.refundAmount,
            referenceTransactionId = command.authorization.pgTransactionId,
            description = "refund",
        )

        // 상위 상태는 특정 authorization 하나가 아니라 allocation 전체 기준으로 재계산한다.
        // 모든 authorization의 환불 가능 잔액이 0이면 allocation/order 전체가 사실상 전부 환불된 상태다.
        val allocationRemaining = command.allAuthorizations.sumOf { it.remainingRefundableAmount.amount }
        if (allocationRemaining == 0L) {
            command.allocation.status = PaymentAllocationStatus.CANCELED
            command.order.status = PaymentOrderStatus.CANCELED
        } else {
            command.allocation.status = PaymentAllocationStatus.PARTIALLY_CANCELED
            command.order.status = PaymentOrderStatus.PARTIALLY_CANCELED
        }

        // 결과 객체는 환불 후 달라진 authorization과 원장, 상위 상태를 한 번에 묶어준다.
        val result = RefundResult(
            order = command.order,
            allocation = command.allocation,
            authorization = command.authorization,
            ledgerEntry = ledgerEntry,
        )

        return writer.save(result)
    }

    private fun refundAuthorization(authorization: Authorization, refundAmount: Money) {
        // 환불도 취소와 마찬가지로 통화가 다르면 같은 authorization 잔액에서 차감할 수 없다.
        require(refundAmount.currency == authorization.remainingRefundableAmount.currency) {
            "refund currency must match remaining refundable currency"
        }
        // 이미 환불한 금액을 초과해서 또 환불하면 실제 돈 이동과 로컬 잔액이 틀어지므로 차단한다.
        require(refundAmount.amount <= authorization.remainingRefundableAmount.amount) {
            "refund amount must not exceed remaining refundable amount"
        }

        // 환불 가능 잔액에서 실제 환불 금액만큼 차감하여 다음 환불 가능 범위를 갱신한다.
        authorization.remainingRefundableAmount = Money(
            authorization.remainingRefundableAmount.amount - refundAmount.amount,
            authorization.remainingRefundableAmount.currency,
        )
    }
}

interface RefundWriter {
    fun save(result: RefundResult): RefundResult
}

data class RefundCommand(
    val order: PaymentOrder,
    val allocation: PaymentAllocation,
    val authorization: Authorization,
    val refundAmount: Money,
    val allAuthorizations: List<Authorization> = listOf(authorization),
)

data class RefundResult(
    val order: PaymentOrder,
    val allocation: PaymentAllocation,
    val authorization: Authorization,
    val ledgerEntry: LedgerEntry,
)
