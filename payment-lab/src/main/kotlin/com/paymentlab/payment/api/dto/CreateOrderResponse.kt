package com.paymentlab.payment.api.dto

data class CreateOrderResponse(
    val orderId: Long,
    val merchantOrderId: String,
    val amount: Long,
)
