package com.paymentlab.inventory.infrastructure.persistence

import com.paymentlab.inventory.domain.InventoryHold
import com.paymentlab.inventory.domain.InventoryHoldStatus
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@SpringBootTest
class InventoryHoldPersistenceTest {

    @Autowired
    lateinit var inventoryHoldRepository: InventoryHoldRepository

    @Autowired
    lateinit var inventoryHoldItemRepository: InventoryHoldItemRepository

    @BeforeEach
    fun setUp() {
        inventoryHoldItemRepository.deleteAll()
        inventoryHoldRepository.deleteAll()
    }

    @AfterEach
    fun tearDown() {
        inventoryHoldItemRepository.deleteAll()
        inventoryHoldRepository.deleteAll()
    }

    @Test
    fun `inventory hold를 저장하고 다시 읽으면 orderId status expiresAt이 유지된다`() {
        val expiresAt = LocalDateTime.of(2026, 4, 8, 12, 30, 0)

        val savedHold = inventoryHoldRepository.saveAndFlush(
            InventoryHold(
                orderId = 1001,
                status = InventoryHoldStatus.HELD,
                expiresAt = expiresAt,
            ),
        )

        val reloadedHold = inventoryHoldRepository.findById(savedHold.id).orElse(null)

        assertNotNull(reloadedHold)
        assertEquals(1001, reloadedHold.orderId)
        assertEquals(InventoryHoldStatus.HELD, reloadedHold.status)
        assertEquals(expiresAt, reloadedHold.expiresAt)
    }
}
