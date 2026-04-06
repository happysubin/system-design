package com.paymentlab.payment.application

import com.paymentlab.payment.domain.PaymentAttempt
import com.paymentlab.payment.domain.PaymentStatus
import com.paymentlab.payment.infrastructure.persistence.PaymentAttemptRepository
import com.paymentlab.payment.infrastructure.pg.PgClient
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.never
import org.mockito.junit.jupiter.MockitoExtension
import java.util.Optional
import kotlin.test.assertEquals

@ExtendWith(MockitoExtension::class)
class PaymentReconciliationApplicationServiceTest {

    @Mock
    lateinit var paymentAttemptRepository: PaymentAttemptRepository

    @Mock
    lateinit var pgClient: PgClient

    @Test
    fun `pending 결제 시도를 PG 조회 결과가 성공이면 done으로 확정한다`() {
        val paymentAttempt = pendingAttempt()
        val service = PaymentApplicationService(paymentAttemptRepository, pgClient)

        given(paymentAttemptRepository.findById(paymentAttempt.id)).willReturn(Optional.of(paymentAttempt))
        given(pgClient.query(paymentAttempt.pgTransactionId!!)).willReturn("SUCCESS")

        val result = service.reconcilePaymentAttempt(paymentAttempt.id)

        assertEquals(paymentAttempt.id, result.paymentAttemptId)
        assertEquals(PaymentStatus.DONE, result.status)
        assertEquals(PaymentStatus.DONE, paymentAttempt.status)
        verify(pgClient).query("pg-tx-1")
    }

    @Test
    fun `pending 결제 시도를 PG 조회 결과가 실패면 failed로 확정한다`() {
        val paymentAttempt = pendingAttempt()
        val service = PaymentApplicationService(paymentAttemptRepository, pgClient)

        given(paymentAttemptRepository.findById(paymentAttempt.id)).willReturn(Optional.of(paymentAttempt))
        given(pgClient.query(paymentAttempt.pgTransactionId!!)).willReturn("FAIL")

        val result = service.reconcilePaymentAttempt(paymentAttempt.id)

        assertEquals(PaymentStatus.FAILED, result.status)
        assertEquals(PaymentStatus.FAILED, paymentAttempt.status)
        verify(pgClient).query("pg-tx-1")
    }

    @Test
    fun `pending이 아닌 결제 시도는 재확정할 수 없다`() {
        val paymentAttempt = pendingAttempt().apply { status = PaymentStatus.DONE }
        val service = PaymentApplicationService(paymentAttemptRepository, pgClient)

        given(paymentAttemptRepository.findById(paymentAttempt.id)).willReturn(Optional.of(paymentAttempt))

        assertThrows<IllegalStateException> {
            service.reconcilePaymentAttempt(paymentAttempt.id)
        }

        verify(pgClient, never()).query("pg-tx-1")
    }

    private fun pendingAttempt(): PaymentAttempt = PaymentAttempt(
        id = 10,
        orderId = 1,
        merchantOrderId = "order-1",
        idempotencyKey = "idem-1",
        pgTransactionId = "pg-tx-1",
        amount = 15000,
        status = PaymentStatus.PENDING,
    )
}
