package com.paymentlab.payment.application

import com.paymentlab.payment.domain.PaymentAttempt
import com.paymentlab.payment.domain.PaymentStatus
import com.paymentlab.payment.infrastructure.persistence.PaymentAttemptRepository
import com.paymentlab.payment.infrastructure.pg.PgClient
import com.paymentlab.payment.infrastructure.redis.CheckoutKeyStore
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.verify
import org.mockito.Mockito.never
import org.mockito.Mockito.doReturn
import org.mockito.junit.jupiter.MockitoExtension
import java.util.Optional
import kotlin.test.assertEquals
import com.paymentlab.payment.infrastructure.pg.PgApproveResult
import com.paymentlab.payment.infrastructure.pg.PgApproveOutcome

@ExtendWith(MockitoExtension::class)
class PaymentApprovalApplicationServiceTest {

    @Mock
    lateinit var paymentAttemptRepository: PaymentAttemptRepository

    @Mock
    lateinit var pgClient: PgClient

    @Mock
    lateinit var checkoutKeyStore: CheckoutKeyStore

    @Test
    fun `ready 상태의 결제 시도를 승인 요청하면 pg 호출 후 pending 상태로 바꾼다`() {
        val paymentAttempt = PaymentAttempt(
            id = 10,
            orderId = 1,
            merchantOrderId = "order-1",
            checkoutKey = "checkout-1",
            amount = 15000,
            status = PaymentStatus.READY,
        )
        val service = PaymentApplicationService(paymentAttemptRepository, pgClient, checkoutKeyStore)

        given(paymentAttemptRepository.findById(paymentAttempt.id)).willReturn(Optional.of(paymentAttempt))
        given(pgClient.approve(paymentAttempt.id, paymentAttempt.merchantOrderId, paymentAttempt.amount)).willReturn(
            PgApproveResult(pgTransactionId = "pg-tx-1", webhookSecret = "secret-1", outcome = PgApproveOutcome.PENDING),
        )
        doReturn(1).`when`(paymentAttemptRepository)
            .updateStatusAndPgTransactionIdAndWebhookSecretIfCurrentStatus(paymentAttempt.id, PaymentStatus.READY, PaymentStatus.PENDING, "pg-tx-1", "secret-1")

        val result = service.approvePaymentAttempt(paymentAttempt.id)

        assertEquals(paymentAttempt.id, result.paymentAttemptId)
        assertEquals(PaymentStatus.PENDING, result.status)
        assertEquals("pg-tx-1", result.pgTransactionId)
        verify(pgClient).approve(paymentAttempt.id, paymentAttempt.merchantOrderId, paymentAttempt.amount)
    }

    @Test
    fun `ready가 아닌 결제 시도는 승인 요청할 수 없다`() {
        val paymentAttempt = PaymentAttempt(
            id = 10,
            orderId = 1,
            merchantOrderId = "order-1",
            checkoutKey = "checkout-1",
            amount = 15000,
            status = PaymentStatus.PENDING,
        )
        val service = PaymentApplicationService(paymentAttemptRepository, pgClient, checkoutKeyStore)

        given(paymentAttemptRepository.findById(paymentAttempt.id)).willReturn(Optional.of(paymentAttempt))

        assertThrows<IllegalStateException> {
            service.approvePaymentAttempt(paymentAttempt.id)
        }

        verify(pgClient, never()).approve(paymentAttempt.id, paymentAttempt.merchantOrderId, paymentAttempt.amount)
    }

    @Test
    fun `동시성 충돌로 ready에서 pending 전이가 실패하면 승인 요청을 거부한다`() {
        val paymentAttempt = PaymentAttempt(
            id = 10,
            orderId = 1,
            merchantOrderId = "order-1",
            checkoutKey = "checkout-1",
            amount = 15000,
            status = PaymentStatus.READY,
        )
        val service = PaymentApplicationService(paymentAttemptRepository, pgClient, checkoutKeyStore)

        given(paymentAttemptRepository.findById(paymentAttempt.id)).willReturn(Optional.of(paymentAttempt))
        given(pgClient.approve(paymentAttempt.id, paymentAttempt.merchantOrderId, paymentAttempt.amount)).willReturn(
            PgApproveResult(pgTransactionId = "pg-tx-1", webhookSecret = "secret-1", outcome = PgApproveOutcome.PENDING),
        )
        doReturn(0).`when`(paymentAttemptRepository)
            .updateStatusAndPgTransactionIdAndWebhookSecretIfCurrentStatus(paymentAttempt.id, PaymentStatus.READY, PaymentStatus.PENDING, "pg-tx-1", "secret-1")

        assertThrows<IllegalStateException> {
            service.approvePaymentAttempt(paymentAttempt.id)
        }
    }

    @Test
    fun `비즈니스 거절이면 declined 상태로 남긴다`() {
        val paymentAttempt = PaymentAttempt(
            id = 10,
            orderId = 1,
            merchantOrderId = "order-1",
            checkoutKey = "checkout-1",
            amount = 15000,
            status = PaymentStatus.READY,
        )
        val service = PaymentApplicationService(paymentAttemptRepository, pgClient, checkoutKeyStore)

        given(paymentAttemptRepository.findById(paymentAttempt.id)).willReturn(Optional.of(paymentAttempt))
        given(pgClient.approve(paymentAttempt.id, paymentAttempt.merchantOrderId, paymentAttempt.amount)).willReturn(
            PgApproveResult(pgTransactionId = "pg-tx-1", webhookSecret = "secret-1", outcome = PgApproveOutcome.DECLINED),
        )
        doReturn(1).`when`(paymentAttemptRepository)
            .updateStatusAndPgTransactionIdAndWebhookSecretIfCurrentStatus(paymentAttempt.id, PaymentStatus.READY, PaymentStatus.DECLINED, "pg-tx-1", "secret-1")

        val result = service.approvePaymentAttempt(paymentAttempt.id)

        assertEquals(PaymentStatus.DECLINED, result.status)
        assertEquals("pg-tx-1", result.pgTransactionId)
    }
}
