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

        // 같은 주문의 재시도에서 active hold를 재사용하는 이유는,
        // 결제 버튼 재클릭이나 재시도마다 reserved를 다시 올려 중복 예약되는 것을 막기 위해서다.
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
            // hold row를 먼저 저장하지 않고 SKU별 reserved를 먼저 올리는 이유는,
            // 재고를 실제로 확보하지 못한 주문에 빈 hold만 남는 중간 상태를 피하기 위해서다.
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
                // hold item은 '이 hold가 어떤 SKU를 몇 개 잡았는지'를 스냅샷으로 남겨,
                // 이후 confirm/release/expire가 정확히 같은 수량을 되돌리거나 차감하게 만든다.
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
            // 성공 확정에서만 reserved와 onHand를 같이 줄이는 이유는,
            // 예약(reserved)과 실제 판매 확정(onHand 감소)을 구분해 oversell과 중복 차감을 막기 위해서다.
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
            // 실패/취소/만료에서는 실제 판매가 일어난 것이 아니므로 onHand는 건드리지 않고,
            // 결제 중 임시로 잡아둔 reserved만 복구해야 다시 판매 가능한 수량이 열린다.
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
