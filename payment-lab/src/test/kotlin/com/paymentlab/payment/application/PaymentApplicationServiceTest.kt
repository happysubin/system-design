package com.paymentlab.payment.application

import com.paymentlab.payment.api.dto.CreatePaymentAttemptRequest
import com.paymentlab.payment.domain.Order
import com.paymentlab.payment.domain.PaymentAttempt
import com.paymentlab.payment.domain.PaymentStatus
import com.paymentlab.payment.infrastructure.persistence.OrderRepository
import com.paymentlab.payment.infrastructure.persistence.PaymentAttemptRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@SpringBootTest
@ActiveProfiles("test")
class PaymentApplicationServiceTest {

    @Autowired
    lateinit var paymentApplicationService: PaymentApplicationService

    @Autowired
    lateinit var orderRepository: OrderRepository

    @Autowired
    lateinit var paymentAttemptRepository: PaymentAttemptRepository

    @BeforeEach
    fun setUp() {
        paymentAttemptRepository.deleteAll()
        orderRepository.deleteAll()
    }

    @Test
    fun `기존 결제 시도가 없으면 주문 기준으로 결제 시도를 생성한다`() {
        val order = orderRepository.save(
            Order(
            merchantOrderId = "order-1",
            amount = 15000,
            ),
        )

        val result = paymentApplicationService.createPaymentAttempt(
            CreatePaymentAttemptRequest(
                orderId = order.id,
                idempotencyKey = "idem-1",
            ),
        )

        val savedAttempt = paymentAttemptRepository.findById(result.paymentAttemptId).orElse(null)

        assertNotNull(savedAttempt)
        assertEquals(order.id, savedAttempt.order.id)
        assertEquals("idem-1", savedAttempt.idempotencyKey)
        assertEquals(order.amount, savedAttempt.amount)
        assertEquals(PaymentStatus.READY, savedAttempt.status)
        assertEquals(order.id, result.orderId)
        assertEquals(PaymentStatus.READY, result.status)
    }

    @Test
    fun `같은 idempotency key가 이미 있으면 기존 결제 시도를 그대로 반환한다`() {
        val order = orderRepository.save(
            Order(
            merchantOrderId = "order-1",
            amount = 15000,
            ),
        )
        val existingAttempt = paymentAttemptRepository.save(
            PaymentAttempt(
                order = order,
                idempotencyKey = "idem-1",
                amount = order.amount,
                status = PaymentStatus.READY,
            ),
        )

        val result = paymentApplicationService.createPaymentAttempt(
            CreatePaymentAttemptRequest(
                orderId = order.id,
                idempotencyKey = "idem-1",
            ),
        )

        assertEquals(existingAttempt.id, result.paymentAttemptId)
        assertEquals(order.id, result.orderId)
        assertEquals(PaymentStatus.READY, result.status)
        assertEquals(1, paymentAttemptRepository.count())
    }
}
