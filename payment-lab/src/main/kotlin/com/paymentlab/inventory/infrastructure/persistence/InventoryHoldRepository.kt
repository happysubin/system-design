package com.paymentlab.inventory.infrastructure.persistence

import com.paymentlab.inventory.domain.InventoryHold
import com.paymentlab.inventory.domain.InventoryHoldStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface InventoryHoldRepository : JpaRepository<InventoryHold, Long> {
    fun findFirstByOrderIdAndStatusOrderByCreatedAtDesc(
        orderId: Long,
        status: InventoryHoldStatus,
    ): InventoryHold?

    fun findFirstByOrderIdAndStatusAndExpiresAtAfter(
        orderId: Long,
        status: InventoryHoldStatus,
        expiresAt: LocalDateTime,
    ): InventoryHold?

    fun findAllByStatusAndExpiresAtBefore(
        status: InventoryHoldStatus,
        expiresAt: LocalDateTime,
    ): List<InventoryHold>

    @Modifying
    @Query(
        """
        update InventoryHold ih
        set ih.status = :nextStatus
        where ih.id = :inventoryHoldId
          and ih.status = :currentStatus
        """,
    )
    fun updateStatusIfCurrentStatus(
        @Param("inventoryHoldId") inventoryHoldId: Long,
        @Param("currentStatus") currentStatus: InventoryHoldStatus,
        @Param("nextStatus") nextStatus: InventoryHoldStatus,
    ): Int

    @Modifying
    @Query(
        """
        update InventoryHold ih
        set ih.status = :nextStatus
        where ih.id = :inventoryHoldId
          and ih.status = :currentStatus
          and ih.expiresAt <= :expiresAt
        """,
    )
    fun updateStatusIfCurrentStatusAndExpired(
        @Param("inventoryHoldId") inventoryHoldId: Long,
        @Param("currentStatus") currentStatus: InventoryHoldStatus,
        @Param("nextStatus") nextStatus: InventoryHoldStatus,
        @Param("expiresAt") expiresAt: LocalDateTime,
    ): Int
}
