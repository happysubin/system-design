package com.paymentlab.payment.api.dto

import com.paymentlab.payment.domain.PaymentStatus

data class PaymentWebhookResponse(
    val paymentAttemptId: Long,
    val status: PaymentStatus,
)
