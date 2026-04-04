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

        paymentAttempt.status = if (request.result == "SUCCESS") {
            PaymentStatus.DONE
        } else {
            PaymentStatus.FAILED
        }

        paymentAttemptRepository.save(paymentAttempt)

        return PaymentWebhookResponse(
            paymentAttemptId = paymentAttempt.id,
            status = paymentAttempt.status,
        )
    }
}
