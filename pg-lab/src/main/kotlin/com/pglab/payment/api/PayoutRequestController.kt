package com.pglab.payment.api

import com.pglab.payment.settlement.PayoutRequestApplicationService
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime

@RestController
@RequestMapping("/api/settlements")
class PayoutRequestController(
    private val payoutRequestApplicationService: PayoutRequestApplicationService,
) {
    @PostMapping("/{settlementId}/payouts")
    fun requestPayout(
        @PathVariable settlementId: Long,
        @RequestBody request: PayoutRequestApiRequest,
    ): PayoutRequestApiResponse {
        val result = payoutRequestApplicationService.requestPayout(
            settlementId = settlementId,
            bankCode = request.bankCode,
            bankAccountNumber = request.bankAccountNumber,
            accountHolderName = request.accountHolderName,
            bankTransferRequestId = request.bankTransferRequestId,
            requestedAt = OffsetDateTime.parse(request.requestedAt),
        )

        return PayoutRequestApiResponse(
            settlementStatus = result.settlement.status.name,
            payoutStatus = result.payout.status.name,
            retryCount = result.payout.retryCount,
            requestedAmount = result.payout.requestedAmount.amount,
        )
    }
}

data class PayoutRequestApiRequest(
    val bankCode: String,
    val bankAccountNumber: String,
    val accountHolderName: String,
    val bankTransferRequestId: String,
    val requestedAt: String,
)

data class PayoutRequestApiResponse(
    val settlementStatus: String,
    val payoutStatus: String,
    val retryCount: Int,
    val requestedAmount: Long,
)
