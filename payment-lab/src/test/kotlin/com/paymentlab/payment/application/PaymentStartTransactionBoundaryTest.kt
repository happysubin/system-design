package com.paymentlab.payment.application

import com.paymentlab.payment.api.dto.CreatePaymentAttemptRequest
import com.paymentlab.payment.domain.PaymentStatus
import com.paymentlab.payment.infrastructure.persistence.PaymentAttemptRepository
import com.paymentlab.payment.infrastructure.pg.PgClient
import com.paymentlab.payment.infrastructure.redis.CheckoutKeyStore
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.mockito.BDDMockito.given
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@SpringBootTest
class PaymentStartTransactionBoundaryTest {

    @Autowired
    lateinit var paymentFacade: PaymentFacade

    @Autowired
    lateinit var paymentAttemptRepository: PaymentAttemptRepository

    @MockitoBean
    lateinit var pgClient: PgClient

    @MockitoBean
    lateinit var checkoutKeyStore: CheckoutKeyStore

    @BeforeEach
    fun setUp() {
        paymentAttemptRepository.deleteAll()
    }

    @Test
    fun `PG 승인 호출이 실패해도 결제 시도 저장은 롤백되지 않는다`() {
        given(checkoutKeyStore.consumeIfValid("checkout-1", 1, "order-1", 15000)).willReturn(true)
        given(pgClient.approve(1, "order-1", 15000)).willThrow(RuntimeException("pg 500"))

        assertThrows<RuntimeException> {
            paymentFacade.startPayment(
                CreatePaymentAttemptRequest(
                    orderId = 1,
                    merchantOrderId = "order-1",
                    amount = 15000,
                    checkoutKey = "checkout-1",
                ),
            )
        }

        val savedAttempt = paymentAttemptRepository.findByCheckoutKey("checkout-1")
        assertNotNull(savedAttempt)
        assertEquals(PaymentStatus.PENDING, savedAttempt.status)
        assertEquals("order-1", savedAttempt.merchantOrderId)
        assertEquals(15000, savedAttempt.amount)
    }

    @Test
    fun `PG 재조회 호출이 실패해도 pending 결제 상태는 롤백되지 않는다`() {
        paymentAttemptRepository.saveAndFlush(
            com.paymentlab.payment.domain.PaymentAttempt(
                orderId = 2,
                merchantOrderId = "order-2",
                checkoutKey = "checkout-2",
                pgTransactionId = "pg-tx-2",
                amount = 20000,
                status = PaymentStatus.PENDING,
            ),
        )
        given(pgClient.query("pg-tx-2")).willThrow(RuntimeException("pg timeout"))

        assertThrows<RuntimeException> {
            paymentFacade.reconcilePaymentAttempt(1)
        }

        val savedAttempt = paymentAttemptRepository.findByPgTransactionId("pg-tx-2")
        assertNotNull(savedAttempt)
        assertEquals(PaymentStatus.PENDING, savedAttempt.status)
    }
}
