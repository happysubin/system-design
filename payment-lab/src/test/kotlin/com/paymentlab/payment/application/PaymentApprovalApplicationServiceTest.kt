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
import org.mockito.junit.jupiter.MockitoExtension
import java.util.Optional
import kotlin.test.assertEquals

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
        given(pgClient.approve(paymentAttempt.id, paymentAttempt.merchantOrderId, paymentAttempt.amount)).willReturn("pg-tx-1")

        val result = service.approvePaymentAttempt(paymentAttempt.id)

        assertEquals(paymentAttempt.id, result.paymentAttemptId)
        assertEquals(PaymentStatus.PENDING, result.status)
        assertEquals("pg-tx-1", result.pgTransactionId)
        assertEquals(PaymentStatus.PENDING, paymentAttempt.status)
        assertEquals("pg-tx-1", paymentAttempt.pgTransactionId)
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
}
