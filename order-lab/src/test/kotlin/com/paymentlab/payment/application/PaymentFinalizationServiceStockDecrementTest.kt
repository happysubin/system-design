package com.paymentlab.payment.application

import com.paymentlab.inventory.domain.InventoryHold
import com.paymentlab.inventory.domain.InventoryHoldItem
import com.paymentlab.inventory.domain.InventoryHoldStatus
import com.paymentlab.inventory.domain.SkuStock
import com.paymentlab.inventory.infrastructure.persistence.InventoryHoldItemRepository
import com.paymentlab.inventory.infrastructure.persistence.InventoryHoldRepository
import com.paymentlab.inventory.infrastructure.persistence.SkuStockRepository
import com.paymentlab.payment.domain.PaymentAttempt
import com.paymentlab.payment.domain.PaymentStatus
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import java.time.LocalDateTime
import kotlin.test.assertEquals

@SpringBootTest
class PaymentFinalizationServiceStockDecrementTest {

    @Autowired
    lateinit var paymentFinalizationService: PaymentFinalizationService

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
    fun `결제 성공이면 연결된 hold item 기준으로 reserved와 onHand를 차감하고 다른 sku는 그대로 둔다`() {
        val linkedHold = inventoryHoldRepository.saveAndFlush(
            InventoryHold(
                orderId = 1L,
                status = InventoryHoldStatus.HELD,
                expiresAt = LocalDateTime.of(2026, 4, 8, 10, 5, 0),
                createdAt = LocalDateTime.of(2026, 4, 8, 10, 0, 0),
            ),
        )
        inventoryHoldItemRepository.saveAllAndFlush(
            listOf(
                InventoryHoldItem(hold = linkedHold, skuId = 101L, quantity = 2),
                InventoryHoldItem(hold = linkedHold, skuId = 202L, quantity = 3),
            ),
        )
        skuStockRepository.saveAllAndFlush(
            listOf(
                SkuStock(skuId = 101L, onHand = 10, reserved = 4),
                SkuStock(skuId = 202L, onHand = 7, reserved = 3),
                SkuStock(skuId = 999L, onHand = 20, reserved = 5),
            ),
        )

        paymentFinalizationService.finalizeInventoryHold(
            paymentAttempt(orderId = 1L, inventoryHoldId = linkedHold.id),
            PaymentStatus.DONE,
        )

        assertEquals(
            InventoryHoldStatus.CONFIRMED,
            inventoryHoldRepository.findById(linkedHold.id).orElseThrow().status,
        )
        assertEquals(
            mapOf(
                101L to Pair(8, 2),
                202L to Pair(4, 0),
                999L to Pair(20, 5),
            ),
            skuStockRepository.findAll()
                .associate { it.skuId to (it.onHand to it.reserved) },
        )
    }

    private fun paymentAttempt(orderId: Long, inventoryHoldId: Long) = PaymentAttempt(
        id = 10,
        orderId = orderId,
        merchantOrderId = "merchant-order-1",
        checkoutKey = "checkout-key-1",
        pgTransactionId = "pg-tx-1",
        webhookSecret = "secret-1",
        amount = 15000,
        status = PaymentStatus.PENDING,
        inventoryHoldId = inventoryHoldId,
    )
}
