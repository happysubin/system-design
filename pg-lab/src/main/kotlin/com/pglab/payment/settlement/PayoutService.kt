package com.pglab.payment.settlement

import java.time.OffsetDateTime

/**
 * 정산 건에 대한 실제 지급 시도 유스케이스를 표현하는 서비스다.
 *
 * 정산 계산 결과를 바로 송금 상태로 바꾸지 않고,
 * Payout을 생성하고 그 결과에 따라 Settlement와 Payout 상태를 함께 전이시킨다.
 */
class PayoutService {
    fun requestPayout(
        settlement: Settlement,
        bankCode: String,
        bankAccountNumber: String,
        accountHolderName: String,
        bankTransferRequestId: String,
        requestedAt: OffsetDateTime,
        existingPayouts: List<Payout> = emptyList(),
    ): Payout {
        check(existingPayouts.none { it.status == PayoutStatus.REQUESTED || it.status == PayoutStatus.SENT || it.status == PayoutStatus.RECONCILING }) {
            "active payout already exists"
        }

        settlement.markProcessing()
        val nextRetryCount = (existingPayouts.maxOfOrNull { it.retryCount } ?: -1) + 1

        return Payout(
            settlement = settlement,
            requestedAmount = settlement.netAmount,
            bankCode = bankCode,
            bankAccountNumber = bankAccountNumber,
            accountHolderName = accountHolderName,
            bankTransferRequestId = bankTransferRequestId,
            requestedAt = requestedAt,
            retryCount = nextRetryCount,
        )
    }

    fun markSent(payout: Payout, sentAt: OffsetDateTime) {
        payout.markSent(sentAt)
    }

    fun markTimedOut(payout: Payout) {
        payout.markReconciling(payout.sentAt ?: payout.requestedAt)
    }

    fun markSucceeded(payout: Payout, bankTransferTransactionId: String, completedAt: OffsetDateTime) {
        payout.markSucceeded(bankTransferTransactionId, completedAt)
        payout.settlement?.markPaid(completedAt)
    }

    fun markFailed(payout: Payout, failureCode: String, failureReason: String, completedAt: OffsetDateTime) {
        payout.markFailed(failureCode, failureReason, completedAt)
        payout.settlement?.markFailed()
    }
}
