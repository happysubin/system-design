package com.paymentlab.inventory.application

import com.paymentlab.inventory.domain.InventoryHold
import com.paymentlab.inventory.domain.InventoryHoldItem
import com.paymentlab.inventory.domain.InventoryHoldStatus
import com.paymentlab.inventory.infrastructure.persistence.InventoryHoldItemRepository
import com.paymentlab.inventory.infrastructure.persistence.InventoryHoldRepository
import com.paymentlab.inventory.infrastructure.persistence.SkuStockRepository
import com.paymentlab.order.infrastructure.persistence.OrderItemRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

class InsufficientInventoryReservationException(
    message: String,
) : IllegalStateException(message)

@Service
class InventoryHoldApplicationService(
    private val inventoryHoldRepository: InventoryHoldRepository,
    private val inventoryHoldItemRepository: InventoryHoldItemRepository,
    private val skuStockRepository: SkuStockRepository,
    private val orderItemRepository: OrderItemRepository,
    @Value("\${inventory.hold.ttl-seconds:300}") private val ttlSeconds: Long = 300,
) {
    @Transactional
    fun reserveOrReuse(orderId: Long): InventoryHold {
        val now = LocalDateTime.now()
        val activeHold = inventoryHoldRepository.findFirstByOrderIdAndStatusAndExpiresAtAfter(
            orderId,
            InventoryHoldStatus.HELD,
            now,
        )
        if (activeHold != null) {
            return activeHold
        }

        val orderItems = orderItemRepository.findAllByOrderIdOrderByIdAsc(orderId)
        if (orderItems.isEmpty()) {
            throw IllegalStateException("order has no items: $orderId")
        }

        orderItems.forEach { orderItem ->
            val reserved = skuStockRepository.incrementReservedIfAvailable(orderItem.skuId, orderItem.quantity)
            if (reserved == 0) {
                throw InsufficientInventoryReservationException(
                    "insufficient available stock for skuId: ${orderItem.skuId}",
                )
            }
        }

        val savedHold = inventoryHoldRepository.saveAndFlush(
            InventoryHold(
                orderId = orderId,
                status = InventoryHoldStatus.HELD,
                expiresAt = now.plusSeconds(ttlSeconds),
                createdAt = now,
            ),
        )
        inventoryHoldItemRepository.saveAll(
            orderItems.map { orderItem ->
                InventoryHoldItem(
                    hold = savedHold,
                    skuId = orderItem.skuId,
                    quantity = orderItem.quantity,
                )
            },
        )

        return savedHold
    }

    @Transactional
    fun confirmHeldHold(orderId: Long, inventoryHoldId: Long) {
        val hold = inventoryHoldRepository.findById(inventoryHoldId)
            .orElseThrow { IllegalStateException("inventory hold not found: $inventoryHoldId") }

        if (hold.orderId != orderId) {
            throw IllegalStateException(
                "inventory hold $inventoryHoldId does not belong to orderId: $orderId",
            )
        }

        val holdItems = inventoryHoldItemRepository.findAllByHoldIdOrderByIdAsc(inventoryHoldId)
        if (holdItems.isEmpty()) {
            throw IllegalStateException("inventory hold has no items: $inventoryHoldId")
        }

        holdItems.forEach { holdItem ->
            val updated = skuStockRepository.decrementReservedAndOnHand(
                skuId = holdItem.skuId,
                quantity = holdItem.quantity,
            )
            if (updated == 0) {
                throw IllegalStateException("stock confirmation failed for skuId: ${holdItem.skuId}")
            }
        }

        val transitioned = inventoryHoldRepository.updateStatusIfCurrentStatus(
            inventoryHoldId,
            InventoryHoldStatus.HELD,
            InventoryHoldStatus.CONFIRMED,
        )
        if (transitioned == 0) {
            throw IllegalStateException("inventory hold is no longer held: $inventoryHoldId")
        }
    }

    @Transactional
    fun releaseHeldHold(orderId: Long, inventoryHoldId: Long) {
        val hold = inventoryHoldRepository.findById(inventoryHoldId)
            .orElseThrow { IllegalStateException("inventory hold not found: $inventoryHoldId") }

        if (hold.orderId != orderId) {
            throw IllegalStateException(
                "inventory hold $inventoryHoldId does not belong to orderId: $orderId",
            )
        }

        restoreReservedStock(inventoryHoldId)

        val transitioned = inventoryHoldRepository.updateStatusIfCurrentStatus(
            inventoryHoldId,
            InventoryHoldStatus.HELD,
            InventoryHoldStatus.RELEASED,
        )
        if (transitioned == 0) {
            throw IllegalStateException("inventory hold is no longer held: $inventoryHoldId")
        }
    }

    fun findExpiredHeldHoldIds(now: LocalDateTime = LocalDateTime.now()): List<Long> =
        inventoryHoldRepository.findAllByStatusAndExpiresAtBefore(InventoryHoldStatus.HELD, now)
            .map { it.id }

    @Transactional
    fun expireHeldHold(inventoryHoldId: Long, now: LocalDateTime = LocalDateTime.now()) {
        inventoryHoldRepository.findById(inventoryHoldId)
            .orElseThrow { IllegalStateException("inventory hold not found: $inventoryHoldId") }

        restoreReservedStock(inventoryHoldId)

        val transitioned = inventoryHoldRepository.updateStatusIfCurrentStatusAndExpired(
            inventoryHoldId,
            InventoryHoldStatus.HELD,
            InventoryHoldStatus.EXPIRED,
            now,
        )
        if (transitioned == 0) {
            throw IllegalStateException("inventory hold is no longer eligible for expiration: $inventoryHoldId")
        }
    }

    @Transactional
    fun expireStaleHolds() {
        val now = LocalDateTime.now()
        findExpiredHeldHoldIds(now)
            .forEach { holdId ->
                runCatching {
                    expireHeldHoldInCurrentTransaction(holdId, now)
                }
            }
    }

    private fun expireHeldHoldInCurrentTransaction(inventoryHoldId: Long, now: LocalDateTime) {
        inventoryHoldRepository.findById(inventoryHoldId)
            .orElseThrow { IllegalStateException("inventory hold not found: $inventoryHoldId") }

        restoreReservedStock(inventoryHoldId)

        val transitioned = inventoryHoldRepository.updateStatusIfCurrentStatusAndExpired(
            inventoryHoldId,
            InventoryHoldStatus.HELD,
            InventoryHoldStatus.EXPIRED,
            now,
        )
        if (transitioned == 0) {
            throw IllegalStateException("inventory hold is no longer eligible for expiration: $inventoryHoldId")
        }
    }

    private fun restoreReservedStock(inventoryHoldId: Long) {
        val holdItems = inventoryHoldItemRepository.findAllByHoldIdOrderByIdAsc(inventoryHoldId)
        if (holdItems.isEmpty()) {
            return
        }

        holdItems.forEach { holdItem ->
            val updated = skuStockRepository.decrementReserved(
                skuId = holdItem.skuId,
                quantity = holdItem.quantity,
            )
            if (updated == 0) {
                throw IllegalStateException("stock release failed for skuId: ${holdItem.skuId}")
            }
        }
    }
}
