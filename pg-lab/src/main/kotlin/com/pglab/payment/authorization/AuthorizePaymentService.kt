package com.pglab.payment.authorization

import com.pglab.payment.allocation.PaymentAllocation
import com.pglab.payment.allocation.PaymentAllocationStatus
import com.pglab.payment.ledger.LedgerEntry
import com.pglab.payment.ledger.LedgerEntryType
import com.pglab.payment.order.PaymentOrder
import com.pglab.payment.order.PaymentOrderStatus
import com.pglab.payment.shared.Money
import java.time.OffsetDateTime

class AuthorizePaymentService {
    fun authorize(command: AuthorizePaymentCommand): AuthorizePaymentResult {
        require(command.allocations.isNotEmpty()) { "allocations must not be empty" }

        val allocationTotal = command.allocations.sumOf { it.allocationAmount.amount }
        require(allocationTotal == command.totalAmount.amount) { "allocation sum must match total amount" }

        require(command.allocations.all { it.allocationAmount.currency == command.totalAmount.currency }) {
            "all allocation currencies must match total amount currency"
        }

        command.allocations.forEach { allocation ->
            val requestedTotal = allocation.authorizations.sumOf { it.requestedAmount.amount }
            require(requestedTotal == allocation.allocationAmount.amount) {
                "authorization sum must match allocation amount"
            }
            require(allocation.authorizations.all { it.requestedAmount.currency == command.totalAmount.currency }) {
                "all requested currencies must match total amount currency"
            }
            require(allocation.authorizations.all { it.approvedAmount.currency == command.totalAmount.currency }) {
                "all approved currencies must match total amount currency"
            }
            require(allocation.authorizations.all { it.approvedAmount.amount <= it.requestedAmount.amount }) {
                "approved amount must not exceed requested amount"
            }
        }

        val orderApprovedTotal = command.allocations.sumOf { allocation ->
            allocation.authorizations.sumOf { it.approvedAmount.amount }
        }

        val order = PaymentOrder(
            merchantId = command.merchantId,
            merchantOrderId = command.merchantOrderId,
            totalAmount = command.totalAmount,
            status = if (orderApprovedTotal == command.totalAmount.amount) {
                PaymentOrderStatus.AUTHORIZED
            } else {
                PaymentOrderStatus.PARTIALLY_AUTHORIZED
            },
        )

        val allocationModels = command.allocations.mapIndexed { index, request ->
            val allocationApprovedTotal = request.authorizations.sumOf { it.approvedAmount.amount }
            PaymentAllocation(
                paymentOrder = order,
                payerReference = request.payerReference,
                allocationAmount = request.allocationAmount,
                sequence = index + 1,
                status = if (allocationApprovedTotal == request.allocationAmount.amount) {
                    PaymentAllocationStatus.AUTHORIZED
                } else {
                    PaymentAllocationStatus.PARTIALLY_AUTHORIZED
                },
            )
        }

        val authorizations = command.allocations.zip(allocationModels).flatMap { (allocationRequest, allocationModel) ->
            allocationRequest.authorizations.map { authorizationRequest ->
                Authorization(
                    paymentAllocation = allocationModel,
                    instrumentType = authorizationRequest.instrumentType,
                    requestedAmount = authorizationRequest.requestedAmount,
                    approvedAmount = authorizationRequest.approvedAmount,
                    pgTransactionId = authorizationRequest.pgTransactionId,
                    approvalCode = authorizationRequest.approvalCode,
                    approvedAt = authorizationRequest.approvedAt,
                )
            }
        }

        val ledgerEntries = authorizations.map { authorization ->
            LedgerEntry(
                paymentOrder = order,
                paymentAllocation = authorization.paymentAllocation,
                authorization = authorization,
                type = LedgerEntryType.AUTH_CAPTURED,
                amount = authorization.approvedAmount,
                referenceTransactionId = authorization.pgTransactionId,
                description = "payment authorization",
            )
        }

        return AuthorizePaymentResult(
            order = order,
            allocations = allocationModels,
            authorizations = authorizations,
            ledgerEntries = ledgerEntries,
        )
    }
}

data class AuthorizePaymentCommand(
    val merchantId: String,
    val merchantOrderId: String,
    val totalAmount: Money,
    val allocations: List<AllocationAuthorizationRequest>,
)

data class AllocationAuthorizationRequest(
    val payerReference: String,
    val allocationAmount: Money,
    val authorizations: List<AuthorizationRequest>,
)

data class AuthorizationRequest(
    val instrumentType: InstrumentType,
    val requestedAmount: Money,
    val approvedAmount: Money,
    val pgTransactionId: String,
    val approvalCode: String? = null,
    val approvedAt: OffsetDateTime? = null,
)

data class AuthorizePaymentResult(
    val order: PaymentOrder,
    val allocations: List<PaymentAllocation>,
    val authorizations: List<Authorization>,
    val ledgerEntries: List<LedgerEntry>,
)
