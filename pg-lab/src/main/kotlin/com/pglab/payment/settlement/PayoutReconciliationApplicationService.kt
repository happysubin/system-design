package com.pglab.payment.settlement

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PayoutReconciliationApplicationService(
    private val payoutReconciliationBatchService: PayoutReconciliationBatchService,
) {
    @Transactional
    fun reconcile(): PayoutReconciliationSummary {
        val payouts = payoutReconciliationBatchService.reconcile()

        return PayoutReconciliationSummary(
            processedCount = payouts.size,
            succeededCount = payouts.count { it.status == PayoutStatus.SUCCEEDED },
            failedCount = payouts.count { it.status == PayoutStatus.FAILED },
            stillReconcilingCount = payouts.count { it.status == PayoutStatus.RECONCILING },
        )
    }
}

data class PayoutReconciliationSummary(
    val processedCount: Int,
    val succeededCount: Int,
    val failedCount: Int,
    val stillReconcilingCount: Int,
)
