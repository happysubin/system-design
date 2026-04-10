package com.pglab.payment.api

import com.pglab.payment.settlement.PayoutReconciliationApplicationService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/payouts")
class PayoutReconciliationController(
    private val payoutReconciliationApplicationService: PayoutReconciliationApplicationService,
) {
    @PostMapping("/reconciliation")
    fun reconcile(@RequestBody request: PayoutReconciliationApiRequest): PayoutReconciliationApiResponse {
        val result = payoutReconciliationApplicationService.reconcile()

        return PayoutReconciliationApiResponse(
            processedCount = result.processedCount,
            succeededCount = result.succeededCount,
            failedCount = result.failedCount,
            stillReconcilingCount = result.stillReconcilingCount,
        )
    }
}

data class PayoutReconciliationApiRequest(
    val noop: String? = null,
)

data class PayoutReconciliationApiResponse(
    val processedCount: Int,
    val succeededCount: Int,
    val failedCount: Int,
    val stillReconcilingCount: Int,
)
