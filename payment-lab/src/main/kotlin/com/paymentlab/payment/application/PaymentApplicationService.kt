package com.paymentlab.payment.application

import com.paymentlab.payment.api.dto.CreatePaymentAttemptRequest
import com.paymentlab.payment.api.dto.CreatePaymentAttemptResponse
import com.paymentlab.payment.api.dto.ApprovePaymentAttemptResponse
import com.paymentlab.payment.api.dto.ReconcilePaymentAttemptResponse
import com.paymentlab.payment.domain.PaymentAttempt
import com.paymentlab.payment.domain.PaymentStatus
import com.paymentlab.payment.infrastructure.persistence.OrderRepository
import com.paymentlab.payment.infrastructure.persistence.PaymentAttemptRepository
import com.paymentlab.payment.infrastructure.pg.PgClient
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PaymentApplicationService(
    private val orderRepository: OrderRepository,
    private val paymentAttemptRepository: PaymentAttemptRepository,
    private val pgClient: PgClient,
) {

    @Transactional
    fun createPaymentAttempt(request: CreatePaymentAttemptRequest): CreatePaymentAttemptResponse {
        val existingAttempt = paymentAttemptRepository.findByIdempotencyKey(request.idempotencyKey)
        if (existingAttempt != null) {
            return CreatePaymentAttemptResponse(
                paymentAttemptId = existingAttempt.id,
                orderId = existingAttempt.order.id,
                status = existingAttempt.status,
            )
        }

        val order = orderRepository.findById(request.orderId)
            .orElseThrow { IllegalArgumentException("order not found: ${request.orderId}") }

        val savedAttempt = paymentAttemptRepository.save(
            PaymentAttempt(
                order = order,
                idempotencyKey = request.idempotencyKey,
                amount = order.amount,
                status = PaymentStatus.READY,
            ),
        )

        return CreatePaymentAttemptResponse(
            paymentAttemptId = savedAttempt.id,
            orderId = savedAttempt.order.id,
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

        val pgTransactionId = pgClient.approve(
            paymentAttempt.id,
            paymentAttempt.order.merchantOrderId,
            paymentAttempt.amount,
        )

        paymentAttempt.status = PaymentStatus.PENDING
        paymentAttempt.pgTransactionId = pgTransactionId
        paymentAttemptRepository.save(paymentAttempt)

        return ApprovePaymentAttemptResponse(
            paymentAttemptId = paymentAttempt.id,
            status = paymentAttempt.status,
            pgTransactionId = pgTransactionId,
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

        paymentAttempt.status = if (result == "SUCCESS") {
            PaymentStatus.DONE
        } else {
            PaymentStatus.FAILED
        }
        paymentAttemptRepository.save(paymentAttempt)

        return ReconcilePaymentAttemptResponse(
            paymentAttemptId = paymentAttempt.id,
            status = paymentAttempt.status,
        )
    }
}
