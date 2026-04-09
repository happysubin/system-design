package com.paymentlab.inventory.infrastructure.persistence

import com.paymentlab.inventory.domain.SkuStock
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@SpringBootTest
class SkuStockPersistenceTest {

    @Autowired
    lateinit var skuStockRepository: SkuStockRepository

    @BeforeEach
    fun setUp() {
        skuStockRepository.deleteAll()
    }

    @Test
    fun `sku stock를 저장하고 다시 읽으면 skuId onHand reserved가 유지된다`() {
        val savedSkuStock = skuStockRepository.saveAndFlush(
            SkuStock(
                skuId = 101L,
                onHand = 25,
                reserved = 4,
            ),
        )

        val reloadedSkuStock = skuStockRepository.findById(savedSkuStock.id).orElse(null)

        assertNotNull(reloadedSkuStock)
        assertEquals(101L, reloadedSkuStock.skuId)
        assertEquals(25, reloadedSkuStock.onHand)
        assertEquals(4, reloadedSkuStock.reserved)
    }
}
