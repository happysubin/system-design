package com.paymentlab.inventory.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "inventory_hold_items")
class InventoryHoldItem(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hold_id", nullable = false)
    var hold: InventoryHold? = null,

    @Column(name = "sku_id", nullable = false)
    var skuId: Long = 0,

    @Column(nullable = false)
    var quantity: Int = 0,
)
