package com.paymentlab.inventory.application

import com.paymentlab.inventory.domain.InventoryHold
import com.paymentlab.inventory.domain.InventoryHoldStatus
import com.paymentlab.inventory.infrastructure.persistence.InventoryHoldRepository
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

@SpringBootTest(properties = ["inventory.hold.ttl-seconds=300"])
class InventoryHoldApplicationServiceTest {

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
    fun `활성 재고 홀드가 없으면 HELD 상태 새 홀드를 만든다`() {
        val result = inventoryHoldApplicationService.reserveOrReuse(1001L)

        assertEquals(1L, result.id)
        assertEquals(1001L, result.orderId)
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
        val expiredHold = inventoryHoldRepository.saveAndFlush(
            hold(
                orderId = 1001L,
                status = InventoryHoldStatus.HELD,
                expiresAt = LocalDateTime.now().minusMinutes(1),
                createdAt = LocalDateTime.now().minusMinutes(6),
            ),
        )

        val result = inventoryHoldApplicationService.reserveOrReuse(1001L)

        assertNotEquals(expiredHold.id, result.id)
        assertEquals(InventoryHoldStatus.HELD, result.status)
        assertEquals(2, inventoryHoldRepository.count())
    }

    @Test
    fun `RELEASED 상태 홀드는 재사용하지 않고 새 홀드를 만든다`() {
        val releasedHold = inventoryHoldRepository.saveAndFlush(
            hold(
                orderId = 1001L,
                status = InventoryHoldStatus.RELEASED,
                expiresAt = LocalDateTime.now().plusMinutes(5),
                createdAt = LocalDateTime.now().minusMinutes(1),
            ),
        )

        val result = inventoryHoldApplicationService.reserveOrReuse(1001L)

        assertNotEquals(releasedHold.id, result.id)
        assertEquals(InventoryHoldStatus.HELD, result.status)
        assertEquals(2, inventoryHoldRepository.count())
    }

    @Test
    fun `EXPIRED 상태 홀드는 재사용하지 않고 새 홀드를 만든다`() {
        val expiredHold = inventoryHoldRepository.saveAndFlush(
            hold(
                orderId = 1001L,
                status = InventoryHoldStatus.EXPIRED,
                expiresAt = LocalDateTime.now().minusMinutes(1),
                createdAt = LocalDateTime.now().minusMinutes(6),
            ),
        )

        val result = inventoryHoldApplicationService.reserveOrReuse(1001L)

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
        val service = InventoryHoldApplicationService(repository, 300)
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
}
