package com.paymentlab.payment.api.dto

data class CreateOrderRequest(
    val merchantOrderId: String,
    val amount: Long,
)
