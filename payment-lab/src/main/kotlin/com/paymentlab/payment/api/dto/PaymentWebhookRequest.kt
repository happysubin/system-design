package com.paymentlab.payment.api.dto

data class PaymentWebhookRequest(
    val pgTransactionId: String,
    val result: String,
)
