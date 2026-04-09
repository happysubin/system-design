package com.paymentlab.order.infrastructure.persistence

import com.paymentlab.order.domain.Order
import org.springframework.data.jpa.repository.JpaRepository

interface OrderRepository : JpaRepository<Order, Long>
