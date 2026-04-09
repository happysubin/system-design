package com.paymentlab.payment.domain

import com.paymentlab.order.domain.OrderItem
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "orders")
class Order(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(name = "merchant_order_id", nullable = false, length = 100, unique = true)
    var merchantOrderId: String = "",

    @Column(nullable = false)
    var amount: Long = 0,

    @OneToMany(mappedBy = "order", cascade = [CascadeType.ALL], orphanRemoval = true)
    var items: MutableList<OrderItem> = mutableListOf(),

    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),
) {

    init {
        items.forEach(::addItem)
    }

    fun addItem(item: OrderItem) {
        item.order = this
        if (!items.contains(item)) {
            items.add(item)
        }
    }
}
