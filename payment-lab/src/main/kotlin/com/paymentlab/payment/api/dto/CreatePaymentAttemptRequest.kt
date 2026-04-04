package com.paymentlab.payment.api.dto

data class CreatePaymentAttemptRequest(
    val orderId: Long,
    val idempotencyKey: String,
)
