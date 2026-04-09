package com.paymentlab.payment.application

import com.paymentlab.inventory.domain.InventoryHold
import com.paymentlab.inventory.domain.InventoryHoldItem
import com.paymentlab.inventory.domain.InventoryHoldStatus
import com.paymentlab.inventory.infrastructure.persistence.InventoryHoldRepository
import com.paymentlab.inventory.infrastructure.persistence.InventoryHoldItemRepository
import com.paymentlab.inventory.infrastructure.persistence.SkuStockRepository
import com.paymentlab.inventory.domain.SkuStock
import com.paymentlab.payment.api.dto.PaymentWebhookRequest
import com.paymentlab.payment.domain.PaymentAttempt
import com.paymentlab.payment.domain.PaymentStatus
import com.paymentlab.payment.infrastructure.persistence.PaymentAttemptRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import java.time.LocalDateTime
import kotlin.test.assertEquals

@SpringBootTest
class PaymentWebhookApplicationServiceFinalizationTest {

    @Autowired
    lateinit var paymentWebhookApplicationService: PaymentWebhookApplicationService

    @Autowired
    lateinit var paymentAttemptRepository: PaymentAttemptRepository

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
        paymentAttemptRepository.deleteAll()
        inventoryHoldRepository.deleteAll()
        jdbcTemplate.execute("ALTER TABLE payment_attempts ALTER COLUMN id RESTART WITH 1")
        jdbcTemplate.execute("ALTER TABLE inventory_hold_items ALTER COLUMN id RESTART WITH 1")
        jdbcTemplate.execute("ALTER TABLE sku_stocks ALTER COLUMN id RESTART WITH 1")
        jdbcTemplate.execute("ALTER TABLE inventory_holds ALTER COLUMN id RESTART WITH 1")
    }

    @Test
    fun `성공 웹훅은 연결된 홀드만 확정하고 같은 주문의 다른 홀드는 유지한다`() {
        val linkedHold = inventoryHoldRepository.saveAndFlush(heldHold(orderId = 1, createdAt = LocalDateTime.of(2026, 4, 8, 10, 0, 0)))
        val laterHold = inventoryHoldRepository.saveAndFlush(heldHold(orderId = 1, createdAt = LocalDateTime.of(2026, 4, 8, 10, 5, 0)))
        inventoryHoldItemRepository.saveAndFlush(InventoryHoldItem(hold = linkedHold, skuId = 101L, quantity = 2))
        skuStockRepository.saveAndFlush(SkuStock(skuId = 101L, onHand = 10, reserved = 2))
        paymentAttemptRepository.saveAndFlush(
            pendingAttempt(orderId = 1, inventoryHoldId = linkedHold.id),
        )

        val result = paymentWebhookApplicationService.handleWebhook(successWebhook())

        assertEquals(PaymentStatus.DONE, result.status)
        assertEquals(InventoryHoldStatus.CONFIRMED, inventoryHoldRepository.findById(linkedHold.id).orElseThrow().status)
        assertEquals(InventoryHoldStatus.HELD, inventoryHoldRepository.findById(laterHold.id).orElseThrow().status)
        assertEquals(PaymentStatus.DONE, paymentAttemptRepository.findByPgTransactionId("pg-tx-1")!!.status)
    }

    @Test
    fun `이미 done인 결제에 같은 성공 웹훅이 다시 와도 홀드를 다시 확정하지 않는다`() {
        val linkedHold = inventoryHoldRepository.saveAndFlush(
            heldHold(orderId = 1, status = InventoryHoldStatus.CONFIRMED, createdAt = LocalDateTime.of(2026, 4, 8, 10, 0, 0)),
        )
        paymentAttemptRepository.saveAndFlush(
            pendingAttempt(orderId = 1, status = PaymentStatus.DONE, inventoryHoldId = linkedHold.id),
        )

        val result = paymentWebhookApplicationService.handleWebhook(successWebhook())

        assertEquals(PaymentStatus.DONE, result.status)
        assertEquals(InventoryHoldStatus.CONFIRMED, inventoryHoldRepository.findById(linkedHold.id).orElseThrow().status)
    }

    private fun successWebhook() = PaymentWebhookRequest(
        merchantOrderId = "order-1",
        pgTransactionId = "pg-tx-1",
        secret = "secret-1",
        result = "SUCCESS",
    )

    private fun heldHold(
        orderId: Long,
        status: InventoryHoldStatus = InventoryHoldStatus.HELD,
        createdAt: LocalDateTime,
    ) = InventoryHold(
        orderId = orderId,
        status = status,
        expiresAt = createdAt.plusMinutes(5),
        createdAt = createdAt,
    )

    private fun pendingAttempt(
        orderId: Long,
        status: PaymentStatus = PaymentStatus.PENDING,
        inventoryHoldId: Long,
    ) = PaymentAttempt(
        orderId = orderId,
        merchantOrderId = "order-1",
        checkoutKey = "checkout-1",
        pgTransactionId = "pg-tx-1",
        webhookSecret = "secret-1",
        amount = 15000,
        status = status,
        inventoryHoldId = inventoryHoldId,
    )
}
