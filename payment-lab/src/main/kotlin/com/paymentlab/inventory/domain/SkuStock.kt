package com.paymentlab.inventory.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "sku_stocks")
class SkuStock(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(name = "sku_id", nullable = false)
    var skuId: Long = 0,

    @Column(name = "on_hand", nullable = false)
    var onHand: Int = 0,

    @Column(nullable = false)
    var reserved: Int = 0,
)
