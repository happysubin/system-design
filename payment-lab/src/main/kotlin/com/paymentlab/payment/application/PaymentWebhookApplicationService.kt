package com.paymentlab.payment.application

import com.paymentlab.payment.api.dto.PaymentWebhookRequest
import com.paymentlab.payment.api.dto.PaymentWebhookResponse
import com.paymentlab.payment.domain.PaymentStatus
import com.paymentlab.payment.infrastructure.persistence.PaymentAttemptRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PaymentWebhookApplicationService(
    private val paymentAttemptRepository: PaymentAttemptRepository,
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

        return PaymentWebhookResponse(
            paymentAttemptId = paymentAttempt.id,
            status = nextStatus,
        )
    }
}
