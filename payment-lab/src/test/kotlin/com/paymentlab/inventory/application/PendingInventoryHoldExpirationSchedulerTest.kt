package com.paymentlab.inventory.application

import com.paymentlab.inventory.domain.InventoryHold
import com.paymentlab.inventory.domain.InventoryHoldStatus
import com.paymentlab.inventory.infrastructure.persistence.InventoryHoldRepository
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
    lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun setUp() {
        inventoryHoldRepository.deleteAll()
        jdbcTemplate.execute("ALTER TABLE inventory_holds ALTER COLUMN id RESTART WITH 1")
    }

    @Test
    fun `만료된 HELD 홀드를 EXPIRED로 바꾸고 reserveOrReuse가 새 홀드를 만든다`() {
        val expiredHold = inventoryHoldRepository.saveAndFlush(
            heldHold(
                orderId = 1001L,
                expiresAt = LocalDateTime.now().minusMinutes(1),
                createdAt = LocalDateTime.now().minusMinutes(6),
            ),
        )

        pendingInventoryHoldExpirationScheduler.expirePendingInventoryHolds()

        val expiredStatus = inventoryHoldRepository.findById(expiredHold.id).orElseThrow().status
        val newHold = inventoryHoldApplicationService.reserveOrReuse(1001L)

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
