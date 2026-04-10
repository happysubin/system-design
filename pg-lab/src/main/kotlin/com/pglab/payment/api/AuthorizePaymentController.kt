package com.pglab.payment.api

import com.pglab.payment.authorization.AllocationAuthorizationRequest
import com.pglab.payment.authorization.AuthorizationRequest
import com.pglab.payment.authorization.AuthorizePaymentCommand
import com.pglab.payment.authorization.AuthorizePaymentService
import com.pglab.payment.shared.CurrencyCode
import com.pglab.payment.shared.Money
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/payments")
class AuthorizePaymentController(
    private val authorizePaymentService: AuthorizePaymentService,
) {
    @PostMapping("/authorize")
    fun authorize(@RequestBody request: AuthorizePaymentApiRequest): AuthorizePaymentApiResponse {
        val command = AuthorizePaymentCommand(
            merchantId = request.merchantId,
            merchantOrderId = request.merchantOrderId,
            totalAmount = Money(request.totalAmount, CurrencyCode.valueOf(request.currency)),
            allocations = request.allocations.map { allocation ->
                AllocationAuthorizationRequest(
                    payerReference = allocation.payerReference,
                    allocationAmount = Money(allocation.allocationAmount, CurrencyCode.valueOf(request.currency)),
                    authorizations = allocation.authorizations.map { authorization ->
                        AuthorizationRequest(
                            instrumentType = authorization.instrumentType,
                            requestedAmount = Money(authorization.requestedAmount, CurrencyCode.valueOf(request.currency)),
                            approvedAmount = Money(authorization.approvedAmount, CurrencyCode.valueOf(request.currency)),
                            pgTransactionId = authorization.pgTransactionId,
                            approvalCode = authorization.approvalCode,
                        )
                    },
                )
            },
        )

        val result = authorizePaymentService.authorize(command)

        return AuthorizePaymentApiResponse(
            orderId = result.order.id ?: 0L,
            orderStatus = result.order.status.name,
            allocationCount = result.allocations.size,
            authorizationCount = result.authorizations.size,
            ledgerEntryCount = result.ledgerEntries.size,
        )
    }
}

data class AuthorizePaymentApiRequest(
    val merchantId: String,
    val merchantOrderId: String,
    val totalAmount: Long,
    val currency: String,
    val allocations: List<AuthorizePaymentAllocationApiRequest>,
)

data class AuthorizePaymentAllocationApiRequest(
    val payerReference: String,
    val allocationAmount: Long,
    val authorizations: List<AuthorizePaymentAuthorizationApiRequest>,
)

data class AuthorizePaymentAuthorizationApiRequest(
    val instrumentType: com.pglab.payment.authorization.InstrumentType,
    val requestedAmount: Long,
    val approvedAmount: Long,
    val pgTransactionId: String,
    val approvalCode: String? = null,
)

data class AuthorizePaymentApiResponse(
    val orderId: Long,
    val orderStatus: String,
    val allocationCount: Int,
    val authorizationCount: Int,
    val ledgerEntryCount: Int,
)
