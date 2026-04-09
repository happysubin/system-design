package com.paymentlab.order.infrastructure.persistence

import com.paymentlab.order.domain.OrderItem
import org.springframework.data.jpa.repository.JpaRepository

interface OrderItemRepository : JpaRepository<OrderItem, Long> {
    fun findAllByOrderIdOrderByIdAsc(orderId: Long): List<OrderItem>
}
