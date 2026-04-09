package com.paymentlab.inventory.application

import com.paymentlab.inventory.domain.InventoryHold
import com.paymentlab.inventory.domain.InventoryHoldItem
import com.paymentlab.inventory.domain.InventoryHoldStatus
import com.paymentlab.inventory.domain.SkuStock
import com.paymentlab.inventory.infrastructure.persistence.InventoryHoldItemRepository
import com.paymentlab.inventory.infrastructure.persistence.InventoryHoldRepository
import com.paymentlab.inventory.infrastructure.persistence.SkuStockRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import java.time.LocalDateTime
import kotlin.test.assertEquals

@SpringBootTest
class PendingInventoryHoldExpirationSchedulerStockTest {

    @Autowired
    lateinit var pendingInventoryHoldExpirationScheduler: PendingInventoryHoldExpirationScheduler

    @Autowired
    lateinit var inventoryHoldRepository: InventoryHoldRepository

    @Autowired
    lateinit var inventoryHoldItemRepository: InventoryHoldItemRepository

    @Autowired
    lateinit var skuStockRepository: SkuStockRepository

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun setUp() {
        inventoryHoldItemRepository.deleteAll()
        skuStockRepository.deleteAll()
        inventoryHoldRepository.deleteAll()
        jdbcTemplate.execute("ALTER TABLE inventory_hold_items ALTER COLUMN id RESTART WITH 1")
        jdbcTemplate.execute("ALTER TABLE sku_stocks ALTER COLUMN id RESTART WITH 1")
        jdbcTemplate.execute("ALTER TABLE inventory_holds ALTER COLUMN id RESTART WITH 1")
    }

    @Test
    fun `만료 스케줄러는 expired hold의 reserved만 복구하고 onHand는 유지한다`() {
        val expiredHold = inventoryHoldRepository.saveAndFlush(
            heldHold(
                orderId = 1001L,
                expiresAt = LocalDateTime.now().minusMinutes(1),
                createdAt = LocalDateTime.now().minusMinutes(6),
            ),
        )
        val activeHold = inventoryHoldRepository.saveAndFlush(
            heldHold(
                orderId = 1002L,
                expiresAt = LocalDateTime.now().plusMinutes(1),
                createdAt = LocalDateTime.now().minusMinutes(1),
            ),
        )
        inventoryHoldItemRepository.saveAllAndFlush(
            listOf(
                InventoryHoldItem(hold = expiredHold, skuId = 101L, quantity = 2),
                InventoryHoldItem(hold = expiredHold, skuId = 202L, quantity = 3),
                InventoryHoldItem(hold = activeHold, skuId = 303L, quantity = 1),
            ),
        )
        skuStockRepository.saveAllAndFlush(
            listOf(
                SkuStock(skuId = 101L, onHand = 10, reserved = 4),
                SkuStock(skuId = 202L, onHand = 7, reserved = 3),
                SkuStock(skuId = 303L, onHand = 12, reserved = 1),
            ),
        )

        pendingInventoryHoldExpirationScheduler.expirePendingInventoryHolds()

        assertEquals(
            InventoryHoldStatus.EXPIRED,
            inventoryHoldRepository.findById(expiredHold.id).orElseThrow().status,
        )
        assertEquals(
            InventoryHoldStatus.HELD,
            inventoryHoldRepository.findById(activeHold.id).orElseThrow().status,
        )
        assertEquals(
            mapOf(
                101L to Pair(10, 2),
                202L to Pair(7, 0),
                303L to Pair(12, 1),
            ),
            skuStockRepository.findAll()
                .associate { it.skuId to (it.onHand to it.reserved) },
        )
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
