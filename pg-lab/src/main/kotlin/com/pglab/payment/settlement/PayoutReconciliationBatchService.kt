package com.pglab.payment.settlement

class PayoutReconciliationBatchService(
    private val payoutReader: ReconciliationPayoutReader,
    private val upstreamReader: UpstreamPayoutReader,
    private val payoutWriter: ReconciliationPayoutWriter,
) {
    fun reconcile(): List<Payout> {
        // 먼저 정합성 점검이 필요한 payout attempt만 읽어온다.
        // 보통 timeout이나 unknown 상태로 남아 있는 건들이 대상이 된다.
        val payouts = payoutReader.findReconciliationTargets()

        payouts.forEach { payout ->
            // 외부 시스템에 requestId 기준으로 재조회하고,
            // 마지막 확인 시각을 항상 남겨서 운영자가 배치 수행 여부를 추적할 수 있게 한다.
            val result = upstreamReader.check(payout.bankTransferRequestId)
            payout.lastCheckedAt = result.checkedAt
            when (result.status) {
                UpstreamPayoutStatus.SUCCEEDED -> {
                    // 뒤늦게 성공이 확인되면 payout/settlement를 모두 성공으로 확정한다.
                    payout.markSucceeded(result.bankTransferTransactionId ?: payout.bankTransferRequestId, result.checkedAt)
                    payout.settlement?.markPaid(result.checkedAt)
                }

                UpstreamPayoutStatus.FAILED -> {
                    // 외부에서 확정 실패가 확인되면 실패 메타데이터를 남기고 settlement도 실패로 동기화한다.
                    payout.markFailed(
                        result.failureCode ?: "UPSTREAM_FAILED",
                        result.failureReason ?: "upstream reported failure",
                        result.checkedAt,
                    )
                    payout.settlement?.markFailed()
                }

                UpstreamPayoutStatus.UNKNOWN ->
                    // 여전히 알 수 없으면 reconciling 상태를 유지하되,
                    // 마지막 확인 시각과 reconciling 시작 시각을 갱신/보존한다.
                    payout.markReconciling(result.checkedAt)
            }
        }

        // 배치가 바꾼 payout 상태는 명시적인 저장 포트를 통해 영속화한다.
        return payoutWriter.saveAll(payouts)
    }
}

interface ReconciliationPayoutReader {
    fun findReconciliationTargets(): List<Payout>
}

interface ReconciliationPayoutWriter {
    fun saveAll(payouts: List<Payout>): List<Payout>
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
