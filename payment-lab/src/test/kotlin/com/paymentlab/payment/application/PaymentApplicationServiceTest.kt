package com.paymentlab.payment.application

import com.paymentlab.payment.api.dto.CreatePaymentAttemptRequest
import com.paymentlab.payment.domain.PaymentAttempt
import com.paymentlab.payment.domain.PaymentStatus
import com.paymentlab.payment.infrastructure.persistence.PaymentAttemptRepository
import com.paymentlab.payment.infrastructure.redis.CheckoutKeyStore
import com.paymentlab.payment.infrastructure.pg.StubPgClient
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@ExtendWith(MockitoExtension::class)
class PaymentApplicationServiceTest {

    @Mock
    lateinit var paymentAttemptRepository: PaymentAttemptRepository

    @Mock
    lateinit var checkoutKeyStore: CheckoutKeyStore

    @Mock
    lateinit var paymentFinalizationService: PaymentFinalizationService

    @Test
    fun `기존 결제 시도가 없으면 외부 주문 정보를 기준으로 결제 시도를 생성한다`() {
        val paymentApplicationService = PaymentApplicationService(paymentAttemptRepository, StubPgClient(), checkoutKeyStore, paymentFinalizationService)

        given(checkoutKeyStore.consumeIfValid("checkout-1", 1, "order-1", 15000)).willReturn(true)
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
                checkoutKey = "checkout-1",
            ),
        )

        assertNotNull(result)
        val captor = ArgumentCaptor.forClass(PaymentAttempt::class.java)
        verify(paymentAttemptRepository).save(captor.capture())
        assertEquals(1, captor.value.orderId)
        assertEquals("order-1", captor.value.merchantOrderId)
        assertEquals(15000, captor.value.amount)
        assertEquals("checkout-1", captor.value.checkoutKey)
        assertEquals(PaymentStatus.READY, captor.value.status)
        verify(checkoutKeyStore).consumeIfValid("checkout-1", 1, "order-1", 15000)
        assertEquals(1, result.orderId)
        assertEquals(PaymentStatus.READY, result.status)
    }

    @Test
    fun `같은 checkout key로 다시 요청하면 기존 결제 시도를 재반환한다`() {
        val paymentApplicationService = PaymentApplicationService(paymentAttemptRepository, StubPgClient(), checkoutKeyStore, paymentFinalizationService)
        val existingAttempt = PaymentAttempt(
            id = 10,
            orderId = 1,
            merchantOrderId = "order-1",
            checkoutKey = "checkout-1",
            amount = 15000,
            status = PaymentStatus.READY,
        )
        given(checkoutKeyStore.consumeIfValid("checkout-1", 1, "order-1", 15000)).willReturn(true)
        given(paymentAttemptRepository.findByCheckoutKey("checkout-1")).willReturn(null, existingAttempt)
        given(paymentAttemptRepository.save(org.mockito.ArgumentMatchers.any(PaymentAttempt::class.java))).willAnswer { invocation ->
            val saved = invocation.getArgument(0, PaymentAttempt::class.java)
            saved.id = 10
            saved
        }

        val firstResult = paymentApplicationService.createPaymentAttempt(
            CreatePaymentAttemptRequest(
                orderId = 1,
                merchantOrderId = "order-1",
                amount = 15000,
                checkoutKey = "checkout-1",
            ),
        )

        assertEquals(10, firstResult.paymentAttemptId)
        assertEquals(PaymentStatus.READY, firstResult.status)

        val secondResult = paymentApplicationService.createPaymentAttempt(
            CreatePaymentAttemptRequest(
                orderId = 1,
                merchantOrderId = "order-1",
                amount = 15000,
                checkoutKey = "checkout-1",
            ),
        )

        assertEquals(10, secondResult.paymentAttemptId)
        assertEquals(PaymentStatus.READY, secondResult.status)
        verify(checkoutKeyStore).consumeIfValid("checkout-1", 1, "order-1", 15000)
    }

    @Test
    fun `유효하지 않은 checkout key면 결제 시도를 생성하지 않는다`() {
        val paymentApplicationService = PaymentApplicationService(paymentAttemptRepository, StubPgClient(), checkoutKeyStore, paymentFinalizationService)

        given(checkoutKeyStore.consumeIfValid("checkout-1", 1, "order-1", 15000)).willReturn(false)

        kotlin.test.assertFailsWith<IllegalArgumentException> {
            paymentApplicationService.createPaymentAttempt(
                CreatePaymentAttemptRequest(
                    orderId = 1,
                    merchantOrderId = "order-1",
                    amount = 15000,
                    checkoutKey = "checkout-1",
                ),
            )
        }

        verify(paymentAttemptRepository, never()).save(org.mockito.ArgumentMatchers.any(PaymentAttempt::class.java))
    }
}
