package com.paymentlab.payment.application

import com.paymentlab.payment.domain.PaymentAttempt
import com.paymentlab.payment.domain.PaymentStatus
import com.paymentlab.payment.infrastructure.persistence.PaymentAttemptRepository
import com.paymentlab.payment.infrastructure.pg.PgClient
import com.paymentlab.payment.infrastructure.redis.CheckoutKeyStore
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.doReturn
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

    @Mock
    lateinit var checkoutKeyStore: CheckoutKeyStore

    @Test
    fun `pending 결제 시도를 PG 조회 결과가 성공이면 done으로 확정한다`() {
        val paymentAttempt = pendingAttempt()
        val service = PaymentApplicationService(paymentAttemptRepository, pgClient, checkoutKeyStore)

        given(paymentAttemptRepository.findById(paymentAttempt.id)).willReturn(Optional.of(paymentAttempt))
        given(pgClient.query(paymentAttempt.pgTransactionId!!)).willReturn("SUCCESS")
        doReturn(1).`when`(paymentAttemptRepository)
            .updateStatusIfCurrentStatus(paymentAttempt.id, PaymentStatus.PENDING, PaymentStatus.DONE)

        val result = service.reconcilePaymentAttempt(paymentAttempt.id)

        assertEquals(paymentAttempt.id, result.paymentAttemptId)
        assertEquals(PaymentStatus.DONE, result.status)
        verify(pgClient).query("pg-tx-1")
    }

    @Test
    fun `pending 결제 시도를 PG 조회 결과가 실패면 failed로 확정한다`() {
        val paymentAttempt = pendingAttempt()
        val service = PaymentApplicationService(paymentAttemptRepository, pgClient, checkoutKeyStore)

        given(paymentAttemptRepository.findById(paymentAttempt.id)).willReturn(Optional.of(paymentAttempt))
        given(pgClient.query(paymentAttempt.pgTransactionId!!)).willReturn("FAIL")
        doReturn(1).`when`(paymentAttemptRepository)
            .updateStatusIfCurrentStatus(paymentAttempt.id, PaymentStatus.PENDING, PaymentStatus.FAILED)

        val result = service.reconcilePaymentAttempt(paymentAttempt.id)

        assertEquals(PaymentStatus.FAILED, result.status)
        verify(pgClient).query("pg-tx-1")
    }

    @Test
    fun `pending이 아닌 결제 시도는 재확정할 수 없다`() {
        val paymentAttempt = pendingAttempt().apply { status = PaymentStatus.DONE }
        val service = PaymentApplicationService(paymentAttemptRepository, pgClient, checkoutKeyStore)

        given(paymentAttemptRepository.findById(paymentAttempt.id)).willReturn(Optional.of(paymentAttempt))

        assertThrows<IllegalStateException> {
            service.reconcilePaymentAttempt(paymentAttempt.id)
        }

        verify(pgClient, never()).query("pg-tx-1")
    }

    @Test
    fun `재확정 시점에 이미 다른 경로가 최종 확정했으면 상태 전이를 중단한다`() {
        val paymentAttempt = pendingAttempt()
        val service = PaymentApplicationService(paymentAttemptRepository, pgClient, checkoutKeyStore)

        given(paymentAttemptRepository.findById(paymentAttempt.id)).willReturn(Optional.of(paymentAttempt))
        given(pgClient.query(paymentAttempt.pgTransactionId!!)).willReturn("SUCCESS")
        doReturn(0).`when`(paymentAttemptRepository)
            .updateStatusIfCurrentStatus(paymentAttempt.id, PaymentStatus.PENDING, PaymentStatus.DONE)

        assertThrows<IllegalStateException> {
            service.reconcilePaymentAttempt(paymentAttempt.id)
        }
    }

    private fun pendingAttempt(): PaymentAttempt = PaymentAttempt(
        id = 10,
        orderId = 1,
        merchantOrderId = "order-1",
        checkoutKey = "checkout-1",
        pgTransactionId = "pg-tx-1",
        amount = 15000,
        status = PaymentStatus.PENDING,
    )
}
