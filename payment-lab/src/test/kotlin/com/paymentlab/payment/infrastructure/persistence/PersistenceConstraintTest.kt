package com.paymentlab.payment.infrastructure.persistence

import com.paymentlab.payment.domain.Order
import com.paymentlab.payment.domain.PaymentAttempt
import com.paymentlab.payment.domain.PaymentStatus
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException

@SpringBootTest
class PersistenceConstraintTest {

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
    fun `같은 merchantOrderId 주문은 두 번 저장할 수 없다`() {
        orderRepository.saveAndFlush(
            Order(
                merchantOrderId = "order-1",
                amount = 15000,
            ),
        )

        assertThrows<DataIntegrityViolationException> {
            orderRepository.saveAndFlush(
                Order(
                    merchantOrderId = "order-1",
                    amount = 15000,
                ),
            )
        }
    }

    @Test
    fun `같은 checkoutKey 결제 시도는 두 번 저장할 수 없다`() {
        paymentAttemptRepository.saveAndFlush(
            PaymentAttempt(
                orderId = 1,
                merchantOrderId = "order-1",
                checkoutKey = "checkout-1",
                inventoryHoldId = null,
                amount = 15000,
                status = PaymentStatus.READY,
            ),
        )

        assertThrows<DataIntegrityViolationException> {
            paymentAttemptRepository.saveAndFlush(
                PaymentAttempt(
                    orderId = 1,
                    merchantOrderId = "order-1",
                    checkoutKey = "checkout-1",
                    inventoryHoldId = null,
                    amount = 15000,
                    status = PaymentStatus.READY,
                ),
            )
        }
    }

    @Test
    fun `같은 pgTransactionId 결제 시도는 두 번 저장할 수 없다`() {
        paymentAttemptRepository.saveAndFlush(
            PaymentAttempt(
                orderId = 1,
                merchantOrderId = "order-1",
                checkoutKey = "checkout-1",
                pgTransactionId = "pg-tx-1",
                inventoryHoldId = null,
                amount = 15000,
                status = PaymentStatus.PENDING,
            ),
        )

        assertThrows<DataIntegrityViolationException> {
            paymentAttemptRepository.saveAndFlush(
                PaymentAttempt(
                    orderId = 2,
                    merchantOrderId = "order-2",
                    checkoutKey = "checkout-2",
                    pgTransactionId = "pg-tx-1",
                    inventoryHoldId = null,
                    amount = 17000,
                    status = PaymentStatus.PENDING,
                ),
            )
        }
    }
}
