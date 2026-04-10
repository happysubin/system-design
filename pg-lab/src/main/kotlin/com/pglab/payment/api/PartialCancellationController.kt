package com.pglab.payment.api

import com.pglab.payment.authorization.PartialCancellationCommand
import com.pglab.payment.authorization.PartialCancellationService
import com.pglab.payment.authorization.AuthorizationRepository
import com.pglab.payment.shared.CurrencyCode
import com.pglab.payment.shared.Money
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/payments/authorizations")
class PartialCancellationController(
    private val partialCancellationService: PartialCancellationService,
    private val authorizationRepository: AuthorizationRepository,
) {
    @PostMapping("/{authorizationId}/partial-cancel")
    fun partialCancel(
        @PathVariable authorizationId: Long,
        @RequestBody request: PartialCancellationApiRequest,
    ): PartialCancellationApiResponse {
        val authorization = authorizationRepository.findById(authorizationId)
            .orElseThrow { IllegalArgumentException("authorization not found") }
        val allocation = authorization.paymentAllocation ?: error("paymentAllocation must exist")
        val order = allocation.paymentOrder ?: error("paymentOrder must exist")
        val allAuthorizations = authorizationRepository.findAllByPaymentAllocationId(allocation.id ?: 0L)

        val result = partialCancellationService.cancel(
            PartialCancellationCommand(
                order = order,
                allocation = allocation,
                authorization = authorization,
                cancelAmount = Money(request.cancelAmount, CurrencyCode.valueOf(request.currency)),
                allAuthorizations = allAuthorizations,
            ),
        )

        return PartialCancellationApiResponse(
            orderStatus = result.order.status.name,
            allocationStatus = result.allocation.status.name,
            authorizationStatus = result.authorization.status.name,
            ledgerEntryType = result.ledgerEntry.type.name,
            cancelAmount = result.ledgerEntry.amount.amount,
        )
    }
}

data class PartialCancellationApiRequest(
    val cancelAmount: Long,
    val currency: String,
)

data class PartialCancellationApiResponse(
    val orderStatus: String,
    val allocationStatus: String,
    val authorizationStatus: String,
    val ledgerEntryType: String,
    val cancelAmount: Long,
)
