package com.paymentlab.payment.infrastructure.persistence

import com.paymentlab.payment.domain.Order
import org.springframework.data.jpa.repository.JpaRepository

interface OrderRepository : JpaRepository<Order, Long>
