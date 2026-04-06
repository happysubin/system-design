package com.paymentlab.payment.application

import com.paymentlab.payment.api.dto.PaymentWebhookRequest
import com.paymentlab.payment.domain.PaymentAttempt
import com.paymentlab.payment.domain.PaymentStatus
import com.paymentlab.payment.infrastructure.persistence.PaymentAttemptRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import kotlin.test.assertEquals

@ExtendWith(MockitoExtension::class)
class PaymentWebhookApplicationServiceTest {

    @Mock
    lateinit var paymentAttemptRepository: PaymentAttemptRepository

    @Test
    fun `성공 웹훅이 오면 pending 결제 시도를 done으로 확정한다`() {
        val paymentAttempt = PaymentAttempt(
            id = 10,
            orderId = 1,
            merchantOrderId = "order-1",
            checkoutKey = "checkout-1",
            pgTransactionId = "pg-tx-1",
            amount = 15000,
            status = PaymentStatus.PENDING,
        )
        val service = PaymentWebhookApplicationService(paymentAttemptRepository)

        given(paymentAttemptRepository.findByPgTransactionId("pg-tx-1")).willReturn(paymentAttempt)

        val result = service.handleWebhook(
            PaymentWebhookRequest(
                pgTransactionId = "pg-tx-1",
                result = "SUCCESS",
            ),
        )

        assertEquals(10, result.paymentAttemptId)
        assertEquals(PaymentStatus.DONE, result.status)
        assertEquals(PaymentStatus.DONE, paymentAttempt.status)
        verify(paymentAttemptRepository).save(paymentAttempt)
    }

    @Test
    fun `실패 웹훅이 오면 pending 결제 시도를 failed로 확정한다`() {
        val paymentAttempt = PaymentAttempt(
            id = 10,
            orderId = 1,
            merchantOrderId = "order-1",
            checkoutKey = "checkout-1",
            pgTransactionId = "pg-tx-1",
            amount = 15000,
            status = PaymentStatus.PENDING,
        )
        val service = PaymentWebhookApplicationService(paymentAttemptRepository)

        given(paymentAttemptRepository.findByPgTransactionId("pg-tx-1")).willReturn(paymentAttempt)

        val result = service.handleWebhook(
            PaymentWebhookRequest(
                pgTransactionId = "pg-tx-1",
                result = "FAIL",
            ),
        )

        assertEquals(PaymentStatus.FAILED, result.status)
        assertEquals(PaymentStatus.FAILED, paymentAttempt.status)
        verify(paymentAttemptRepository).save(paymentAttempt)
    }

    @Test
    fun `이미 done인 결제에 같은 성공 웹훅이 다시 오면 저장하지 않고 그대로 반환한다`() {
        val paymentAttempt = PaymentAttempt(
            id = 10,
            orderId = 1,
            merchantOrderId = "order-1",
            checkoutKey = "checkout-1",
            pgTransactionId = "pg-tx-1",
            amount = 15000,
            status = PaymentStatus.DONE,
        )
        val service = PaymentWebhookApplicationService(paymentAttemptRepository)

        given(paymentAttemptRepository.findByPgTransactionId("pg-tx-1")).willReturn(paymentAttempt)

        val result = service.handleWebhook(
            PaymentWebhookRequest(
                pgTransactionId = "pg-tx-1",
                result = "SUCCESS",
            ),
        )

        assertEquals(PaymentStatus.DONE, result.status)
        assertEquals(PaymentStatus.DONE, paymentAttempt.status)
        verify(paymentAttemptRepository, never()).save(paymentAttempt)
    }

    @Test
    fun `이미 failed인 결제에 같은 실패 웹훅이 다시 오면 저장하지 않고 그대로 반환한다`() {
        val paymentAttempt = PaymentAttempt(
            id = 10,
            orderId = 1,
            merchantOrderId = "order-1",
            checkoutKey = "checkout-1",
            pgTransactionId = "pg-tx-1",
            amount = 15000,
            status = PaymentStatus.FAILED,
        )
        val service = PaymentWebhookApplicationService(paymentAttemptRepository)

        given(paymentAttemptRepository.findByPgTransactionId("pg-tx-1")).willReturn(paymentAttempt)

        val result = service.handleWebhook(
            PaymentWebhookRequest(
                pgTransactionId = "pg-tx-1",
                result = "FAIL",
            ),
        )

        assertEquals(PaymentStatus.FAILED, result.status)
        assertEquals(PaymentStatus.FAILED, paymentAttempt.status)
        verify(paymentAttemptRepository, never()).save(paymentAttempt)
    }

    @Test
    fun `pending이 아닌 ready 상태 결제에는 웹훅으로 최종 확정할 수 없다`() {
        val paymentAttempt = PaymentAttempt(
            id = 10,
            orderId = 1,
            merchantOrderId = "order-1",
            checkoutKey = "checkout-1",
            pgTransactionId = "pg-tx-1",
            amount = 15000,
            status = PaymentStatus.READY,
        )
        val service = PaymentWebhookApplicationService(paymentAttemptRepository)

        given(paymentAttemptRepository.findByPgTransactionId("pg-tx-1")).willReturn(paymentAttempt)

        kotlin.test.assertFailsWith<IllegalStateException> {
            service.handleWebhook(
                PaymentWebhookRequest(
                    pgTransactionId = "pg-tx-1",
                    result = "SUCCESS",
                ),
            )
        }

        verify(paymentAttemptRepository, never()).save(paymentAttempt)
    }

    @Test
    fun `cancelled 결제에 웹훅이 다시 와도 저장하지 않고 그대로 반환한다`() {
        val paymentAttempt = PaymentAttempt(
            id = 10,
            orderId = 1,
            merchantOrderId = "order-1",
            checkoutKey = "checkout-1",
            pgTransactionId = "pg-tx-1",
            amount = 15000,
            status = PaymentStatus.CANCELLED,
        )
        val service = PaymentWebhookApplicationService(paymentAttemptRepository)

        given(paymentAttemptRepository.findByPgTransactionId("pg-tx-1")).willReturn(paymentAttempt)

        val result = service.handleWebhook(
            PaymentWebhookRequest(
                pgTransactionId = "pg-tx-1",
                result = "SUCCESS",
            ),
        )

        assertEquals(PaymentStatus.CANCELLED, result.status)
        assertEquals(PaymentStatus.CANCELLED, paymentAttempt.status)
        verify(paymentAttemptRepository, never()).save(paymentAttempt)
    }
}
