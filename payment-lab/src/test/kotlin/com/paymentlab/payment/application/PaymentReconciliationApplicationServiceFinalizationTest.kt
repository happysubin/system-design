package com.paymentlab.payment.application

import com.paymentlab.inventory.domain.InventoryHold
import com.paymentlab.inventory.domain.InventoryHoldStatus
import com.paymentlab.inventory.infrastructure.persistence.InventoryHoldRepository
import com.paymentlab.payment.domain.PaymentAttempt
import com.paymentlab.payment.domain.PaymentStatus
import com.paymentlab.payment.infrastructure.persistence.PaymentAttemptRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import java.time.LocalDateTime
import kotlin.test.assertEquals

@SpringBootTest
class PaymentReconciliationApplicationServiceFinalizationTest {

    @Autowired
    lateinit var paymentApplicationService: PaymentApplicationService

    @Autowired
    lateinit var paymentAttemptRepository: PaymentAttemptRepository

    @Autowired
    lateinit var inventoryHoldRepository: InventoryHoldRepository

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun setUp() {
        paymentAttemptRepository.deleteAll()
        inventoryHoldRepository.deleteAll()
        jdbcTemplate.execute("ALTER TABLE payment_attempts ALTER COLUMN id RESTART WITH 1")
        jdbcTemplate.execute("ALTER TABLE inventory_holds ALTER COLUMN id RESTART WITH 1")
    }

    @Test
    fun `재확정 실패는 연결된 홀드만 해제하고 같은 주문의 다른 홀드는 유지한다`() {
        val linkedHold = inventoryHoldRepository.saveAndFlush(heldHold(orderId = 1, createdAt = LocalDateTime.of(2026, 4, 8, 10, 0, 0)))
        val laterHold = inventoryHoldRepository.saveAndFlush(heldHold(orderId = 1, createdAt = LocalDateTime.of(2026, 4, 8, 10, 5, 0)))
        val paymentAttempt = paymentAttemptRepository.saveAndFlush(
            pendingAttempt(orderId = 1, inventoryHoldId = linkedHold.id),
        )

        val result = paymentApplicationService.applyReconcileResult(paymentAttempt.id, "FAIL")

        assertEquals(PaymentStatus.FAILED, result.status)
        assertEquals(InventoryHoldStatus.RELEASED, inventoryHoldRepository.findById(linkedHold.id).orElseThrow().status)
        assertEquals(InventoryHoldStatus.HELD, inventoryHoldRepository.findById(laterHold.id).orElseThrow().status)
        assertEquals(PaymentStatus.FAILED, paymentAttemptRepository.findById(paymentAttempt.id).orElseThrow().status)
    }

    @Test
    fun `이미 다른 경로가 확정한 결제는 홀드를 다시 해제하지 않는다`() {
        val linkedHold = inventoryHoldRepository.saveAndFlush(
            heldHold(orderId = 1, status = InventoryHoldStatus.RELEASED, createdAt = LocalDateTime.of(2026, 4, 8, 10, 0, 0)),
        )
        val paymentAttempt = paymentAttemptRepository.saveAndFlush(
            pendingAttempt(orderId = 1, status = PaymentStatus.FAILED, inventoryHoldId = linkedHold.id),
        )

        assertThrows<IllegalStateException> {
            paymentApplicationService.applyReconcileResult(paymentAttempt.id, "FAIL")
        }

        assertEquals(InventoryHoldStatus.RELEASED, inventoryHoldRepository.findById(linkedHold.id).orElseThrow().status)
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
