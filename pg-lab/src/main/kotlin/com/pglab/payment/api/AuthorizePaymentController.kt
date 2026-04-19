package com.pglab.payment.api

import com.pglab.payment.authorization.AllocationAuthorizationRequest
import com.pglab.payment.authorization.AuthorizationRequest
import com.pglab.payment.authorization.AuthorizePaymentCommand
import com.pglab.payment.authorization.AuthorizationFacade
import com.pglab.payment.shared.CurrencyCode
import com.pglab.payment.shared.Money
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime

@RestController
@RequestMapping("/api/payments")
class AuthorizePaymentController(
    private val authorizationFacade: AuthorizationFacade,
) {
    @PostMapping("/authorize")
    fun authorize(@RequestBody request: AuthorizePaymentApiRequest): AuthorizePaymentApiResponse {
        val command = AuthorizePaymentCommand(
            merchantId = request.merchantId,
            merchantOrderId = request.merchantOrderId,
            totalAmount = Money(request.totalAmount, CurrencyCode.valueOf(request.currency)),
            lines = request.lines.map { line ->
                com.pglab.payment.authorization.PaymentOrderLineRequest(
                    lineReference = line.lineReference,
                    payeeId = line.payeeId,
                    lineAmount = Money(line.lineAmount, CurrencyCode.valueOf(request.currency)),
                    quantity = line.quantity,
                )
            },
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
                            approvedAt = authorization.approvedAt,
                            linePortions = authorization.linePortions.map { linePortion ->
                                com.pglab.payment.authorization.AuthorizationLinePortionRequest(
                                    lineReference = linePortion.lineReference,
                                    amount = Money(linePortion.amount, CurrencyCode.valueOf(request.currency)),
                                )
                            },
                        )
                    },
                )
            },
        )

        val result = authorizationFacade.authorize(command)

        return AuthorizePaymentApiResponse(
            orderId = result.authorizePaymentResult.order.id ?: 0L,
            orderStatus = result.authorizePaymentResult.order.status.name,
            allocationCount = result.authorizePaymentResult.allocations.size,
            authorizationCount = result.authorizePaymentResult.authorizations.size,
            ledgerEntryCount = result.authorizePaymentResult.ledgerEntries.size,
            upstreamResult = result.upstreamResult,
        )
    }
}

data class AuthorizePaymentApiRequest(
    val merchantId: String,
    val merchantOrderId: String,
    val totalAmount: Long,
    val currency: String,
    val lines: List<AuthorizePaymentOrderLineApiRequest>,
    val allocations: List<AuthorizePaymentAllocationApiRequest>,
)

data class AuthorizePaymentOrderLineApiRequest(
    val lineReference: String,
    val payeeId: String,
    val lineAmount: Long,
    val quantity: Int,
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
    val approvedAt: OffsetDateTime? = null,
    val linePortions: List<AuthorizePaymentAuthorizationLinePortionApiRequest>,
)

data class AuthorizePaymentAuthorizationLinePortionApiRequest(
    val lineReference: String,
    val amount: Long,
)

data class AuthorizePaymentApiResponse(
    val orderId: Long,
    val orderStatus: String,
    val allocationCount: Int,
    val authorizationCount: Int,
    val ledgerEntryCount: Int,
    val upstreamResult: String,
)
