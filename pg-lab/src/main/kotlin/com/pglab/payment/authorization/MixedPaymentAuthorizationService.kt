package com.pglab.payment.authorization

import com.pglab.payment.allocation.PaymentAllocation
import com.pglab.payment.allocation.PaymentAllocationStatus
import com.pglab.payment.ledger.LedgerEntry
import com.pglab.payment.ledger.LedgerEntryType
import com.pglab.payment.order.PaymentOrder
import com.pglab.payment.order.PaymentOrderStatus
import com.pglab.payment.shared.Money
import java.time.OffsetDateTime

class MixedPaymentAuthorizationService {
    fun authorize(command: MixedPaymentAuthorizationCommand): MixedPaymentAuthorizationResult {
        require(command.authorizationRequests.isNotEmpty()) { "authorizationRequests must not be empty" }

        val requestedTotal = command.authorizationRequests.sumOf { it.requestedAmount.amount }
        require(requestedTotal == command.totalAmount.amount) { "requested amount sum must match total amount" }
        require(command.authorizationRequests.all { it.requestedAmount.currency == command.totalAmount.currency }) {
            "all requested currencies must match total amount currency"
        }
        require(command.authorizationRequests.all { it.approvedAmount.currency == command.totalAmount.currency }) {
            "all approved currencies must match total amount currency"
        }
        require(command.authorizationRequests.all { it.approvedAmount.amount <= it.requestedAmount.amount }) {
            "approved amount must not exceed requested amount"
        }

        val approvedTotal = command.authorizationRequests.sumOf { it.approvedAmount.amount }
        val finalStatus = if (approvedTotal == command.totalAmount.amount) {
            PaymentOrderStatus.AUTHORIZED
        } else {
            PaymentOrderStatus.PARTIALLY_AUTHORIZED
        }
        val allocationStatus = if (approvedTotal == command.totalAmount.amount) {
            PaymentAllocationStatus.AUTHORIZED
        } else {
            PaymentAllocationStatus.PARTIALLY_AUTHORIZED
        }

        val order = PaymentOrder(
            merchantId = command.merchantId,
            merchantOrderId = command.merchantOrderId,
            totalAmount = command.totalAmount,
            status = finalStatus,
        )
        val allocation = PaymentAllocation(
            paymentOrder = order,
            payerReference = command.payerReference,
            allocationAmount = command.totalAmount,
            sequence = 1,
            status = allocationStatus,
        )

        val authorizations = command.authorizationRequests.map { request ->
            Authorization(
                paymentAllocation = allocation,
                instrumentType = request.instrumentType,
                requestedAmount = request.requestedAmount,
                approvedAmount = request.approvedAmount,
                pgTransactionId = request.pgTransactionId,
                approvalCode = request.approvalCode,
                approvedAt = request.approvedAt,
            )
        }

        val ledgerEntries = authorizations.map { authorization ->
            LedgerEntry(
                paymentOrder = order,
                paymentAllocation = allocation,
                authorization = authorization,
                type = LedgerEntryType.AUTH_CAPTURED,
                amount = authorization.approvedAmount,
                referenceTransactionId = authorization.pgTransactionId,
                description = "mixed payment authorization",
            )
        }

        return MixedPaymentAuthorizationResult(
            order = order,
            allocation = allocation,
            authorizations = authorizations,
            ledgerEntries = ledgerEntries,
        )
    }
}

data class MixedPaymentAuthorizationCommand(
    val merchantId: String,
    val merchantOrderId: String,
    val payerReference: String,
    val totalAmount: Money,
    val authorizationRequests: List<MixedAuthorizationRequest>,
)

data class MixedAuthorizationRequest(
    val instrumentType: InstrumentType,
    val requestedAmount: Money,
    val approvedAmount: Money,
    val pgTransactionId: String,
    val approvalCode: String? = null,
    val approvedAt: OffsetDateTime? = null,
)

data class MixedPaymentAuthorizationResult(
    val order: PaymentOrder,
    val allocation: PaymentAllocation,
    val authorizations: List<Authorization>,
    val ledgerEntries: List<LedgerEntry>,
)
