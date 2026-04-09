package com.paymentlab.order.infrastructure.persistence

import com.paymentlab.order.domain.OrderItem
import com.paymentlab.payment.domain.Order
import com.paymentlab.payment.infrastructure.persistence.OrderRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@SpringBootTest
@Transactional
class OrderItemPersistenceTest {

    @Autowired
    lateinit var orderRepository: OrderRepository

    @Autowired
    lateinit var orderItemRepository: OrderItemRepository

    @BeforeEach
    fun setUp() {
        orderItemRepository.deleteAll()
        orderRepository.deleteAll()
    }

    @Test
    fun `order를 저장하고 다시 읽으면 여러 order item의 skuId quantity unitPrice가 유지된다`() {
        val savedOrder = orderRepository.saveAndFlush(
            Order(
                merchantOrderId = "merchant-order-1",
                amount = 42000,
                items = mutableListOf(
                    OrderItem(
                        skuId = 101L,
                        quantity = 2,
                        unitPrice = 7000,
                    ),
                    OrderItem(
                        skuId = 202L,
                        quantity = 4,
                        unitPrice = 7000,
                    ),
                ),
            ),
        )

        val reloadedOrder = orderRepository.findById(savedOrder.id).orElse(null)

        assertNotNull(reloadedOrder)
        assertEquals(2, reloadedOrder.items.size)
        assertEquals(listOf(101L, 202L), reloadedOrder.items.map { it.skuId })
        assertEquals(listOf(2, 4), reloadedOrder.items.map { it.quantity })
        assertEquals(listOf(7000L, 7000L), reloadedOrder.items.map { it.unitPrice })
    }
}
