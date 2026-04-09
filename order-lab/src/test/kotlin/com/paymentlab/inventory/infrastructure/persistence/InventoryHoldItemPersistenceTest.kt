package com.paymentlab.inventory.infrastructure.persistence

import com.paymentlab.inventory.domain.InventoryHold
import com.paymentlab.inventory.domain.InventoryHoldItem
import com.paymentlab.inventory.domain.InventoryHoldStatus
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import kotlin.test.assertEquals

@SpringBootTest
@Transactional
class InventoryHoldItemPersistenceTest {

    @Autowired
    lateinit var inventoryHoldRepository: InventoryHoldRepository

    @Autowired
    lateinit var inventoryHoldItemRepository: InventoryHoldItemRepository

    @BeforeEach
    fun setUp() {
        inventoryHoldItemRepository.deleteAll()
        inventoryHoldRepository.deleteAll()
    }

    @Test
    fun `hold를 저장하고 다시 읽으면 여러 hold item의 skuId quantity가 유지된다`() {
        val savedHold = inventoryHoldRepository.saveAndFlush(
            InventoryHold(
                orderId = 1001,
                status = InventoryHoldStatus.HELD,
                expiresAt = LocalDateTime.of(2026, 4, 8, 12, 30, 0),
            ),
        )

        inventoryHoldItemRepository.saveAll(
            listOf(
                InventoryHoldItem(
                    hold = savedHold,
                    skuId = 101L,
                    quantity = 2,
                ),
                InventoryHoldItem(
                    hold = savedHold,
                    skuId = 202L,
                    quantity = 4,
                ),
            ),
        )

        val reloadedItems = inventoryHoldItemRepository.findAllByHoldIdOrderByIdAsc(savedHold.id)

        assertEquals(2, reloadedItems.size)
        assertEquals(listOf(101L, 202L), reloadedItems.map { it.skuId })
        assertEquals(listOf(2, 4), reloadedItems.map { it.quantity })
    }
}
