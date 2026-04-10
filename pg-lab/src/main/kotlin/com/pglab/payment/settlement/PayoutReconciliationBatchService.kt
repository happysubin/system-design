package com.pglab.payment.settlement

class PayoutReconciliationBatchService(
    private val payoutReader: ReconciliationPayoutReader,
    private val upstreamReader: UpstreamPayoutReader,
) {
    fun reconcile(): List<Payout> {
        val payouts = payoutReader.findReconciliationTargets()

        payouts.forEach { payout ->
            val result = upstreamReader.check(payout.bankTransferRequestId)
            when (result.status) {
                UpstreamPayoutStatus.SUCCEEDED -> {
                    payout.markSucceeded(result.bankTransferTransactionId ?: payout.bankTransferRequestId, result.checkedAt)
                    payout.settlement?.markPaid(result.checkedAt)
                }

                UpstreamPayoutStatus.FAILED -> {
                    payout.markFailed(
                        result.failureCode ?: "UPSTREAM_FAILED",
                        result.failureReason ?: "upstream reported failure",
                        result.checkedAt,
                    )
                    payout.settlement?.markFailed()
                }

                UpstreamPayoutStatus.UNKNOWN -> payout.markReconciling()
            }
        }

        return payouts
    }
}

interface ReconciliationPayoutReader {
    fun findReconciliationTargets(): List<Payout>
}

interface UpstreamPayoutReader {
    fun check(bankTransferRequestId: String): UpstreamPayoutCheckResult
}

data class UpstreamPayoutCheckResult(
    val status: UpstreamPayoutStatus,
    val bankTransferTransactionId: String? = null,
    val failureCode: String? = null,
    val failureReason: String? = null,
    val checkedAt: java.time.OffsetDateTime,
)

enum class UpstreamPayoutStatus {
    SUCCEEDED,
    FAILED,
    UNKNOWN,
}
