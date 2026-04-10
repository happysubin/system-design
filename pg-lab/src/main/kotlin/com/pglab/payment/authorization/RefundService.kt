package com.pglab.payment.authorization

import com.pglab.payment.allocation.PaymentAllocation
import com.pglab.payment.allocation.PaymentAllocationStatus
import com.pglab.payment.ledger.LedgerEntry
import com.pglab.payment.ledger.LedgerEntryType
import com.pglab.payment.order.PaymentOrder
import com.pglab.payment.order.PaymentOrderStatus
import com.pglab.payment.shared.Money

class RefundService {
    fun refund(command: RefundCommand): RefundResult {
        refundAuthorization(command.authorization, command.refundAmount)

        val ledgerEntry = LedgerEntry(
            paymentOrder = command.order,
            paymentAllocation = command.allocation,
            authorization = command.authorization,
            type = LedgerEntryType.REFUNDED,
            amount = command.refundAmount,
            referenceTransactionId = command.authorization.pgTransactionId,
            description = "refund",
        )

        val allocationRemaining = command.allAuthorizations.sumOf { it.remainingRefundableAmount.amount }
        if (allocationRemaining == 0L) {
            command.allocation.status = PaymentAllocationStatus.CANCELED
            command.order.status = PaymentOrderStatus.CANCELED
        } else {
            command.allocation.status = PaymentAllocationStatus.PARTIALLY_CANCELED
            command.order.status = PaymentOrderStatus.PARTIALLY_CANCELED
        }

        return RefundResult(
            order = command.order,
            allocation = command.allocation,
            authorization = command.authorization,
            ledgerEntry = ledgerEntry,
        )
    }

    private fun refundAuthorization(authorization: Authorization, refundAmount: Money) {
        require(refundAmount.currency == authorization.remainingRefundableAmount.currency) {
            "refund currency must match remaining refundable currency"
        }
        require(refundAmount.amount <= authorization.remainingRefundableAmount.amount) {
            "refund amount must not exceed remaining refundable amount"
        }

        authorization.remainingRefundableAmount = Money(
            authorization.remainingRefundableAmount.amount - refundAmount.amount,
            authorization.remainingRefundableAmount.currency,
        )
    }
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
