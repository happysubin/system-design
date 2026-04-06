package com.paymentlab.payment.infrastructure.redis

interface CheckoutKeyStore {
    fun issue(orderId: Long, merchantOrderId: String, amount: Long): String

    fun consumeIfValid(checkoutKey: String, orderId: Long, merchantOrderId: String, amount: Long): Boolean
}
