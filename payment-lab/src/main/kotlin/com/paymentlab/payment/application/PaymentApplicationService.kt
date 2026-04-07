package com.paymentlab.payment.application

import com.paymentlab.payment.api.dto.CreatePaymentAttemptRequest
import com.paymentlab.payment.api.dto.CreatePaymentAttemptResponse
import com.paymentlab.payment.api.dto.ApprovePaymentAttemptResponse
import com.paymentlab.payment.api.dto.ReconcilePaymentAttemptResponse
import com.paymentlab.payment.domain.PaymentAttempt
import com.paymentlab.payment.domain.PaymentStatus
import com.paymentlab.payment.infrastructure.persistence.PaymentAttemptRepository
import com.paymentlab.payment.infrastructure.pg.PgApproveOutcome
import com.paymentlab.payment.infrastructure.pg.PgClient
import com.paymentlab.payment.infrastructure.redis.CheckoutKeyStore
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PaymentApplicationService(
    private val paymentAttemptRepository: PaymentAttemptRepository,
    private val pgClient: PgClient,
    private val checkoutKeyStore: CheckoutKeyStore,
) {

    @Transactional
    fun startPayment(request: CreatePaymentAttemptRequest): ApprovePaymentAttemptResponse {
        val paymentAttempt = createPaymentAttempt(request)
        return approvePaymentAttempt(paymentAttempt.paymentAttemptId)
    }

    @Transactional
    fun createPaymentAttempt(request: CreatePaymentAttemptRequest): CreatePaymentAttemptResponse {
        val existingAttempt = paymentAttemptRepository.findByCheckoutKey(request.checkoutKey)
        if (existingAttempt != null) {
            return CreatePaymentAttemptResponse(
                paymentAttemptId = existingAttempt.id,
                orderId = existingAttempt.orderId,
                status = existingAttempt.status,
            )
        }

        val isValidCheckoutKey = checkoutKeyStore.consumeIfValid(
            request.checkoutKey,
            request.orderId,
            request.merchantOrderId,
            request.amount,
        )
        if (!isValidCheckoutKey) {
            throw IllegalArgumentException("invalid checkout key: ${request.checkoutKey}")
        }

        val savedAttempt = paymentAttemptRepository.save(
            PaymentAttempt(
                orderId = request.orderId,
                merchantOrderId = request.merchantOrderId,
                checkoutKey = request.checkoutKey,
                amount = request.amount,
                status = PaymentStatus.READY,
            ),
        )

        return CreatePaymentAttemptResponse(
            paymentAttemptId = savedAttempt.id,
            orderId = savedAttempt.orderId,
            status = savedAttempt.status,
        )
    }

    @Transactional
    fun approvePaymentAttempt(paymentAttemptId: Long): ApprovePaymentAttemptResponse {
        val paymentAttempt = paymentAttemptRepository.findById(paymentAttemptId)
            .orElseThrow { IllegalArgumentException("payment attempt not found: $paymentAttemptId") }

        if (paymentAttempt.status != PaymentStatus.READY) {
            throw IllegalStateException("payment attempt is not ready: ${paymentAttempt.status}")
        }

        val approveResult = pgClient.approve(
            paymentAttempt.id,
            paymentAttempt.merchantOrderId,
            paymentAttempt.amount,
        )

        val nextStatus = when (approveResult.outcome) {
            PgApproveOutcome.PENDING -> PaymentStatus.PENDING
            PgApproveOutcome.DECLINED -> PaymentStatus.DECLINED
        }

        val updated = paymentAttemptRepository.updateStatusAndPgTransactionIdAndWebhookSecretIfCurrentStatus(
            paymentAttempt.id,
            PaymentStatus.READY,
            nextStatus,
            approveResult.pgTransactionId,
            approveResult.webhookSecret,
        )
        if (updated == 0) {
            throw IllegalStateException("payment attempt is no longer ready: ${paymentAttempt.id}")
        }

        return ApprovePaymentAttemptResponse(
            paymentAttemptId = paymentAttempt.id,
            status = nextStatus,
            pgTransactionId = approveResult.pgTransactionId,
        )
    }

    @Transactional
    fun reconcilePaymentAttempt(paymentAttemptId: Long): ReconcilePaymentAttemptResponse {
        val paymentAttempt = paymentAttemptRepository.findById(paymentAttemptId)
            .orElseThrow { IllegalArgumentException("payment attempt not found: $paymentAttemptId") }

        if (paymentAttempt.status != PaymentStatus.PENDING) {
            throw IllegalStateException("payment attempt is not pending: ${paymentAttempt.status}")
        }

        val result = pgClient.query(paymentAttempt.pgTransactionId ?: throw IllegalStateException("pgTransactionId is missing"))

        val nextStatus = if (result == "SUCCESS") {
            PaymentStatus.DONE
        } else {
            PaymentStatus.FAILED
        }

        val updated = paymentAttemptRepository.updateStatusIfCurrentStatus(
            paymentAttempt.id,
            PaymentStatus.PENDING,
            nextStatus,
        )
        if (updated == 0) {
            throw IllegalStateException("payment attempt is no longer pending: ${paymentAttempt.id}")
        }

        return ReconcilePaymentAttemptResponse(
            paymentAttemptId = paymentAttempt.id,
            status = nextStatus,
        )
    }
}
