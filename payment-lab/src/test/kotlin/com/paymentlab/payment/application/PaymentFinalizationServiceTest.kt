package com.paymentlab.payment.application

import com.paymentlab.inventory.domain.InventoryHold
import com.paymentlab.inventory.domain.InventoryHoldStatus
import com.paymentlab.inventory.infrastructure.persistence.InventoryHoldRepository
import com.paymentlab.payment.domain.PaymentAttempt
import com.paymentlab.payment.domain.PaymentStatus
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import java.time.LocalDateTime
import kotlin.test.assertEquals

@SpringBootTest
class PaymentFinalizationServiceTest {

    @Autowired
    lateinit var paymentFinalizationService: PaymentFinalizationService

    @Autowired
    lateinit var inventoryHoldRepository: InventoryHoldRepository

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun setUp() {
        inventoryHoldRepository.deleteAll()
        jdbcTemplate.execute("ALTER TABLE inventory_holds ALTER COLUMN id RESTART WITH 1")
    }

    @Test
    fun `연결된 inventoryHoldId만 확정하고 같은 주문의 다른 홀드는 그대로 둔다`() {
        val linkedHold = inventoryHoldRepository.saveAndFlush(heldHold(orderId = 1, createdAt = LocalDateTime.of(2026, 4, 8, 10, 0, 0)))
        val laterHold = inventoryHoldRepository.saveAndFlush(heldHold(orderId = 1, createdAt = LocalDateTime.of(2026, 4, 8, 10, 5, 0)))
        val paymentAttempt = pendingAttempt(orderId = 1, inventoryHoldId = linkedHold.id)

        paymentFinalizationService.finalizeInventoryHold(paymentAttempt, PaymentStatus.DONE)

        assertEquals(InventoryHoldStatus.CONFIRMED, inventoryHoldRepository.findById(linkedHold.id).orElseThrow().status)
        assertEquals(InventoryHoldStatus.HELD, inventoryHoldRepository.findById(laterHold.id).orElseThrow().status)
    }

    @Test
    fun `연결된 홀드가 없으면 예외로 실패한다`() {
        val paymentAttempt = pendingAttempt(orderId = 1, inventoryHoldId = 9999)

        assertThrows<IllegalStateException> {
            paymentFinalizationService.finalizeInventoryHold(paymentAttempt, PaymentStatus.DONE)
        }
    }

    @Test
    fun `연결된 홀드가 이미 다른 상태면 guarded update 실패를 숨기지 않는다`() {
        val releasedHold = inventoryHoldRepository.saveAndFlush(
            heldHold(orderId = 1, status = InventoryHoldStatus.RELEASED, createdAt = LocalDateTime.of(2026, 4, 8, 10, 0, 0)),
        )
        val paymentAttempt = pendingAttempt(orderId = 1, inventoryHoldId = releasedHold.id)

        assertThrows<IllegalStateException> {
            paymentFinalizationService.finalizeInventoryHold(paymentAttempt, PaymentStatus.FAILED)
        }
    }

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

    private fun pendingAttempt(orderId: Long, inventoryHoldId: Long) = PaymentAttempt(
        id = 10,
        orderId = orderId,
        merchantOrderId = "order-1",
        checkoutKey = "checkout-1",
        pgTransactionId = "pg-tx-1",
        webhookSecret = "secret-1",
        amount = 15000,
        status = PaymentStatus.PENDING,
        inventoryHoldId = inventoryHoldId,
    )
}
