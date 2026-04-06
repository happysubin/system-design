package com.paymentlab.payment.api.dto

data class IssueCheckoutKeyRequest(
    val orderId: Long,
    val merchantOrderId: String,
    val amount: Long,
)
