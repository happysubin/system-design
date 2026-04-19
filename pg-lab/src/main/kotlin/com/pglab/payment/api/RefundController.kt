package com.pglab.payment.api

import com.pglab.payment.authorization.AuthorizationRepository
import com.pglab.payment.authorization.RefundCommand
import com.pglab.payment.authorization.RefundService
import com.pglab.payment.ledger.LedgerEntryRepository
import com.pglab.payment.ledger.LedgerEntryType
import com.pglab.payment.shared.CurrencyCode
import com.pglab.payment.shared.Money
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/payments/authorizations")
class RefundController(
    private val refundService: RefundService,
    private val authorizationRepository: AuthorizationRepository,
    private val ledgerEntryRepository: LedgerEntryRepository,
) {
    @PostMapping("/{authorizationId}/refund")
    fun refund(
        @PathVariable authorizationId: Long,
        @RequestBody request: RefundApiRequest,
    ): RefundApiResponse {
        val authorization = authorizationRepository.findById(authorizationId)
            .orElseThrow { IllegalArgumentException("authorization not found") }
        val allocation = authorization.paymentAllocation ?: error("paymentAllocation must exist")
        val order = allocation.paymentOrder ?: error("paymentOrder must exist")
        val allocationAuthorizations = authorizationRepository.findAllByPaymentAllocationId(allocation.id ?: 0L)
        val orderAuthorizations = authorizationRepository.findAllByPaymentAllocation_PaymentOrderId(order.id ?: 0L)
        val existingNegativeLedgerEntries = authorization.id?.let {
            ledgerEntryRepository.findAllByAuthorizationIdAndTypeIn(it, listOf(LedgerEntryType.CANCELLED, LedgerEntryType.REFUNDED))
        } ?: emptyList()

        val result = refundService.refund(
            RefundCommand(
                order = order,
                allocation = allocation,
                authorization = authorization,
                refundAmount = Money(request.refundAmount, CurrencyCode.valueOf(request.currency)),
                allocationAuthorizations = allocationAuthorizations,
                orderAuthorizations = orderAuthorizations,
                existingNegativeLedgerEntries = existingNegativeLedgerEntries,
            ),
        )

        return RefundApiResponse(
            orderStatus = result.order.status.name,
            allocationStatus = result.allocation.status.name,
            ledgerEntryType = result.ledgerEntries.first().type.name,
            ledgerEntryCount = result.ledgerEntries.size,
            refundAmount = result.ledgerEntries.sumOf { it.amount.amount },
        )
    }
}

data class RefundApiRequest(
    val refundAmount: Long,
    val currency: String,
)

data class RefundApiResponse(
    val orderStatus: String,
    val allocationStatus: String,
    val ledgerEntryType: String,
    val ledgerEntryCount: Int,
    val refundAmount: Long,
)
