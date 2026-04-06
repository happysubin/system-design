package com.paymentlab.payment.application

import com.paymentlab.payment.api.dto.CreatePaymentAttemptRequest
import com.paymentlab.payment.domain.PaymentAttempt
import com.paymentlab.payment.domain.PaymentStatus
import com.paymentlab.payment.infrastructure.persistence.PaymentAttemptRepository
import com.paymentlab.payment.infrastructure.pg.StubPgClient
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@ExtendWith(MockitoExtension::class)
class PaymentApplicationServiceTest {

    @Mock
    lateinit var paymentAttemptRepository: PaymentAttemptRepository

    @Test
    fun `기존 결제 시도가 없으면 외부 주문 정보를 기준으로 결제 시도를 생성한다`() {
        val paymentApplicationService = PaymentApplicationService(paymentAttemptRepository, StubPgClient())

        given(paymentAttemptRepository.findByIdempotencyKey("idem-1")).willReturn(null)
        given(paymentAttemptRepository.save(org.mockito.ArgumentMatchers.any(PaymentAttempt::class.java))).willAnswer { invocation ->
            val saved = invocation.getArgument(0, PaymentAttempt::class.java)
            saved.id = 10
            saved
        }

        val result = paymentApplicationService.createPaymentAttempt(
            CreatePaymentAttemptRequest(
                orderId = 1,
                merchantOrderId = "order-1",
                amount = 15000,
                idempotencyKey = "idem-1",
            ),
        )

        assertNotNull(result)
        val captor = ArgumentCaptor.forClass(PaymentAttempt::class.java)
        verify(paymentAttemptRepository).save(captor.capture())
        assertEquals(1, captor.value.orderId)
        assertEquals("order-1", captor.value.merchantOrderId)
        assertEquals(15000, captor.value.amount)
        assertEquals("idem-1", captor.value.idempotencyKey)
        assertEquals(PaymentStatus.READY, captor.value.status)
        assertEquals(1, result.orderId)
        assertEquals(PaymentStatus.READY, result.status)
    }

    @Test
    fun `같은 idempotency key가 이미 있으면 기존 결제 시도를 그대로 반환한다`() {
        val paymentApplicationService = PaymentApplicationService(paymentAttemptRepository, StubPgClient())
        val existingAttempt = PaymentAttempt(
            id = 10,
            orderId = 1,
            merchantOrderId = "order-1",
            idempotencyKey = "idem-1",
            amount = 15000,
            status = PaymentStatus.READY,
        )

        given(paymentAttemptRepository.findByIdempotencyKey("idem-1")).willReturn(existingAttempt)

        val result = paymentApplicationService.createPaymentAttempt(
            CreatePaymentAttemptRequest(
                orderId = 1,
                merchantOrderId = "order-1",
                amount = 15000,
                idempotencyKey = "idem-1",
            ),
        )

        assertEquals(existingAttempt.id, result.paymentAttemptId)
        assertEquals(1, result.orderId)
        assertEquals(PaymentStatus.READY, result.status)
        verify(paymentAttemptRepository, never()).save(org.mockito.ArgumentMatchers.any(PaymentAttempt::class.java))
    }
}
