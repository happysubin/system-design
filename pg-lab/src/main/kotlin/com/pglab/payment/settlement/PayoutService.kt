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
        // 이미 요청됨/전송됨/정합성 점검 중인 payout이 있으면,
        // 같은 settlement에 대해 중복 송금 시도를 만들 가능성이 있으므로 차단한다.
        check(existingPayouts.none { it.status == PayoutStatus.REQUESTED || it.status == PayoutStatus.SENT || it.status == PayoutStatus.RECONCILING }) {
            "active payout already exists"
        }

        // 지급 요청이 시작되면 settlement는 더 이상 단순 예정 상태가 아니라 처리중 상태가 된다.
        settlement.markProcessing()

        // 이전 시도들의 최대 retryCount를 읽어서 다음 시도의 순번을 계산한다.
        // 첫 시도는 0, 그다음부터는 1, 2, 3... 으로 증가한다.
        val nextRetryCount = (existingPayouts.maxOfOrNull { it.retryCount } ?: -1) + 1

        // 실제 은행 전송은 아직 하지 않았고, 우선 REQUESTED 상태의 payout attempt를 만든다.
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
        // 외부 은행/원천사로 요청이 실제 전송된 시점을 기록한다.
        payout.markSent(sentAt)
    }

    fun markTimedOut(payout: Payout) {
        // timeout은 곧바로 실패가 아니라 "결과를 아직 모름"에 가깝다.
        // 따라서 RECONCILING으로 보내고, 언제부터 미확정 상태였는지 기록한다.
        payout.markReconciling(payout.sentAt ?: payout.requestedAt)
    }

    fun markSucceeded(payout: Payout, bankTransferTransactionId: String, completedAt: OffsetDateTime) {
        // 지급 시도가 성공으로 확정되면 payout 자체를 성공 처리하고,
        // 상위 settlement도 실제 지급 완료 상태로 올린다.
        payout.markSucceeded(bankTransferTransactionId, completedAt)
        payout.settlement?.markPaid(completedAt)
    }

    fun markFailed(payout: Payout, failureCode: String, failureReason: String, completedAt: OffsetDateTime) {
        // 지급 실패가 확정되면 payout의 실패 메타데이터를 남기고,
        // 상위 settlement 역시 실패 상태로 동기화한다.
        payout.markFailed(failureCode, failureReason, completedAt)
        payout.settlement?.markFailed()
    }
}
