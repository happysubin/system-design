package com.paymentlab.inventory.application

import com.paymentlab.inventory.domain.InventoryHold
import com.paymentlab.inventory.infrastructure.persistence.InventoryHoldItemRepository
import com.paymentlab.inventory.domain.SkuStock
import com.paymentlab.inventory.domain.InventoryHoldStatus
import com.paymentlab.inventory.infrastructure.persistence.InventoryHoldRepository
import com.paymentlab.order.domain.OrderItem
import com.paymentlab.order.infrastructure.persistence.OrderItemRepository
import com.paymentlab.payment.domain.Order
import com.paymentlab.payment.infrastructure.persistence.OrderRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@SpringBootTest
class PendingInventoryHoldExpirationSchedulerTest {

    @Autowired
    lateinit var pendingInventoryHoldExpirationScheduler: PendingInventoryHoldExpirationScheduler

    @Autowired
    lateinit var inventoryHoldApplicationService: InventoryHoldApplicationService

    @Autowired
    lateinit var inventoryHoldRepository: InventoryHoldRepository

    @Autowired
    lateinit var inventoryHoldItemRepository: InventoryHoldItemRepository

    @Autowired
    lateinit var skuStockRepository: com.paymentlab.inventory.infrastructure.persistence.SkuStockRepository

    @Autowired
    lateinit var orderRepository: OrderRepository

    @Autowired
    lateinit var orderItemRepository: OrderItemRepository

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun setUp() {
        inventoryHoldItemRepository.deleteAll()
        skuStockRepository.deleteAll()
        orderItemRepository.deleteAll()
        inventoryHoldRepository.deleteAll()
        orderRepository.deleteAll()
        jdbcTemplate.execute("ALTER TABLE inventory_hold_items ALTER COLUMN id RESTART WITH 1")
        jdbcTemplate.execute("ALTER TABLE inventory_holds ALTER COLUMN id RESTART WITH 1")
        jdbcTemplate.execute("ALTER TABLE sku_stocks ALTER COLUMN id RESTART WITH 1")
        jdbcTemplate.execute("ALTER TABLE order_items ALTER COLUMN id RESTART WITH 1")
        jdbcTemplate.execute("ALTER TABLE orders ALTER COLUMN id RESTART WITH 1")
    }

    @Test
    fun `만료된 HELD 홀드를 EXPIRED로 바꾸고 reserveOrReuse가 새 홀드를 만든다`() {
        val order = orderRepository.saveAndFlush(
            Order(
                merchantOrderId = "merchant-order-1001",
                amount = 14000,
                items = mutableListOf(
                    OrderItem(
                        skuId = 101L,
                        quantity = 2,
                        unitPrice = 7000,
                    ),
                ),
            ),
        )
        skuStockRepository.saveAndFlush(SkuStock(skuId = 101L, onHand = 10, reserved = 2))

        val expiredHold = inventoryHoldRepository.saveAndFlush(
            heldHold(
                orderId = order.id,
                expiresAt = LocalDateTime.now().minusMinutes(1),
                createdAt = LocalDateTime.now().minusMinutes(6),
            ),
        )

        pendingInventoryHoldExpirationScheduler.expirePendingInventoryHolds()

        val expiredStatus = inventoryHoldRepository.findById(expiredHold.id).orElseThrow().status
        val newHold = inventoryHoldApplicationService.reserveOrReuse(order.id)

        assertEquals(InventoryHoldStatus.EXPIRED, expiredStatus)
        assertNotEquals(expiredHold.id, newHold.id)
        assertEquals(InventoryHoldStatus.HELD, newHold.status)
        assertEquals(2, inventoryHoldRepository.count())
    }

    private fun heldHold(
        orderId: Long,
        expiresAt: LocalDateTime,
        createdAt: LocalDateTime,
    ) = InventoryHold(
        orderId = orderId,
        status = InventoryHoldStatus.HELD,
        expiresAt = expiresAt,
        createdAt = createdAt,
    )
}
