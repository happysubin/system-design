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
import org.mockito.Mockito.mock
import org.mockito.junit.jupiter.MockitoExtension
import java.util.Optional
import kotlin.test.assertEquals

@ExtendWith(MockitoExtension::class)
class PaymentReconciliationApplicationServiceTest {

    @Mock
    lateinit var paymentAttemptRepository: PaymentAttemptRepository

    @Test
    fun `pending 결제 시도 조회는 현재 상태가 pending일 때만 허용한다`() {
        val paymentAttempt = pendingAttempt()
        val service = PaymentApplicationService(paymentAttemptRepository, mock(), mock())

        given(paymentAttemptRepository.findById(paymentAttempt.id)).willReturn(Optional.of(paymentAttempt))

        val result = service.loadPendingPaymentAttempt(paymentAttempt.id)

        assertEquals(paymentAttempt.id, result.id)
        assertEquals(PaymentStatus.PENDING, result.status)
    }

    @Test
    fun `pending이 아닌 결제 시도는 재확정할 수 없다`() {
        val paymentAttempt = pendingAttempt().apply { status = PaymentStatus.DONE }
        val service = PaymentApplicationService(paymentAttemptRepository, mock(), mock())

        given(paymentAttemptRepository.findById(paymentAttempt.id)).willReturn(Optional.of(paymentAttempt))

        assertThrows<IllegalStateException> {
            service.loadPendingPaymentAttempt(paymentAttempt.id)
        }
    }

    @Test
    fun `재확정 결과 반영은 pending에서 done으로 바꿀 수 있다`() {
        val paymentAttempt = pendingAttempt()
        val service = PaymentApplicationService(paymentAttemptRepository, mock(), mock())

        val successService = PaymentApplicationService(paymentAttemptRepository, mock(), mock())
        doReturn(1).`when`(paymentAttemptRepository)
            .updateStatusIfCurrentStatus(paymentAttempt.id, PaymentStatus.PENDING, PaymentStatus.DONE)

        val result = successService.applyReconcileResult(paymentAttempt.id, "SUCCESS")

        assertEquals(paymentAttempt.id, result.paymentAttemptId)
        assertEquals(PaymentStatus.DONE, result.status)
    }

    @Test
    fun `재확정 결과 반영 시 다른 경로가 먼저 확정했으면 상태 전이를 중단한다`() {
        val paymentAttempt = pendingAttempt()
        val service = PaymentApplicationService(paymentAttemptRepository, mock(), mock())

        doReturn(0).`when`(paymentAttemptRepository)
            .updateStatusIfCurrentStatus(paymentAttempt.id, PaymentStatus.PENDING, PaymentStatus.DONE)

        assertThrows<IllegalStateException> {
            service.applyReconcileResult(paymentAttempt.id, "SUCCESS")
        }
    }

    @Test
    fun `pending 결제에 pgTransactionId가 없으면 merchantOrderId 기준으로 재확정 대상을 조회할 수 있다`() {
        val paymentAttempt = pendingAttempt().apply { pgTransactionId = null }
        val service = PaymentApplicationService(paymentAttemptRepository, mock(), mock())

        given(paymentAttemptRepository.findById(paymentAttempt.id)).willReturn(Optional.of(paymentAttempt))

        val result = service.loadPendingPaymentAttempt(paymentAttempt.id)

        assertEquals("order-1", result.merchantOrderId)
        assertEquals(PaymentStatus.PENDING, result.status)
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
