package com.paymentlab.payment.application

import com.paymentlab.payment.api.dto.PaymentWebhookRequest
import com.paymentlab.payment.api.dto.PaymentWebhookResponse
import com.paymentlab.payment.domain.PaymentStatus
import com.paymentlab.payment.infrastructure.persistence.PaymentAttemptRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
/**
 * PG 웹훅을 검증하고 최종 결제 상태를 반영하는 application service다.
 *
 * 현재는 토스페이먼츠 기준으로
 * - `merchantOrderId` 일치 여부
 * - 저장된 webhook secret과 요청 secret 일치 여부
 * 를 먼저 확인한 뒤,
 * `PENDING` 상태의 결제만 `DONE/FAILED`로 확정한다.
 */
class PaymentWebhookApplicationService(
    private val paymentAttemptRepository: PaymentAttemptRepository,
    private val paymentFinalizationService: PaymentFinalizationService,
) {

    @Transactional
    fun handleWebhook(request: PaymentWebhookRequest): PaymentWebhookResponse {
        val paymentAttempt = paymentAttemptRepository.findByPgTransactionId(request.pgTransactionId)
            ?: throw IllegalArgumentException("payment attempt not found for pgTransactionId: ${request.pgTransactionId}")

        if (paymentAttempt.merchantOrderId != request.merchantOrderId) {
            throw IllegalArgumentException("invalid merchantOrderId")
        }

        if (paymentAttempt.webhookSecret == null || paymentAttempt.webhookSecret != request.secret) {
            throw IllegalArgumentException("invalid webhook secret")
        }

        if (paymentAttempt.status == PaymentStatus.DONE || paymentAttempt.status == PaymentStatus.FAILED || paymentAttempt.status == PaymentStatus.CANCELLED) {
            return PaymentWebhookResponse(
                paymentAttemptId = paymentAttempt.id,
                status = paymentAttempt.status,
            )
        }

        if (paymentAttempt.status != PaymentStatus.PENDING) {
            throw IllegalStateException("payment attempt is not pending: ${paymentAttempt.status}")
        }

        val nextStatus = if (request.result == "SUCCESS") {
            PaymentStatus.DONE
        } else {
            PaymentStatus.FAILED
        }

        val updated = paymentAttemptRepository.updateStatusByPgTransactionIdIfCurrentStatus(
            request.pgTransactionId,
            PaymentStatus.PENDING,
            nextStatus,
        )
        if (updated == 0) {
            throw IllegalStateException("payment attempt is no longer pending for pgTransactionId: ${request.pgTransactionId}")
        }

        paymentFinalizationService.finalizeInventoryHold(paymentAttempt, nextStatus)

        return PaymentWebhookResponse(
            paymentAttemptId = paymentAttempt.id,
            status = nextStatus,
        )
    }
}
