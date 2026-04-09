package com.paymentlab.inventory.application

import com.paymentlab.inventory.domain.InventoryHold
import com.paymentlab.inventory.domain.InventoryHoldItem
import com.paymentlab.inventory.domain.InventoryHoldStatus
import com.paymentlab.inventory.domain.SkuStock
import com.paymentlab.inventory.infrastructure.persistence.InventoryHoldItemRepository
import com.paymentlab.inventory.infrastructure.persistence.InventoryHoldRepository
import com.paymentlab.inventory.infrastructure.persistence.SkuStockRepository
import com.paymentlab.order.domain.OrderItem
import com.paymentlab.order.infrastructure.persistence.OrderItemRepository
import com.paymentlab.payment.domain.Order
import com.paymentlab.payment.infrastructure.persistence.OrderRepository
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.BDDMockito.given
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertFailsWith

@SpringBootTest(properties = ["inventory.hold.ttl-seconds=300"])
class InventoryHoldApplicationServiceTest {

    @Autowired
    lateinit var inventoryHoldApplicationService: InventoryHoldApplicationService

    @Autowired
    lateinit var inventoryHoldRepository: InventoryHoldRepository

    @Autowired
    lateinit var inventoryHoldItemRepository: InventoryHoldItemRepository

    @Autowired
    lateinit var skuStockRepository: SkuStockRepository

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
        jdbcTemplate.execute("ALTER TABLE inventory_holds ALTER COLUMN id RESTART WITH 1")
        jdbcTemplate.execute("ALTER TABLE inventory_hold_items ALTER COLUMN id RESTART WITH 1")
        jdbcTemplate.execute("ALTER TABLE sku_stocks ALTER COLUMN id RESTART WITH 1")
        jdbcTemplate.execute("ALTER TABLE order_items ALTER COLUMN id RESTART WITH 1")
        jdbcTemplate.execute("ALTER TABLE orders ALTER COLUMN id RESTART WITH 1")
    }

    @Test
    fun `새 홀드를 만들면 order item 기준으로 sku stock을 예약하고 hold item을 만든다`() {
        val order = orderRepository.saveAndFlush(
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
        skuStockRepository.saveAllAndFlush(
            listOf(
                SkuStock(skuId = 101L, onHand = 10, reserved = 1),
                SkuStock(skuId = 202L, onHand = 8, reserved = 0),
            ),
        )

        val result = inventoryHoldApplicationService.reserveOrReuse(order.id)

        assertEquals(1L, result.id)
        assertEquals(InventoryHoldStatus.HELD, result.status)
        assertEquals(listOf(3, 4), skuStockRepository.findAll().sortedBy { it.skuId }.map { it.reserved })
        val holdItems = inventoryHoldItemRepository.findAllByHoldIdOrderByIdAsc(result.id)
        assertEquals(listOf(101L, 202L), holdItems.map { it.skuId })
        assertEquals(listOf(2, 4), holdItems.map { it.quantity })
    }

    @Test
    fun `활성 홀드를 재사용하면 sku stock을 다시 예약하지 않는다`() {
        val order = orderRepository.saveAndFlush(
            Order(
                merchantOrderId = "merchant-order-2",
                amount = 12000,
                items = mutableListOf(
                    OrderItem(
                        skuId = 101L,
                        quantity = 3,
                        unitPrice = 4000,
                    ),
                ),
            ),
        )
        skuStockRepository.saveAndFlush(SkuStock(skuId = 101L, onHand = 10, reserved = 1))

        val firstHold = inventoryHoldApplicationService.reserveOrReuse(order.id)
        val reservedAfterFirstCall = skuStockRepository.findAll().single().reserved

        val reusedHold = inventoryHoldApplicationService.reserveOrReuse(order.id)

        assertEquals(firstHold.id, reusedHold.id)
        assertEquals(reservedAfterFirstCall, skuStockRepository.findAll().single().reserved)
        assertEquals(1, inventoryHoldRepository.count())
        assertEquals(1, inventoryHoldItemRepository.findAllByHoldIdOrderByIdAsc(firstHold.id).size)
    }

    @Test
    fun `가용 재고가 부족하면 reservation이 실패하고 기존 reserved는 유지된다`() {
        val order = orderRepository.saveAndFlush(
            Order(
                merchantOrderId = "merchant-order-3",
                amount = 21000,
                items = mutableListOf(
                    OrderItem(
                        skuId = 101L,
                        quantity = 2,
                        unitPrice = 3000,
                    ),
                    OrderItem(
                        skuId = 202L,
                        quantity = 5,
                        unitPrice = 3000,
                    ),
                ),
            ),
        )
        skuStockRepository.saveAllAndFlush(
            listOf(
                SkuStock(skuId = 101L, onHand = 10, reserved = 1),
                SkuStock(skuId = 202L, onHand = 5, reserved = 1),
            ),
        )

        val exception = assertFailsWith<IllegalStateException> {
            inventoryHoldApplicationService.reserveOrReuse(order.id)
        }

        assertEquals("insufficient available stock for skuId: 202", exception.message)
        assertEquals(listOf(1, 1), skuStockRepository.findAll().sortedBy { it.skuId }.map { it.reserved })
        assertEquals(0, inventoryHoldRepository.count())
        assertEquals(0, inventoryHoldItemRepository.count())
    }

    @Test
    fun `order item이 없으면 reservation이 실패하고 빈 hold를 만들지 않는다`() {
        val order = orderRepository.saveAndFlush(
            Order(
                merchantOrderId = "merchant-order-4",
                amount = 0,
            ),
        )

        val exception = assertFailsWith<IllegalStateException> {
            inventoryHoldApplicationService.reserveOrReuse(order.id)
        }

        assertEquals("order has no items: ${order.id}", exception.message)
        assertEquals(0, inventoryHoldRepository.count())
        assertEquals(0, inventoryHoldItemRepository.count())
    }

    @Test
    fun `활성 재고 홀드가 없으면 HELD 상태 새 홀드를 만든다`() {
        val order = createReservableOrder(
            merchantOrderId = "merchant-order-create-new-hold",
            skuId = 901L,
            quantity = 2,
        )

        val result = inventoryHoldApplicationService.reserveOrReuse(order.id)

        assertEquals(1L, result.id)
        assertEquals(order.id, result.orderId)
        assertEquals(InventoryHoldStatus.HELD, result.status)
        assertEquals(result.createdAt.plusSeconds(300), result.expiresAt)
    }

    @Test
    fun `같은 주문에 만료되지 않은 HELD 홀이 있으면 기존 홀드를 재사용한다`() {
        val existingHold = inventoryHoldRepository.saveAndFlush(
            hold(
                orderId = 1001L,
                status = InventoryHoldStatus.HELD,
                expiresAt = LocalDateTime.now().plusMinutes(5),
                createdAt = LocalDateTime.now().minusMinutes(1),
            ),
        )

        val result = inventoryHoldApplicationService.reserveOrReuse(1001L)

        assertEquals(existingHold.id, result.id)
        assertEquals(1, inventoryHoldRepository.count())
    }

    @Test
    fun `만료된 HELD 홀드는 재사용하지 않고 새 홀드를 만든다`() {
        val order = createReservableOrder(
            merchantOrderId = "merchant-order-expired-held",
            skuId = 902L,
            quantity = 2,
        )
        val expiredHold = inventoryHoldRepository.saveAndFlush(
            hold(
                orderId = order.id,
                status = InventoryHoldStatus.HELD,
                expiresAt = LocalDateTime.now().minusMinutes(1),
                createdAt = LocalDateTime.now().minusMinutes(6),
            ),
        )

        val result = inventoryHoldApplicationService.reserveOrReuse(order.id)

        assertNotEquals(expiredHold.id, result.id)
        assertEquals(InventoryHoldStatus.HELD, result.status)
        assertEquals(2, inventoryHoldRepository.count())
    }

    @Test
    fun `RELEASED 상태 홀드는 재사용하지 않고 새 홀드를 만든다`() {
        val order = createReservableOrder(
            merchantOrderId = "merchant-order-released-held",
            skuId = 903L,
            quantity = 2,
        )
        val releasedHold = inventoryHoldRepository.saveAndFlush(
            hold(
                orderId = order.id,
                status = InventoryHoldStatus.RELEASED,
                expiresAt = LocalDateTime.now().plusMinutes(5),
                createdAt = LocalDateTime.now().minusMinutes(1),
            ),
        )

        val result = inventoryHoldApplicationService.reserveOrReuse(order.id)

        assertNotEquals(releasedHold.id, result.id)
        assertEquals(InventoryHoldStatus.HELD, result.status)
        assertEquals(2, inventoryHoldRepository.count())
    }

    @Test
    fun `EXPIRED 상태 홀드는 재사용하지 않고 새 홀드를 만든다`() {
        val order = createReservableOrder(
            merchantOrderId = "merchant-order-expired-status",
            skuId = 904L,
            quantity = 2,
        )
        val expiredHold = inventoryHoldRepository.saveAndFlush(
            hold(
                orderId = order.id,
                status = InventoryHoldStatus.EXPIRED,
                expiresAt = LocalDateTime.now().minusMinutes(1),
                createdAt = LocalDateTime.now().minusMinutes(6),
            ),
        )

        val result = inventoryHoldApplicationService.reserveOrReuse(order.id)

        assertNotEquals(expiredHold.id, result.id)
        assertEquals(InventoryHoldStatus.HELD, result.status)
        assertEquals(2, inventoryHoldRepository.count())
    }

    @Test
    fun `만료된 HELD 홀드만 EXPIRED로 바꾼다`() {
        val expiredHold = inventoryHoldRepository.saveAndFlush(
            hold(
                orderId = 1001L,
                status = InventoryHoldStatus.HELD,
                expiresAt = LocalDateTime.now().minusMinutes(2),
                createdAt = LocalDateTime.now().minusMinutes(10),
            ),
        )
        val activeHold = inventoryHoldRepository.saveAndFlush(
            hold(
                orderId = 1002L,
                status = InventoryHoldStatus.HELD,
                expiresAt = LocalDateTime.now().plusMinutes(2),
                createdAt = LocalDateTime.now().minusMinutes(1),
            ),
        )

        inventoryHoldApplicationService.expireStaleHolds()

        assertEquals(InventoryHoldStatus.EXPIRED, inventoryHoldRepository.findById(expiredHold.id).orElseThrow().status)
        assertEquals(InventoryHoldStatus.HELD, inventoryHoldRepository.findById(activeHold.id).orElseThrow().status)
    }

    @Test
    fun `하나의 만료 홀드 업데이트가 실패해도 다음 홀드 만료는 계속 진행한다`() {
        val repository = mock(InventoryHoldRepository::class.java)
        val service = InventoryHoldApplicationService(
            repository,
            mock(InventoryHoldItemRepository::class.java),
            mock(SkuStockRepository::class.java),
            mock(OrderItemRepository::class.java),
            300,
        )
        val failedHold = hold(
            orderId = 1001L,
            status = InventoryHoldStatus.HELD,
            expiresAt = LocalDateTime.now().minusMinutes(3),
            createdAt = LocalDateTime.now().minusMinutes(10),
        )
        failedHold.id = 1L
        val succeedingHold = hold(
            orderId = 1002L,
            status = InventoryHoldStatus.HELD,
            expiresAt = LocalDateTime.now().minusMinutes(2),
            createdAt = LocalDateTime.now().minusMinutes(9),
        )
        succeedingHold.id = 2L
        given(repository.findAllByStatusAndExpiresAtBefore(eqInventoryHoldStatus(InventoryHoldStatus.HELD), anyLocalDateTime()))
            .willReturn(listOf(failedHold, succeedingHold))
        given(repository.findById(eqLong(failedHold.id)))
            .willReturn(java.util.Optional.of(failedHold))
        given(repository.findById(eqLong(succeedingHold.id)))
            .willReturn(java.util.Optional.of(succeedingHold))
        doThrow(RuntimeException("expiration failed")).`when`(repository).updateStatusIfCurrentStatusAndExpired(
            eqLong(failedHold.id),
            eqInventoryHoldStatus(InventoryHoldStatus.HELD),
            eqInventoryHoldStatus(InventoryHoldStatus.EXPIRED),
            anyLocalDateTime(),
        )

        service.expireStaleHolds()

        verify(repository, times(1)).updateStatusIfCurrentStatusAndExpired(
            eqLong(failedHold.id),
            eqInventoryHoldStatus(InventoryHoldStatus.HELD),
            eqInventoryHoldStatus(InventoryHoldStatus.EXPIRED),
            anyLocalDateTime(),
        )
        verify(repository, times(1)).updateStatusIfCurrentStatusAndExpired(
            eqLong(succeedingHold.id),
            eqInventoryHoldStatus(InventoryHoldStatus.HELD),
            eqInventoryHoldStatus(InventoryHoldStatus.EXPIRED),
            anyLocalDateTime(),
        )
    }

    private fun anyLocalDateTime(): LocalDateTime {
        any(LocalDateTime::class.java)
        return LocalDateTime.MIN
    }

    private fun eqLong(value: Long): Long {
        eq(value)
        return value
    }

    private fun eqInventoryHoldStatus(value: InventoryHoldStatus): InventoryHoldStatus {
        eq(value)
        return value
    }

    private fun hold(
        orderId: Long,
        status: InventoryHoldStatus,
        expiresAt: LocalDateTime,
        createdAt: LocalDateTime,
    ) = InventoryHold(
        orderId = orderId,
        status = status,
        expiresAt = expiresAt,
        createdAt = createdAt,
    )

    private fun createReservableOrder(
        merchantOrderId: String,
        skuId: Long,
        quantity: Int,
    ): Order {
        val order = orderRepository.saveAndFlush(
            Order(
                merchantOrderId = merchantOrderId,
                amount = quantity * 1000L,
                items = mutableListOf(
                    OrderItem(
                        skuId = skuId,
                        quantity = quantity,
                        unitPrice = 1000,
                    ),
                ),
            ),
        )
        skuStockRepository.saveAndFlush(
            SkuStock(
                skuId = skuId,
                onHand = quantity + 10,
                reserved = 0,
            ),
        )
        return order
    }
}
