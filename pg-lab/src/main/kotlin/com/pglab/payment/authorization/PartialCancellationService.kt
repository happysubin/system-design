package com.pglab.payment.authorization

import com.pglab.payment.allocation.PaymentAllocation
import com.pglab.payment.allocation.PaymentAllocationStatus
import com.pglab.payment.ledger.LedgerEntry
import com.pglab.payment.ledger.LedgerEntryType
import com.pglab.payment.order.PaymentOrder
import com.pglab.payment.order.PaymentOrderStatus
import com.pglab.payment.shared.Money

class PartialCancellationService {
    fun cancel(command: PartialCancellationCommand): PartialCancellationResult {
        command.authorization.cancel(command.cancelAmount)

        val ledgerEntry = LedgerEntry(
            paymentOrder = command.order,
            paymentAllocation = command.allocation,
            authorization = command.authorization,
            type = LedgerEntryType.CANCELLED,
            amount = command.cancelAmount,
            referenceTransactionId = command.authorization.pgTransactionId,
            description = "partial cancellation",
        )

        val allocationRemaining = command.allAuthorizations.sumOf { it.remainingCancelableAmount.amount }
        if (allocationRemaining == 0L) {
            command.allocation.status = PaymentAllocationStatus.CANCELED
            command.order.status = PaymentOrderStatus.CANCELED
        } else {
            command.allocation.status = PaymentAllocationStatus.PARTIALLY_CANCELED
            command.order.status = PaymentOrderStatus.PARTIALLY_CANCELED
        }

        return PartialCancellationResult(
            order = command.order,
            allocation = command.allocation,
            authorization = command.authorization,
            ledgerEntry = ledgerEntry,
        )
    }
}

data class PartialCancellationCommand(
    val order: PaymentOrder,
    val allocation: PaymentAllocation,
    val authorization: Authorization,
    val cancelAmount: Money,
    val allAuthorizations: List<Authorization> = listOf(authorization),
)

data class PartialCancellationResult(
    val order: PaymentOrder,
    val allocation: PaymentAllocation,
    val authorization: Authorization,
    val ledgerEntry: LedgerEntry,
)
