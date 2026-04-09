package com.paymentlab.inventory.infrastructure.persistence

import com.paymentlab.inventory.domain.InventoryHoldItem
import org.springframework.data.jpa.repository.JpaRepository

interface InventoryHoldItemRepository : JpaRepository<InventoryHoldItem, Long> {
    fun findAllByHoldIdOrderByIdAsc(holdId: Long): List<InventoryHoldItem>
}
