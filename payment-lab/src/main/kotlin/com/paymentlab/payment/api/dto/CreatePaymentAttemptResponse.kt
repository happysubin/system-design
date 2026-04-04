package com.paymentlab.payment.api.dto

import com.paymentlab.payment.domain.PaymentStatus

data class CreatePaymentAttemptResponse(
    val paymentAttemptId: Long,
    val orderId: Long,
    val status: PaymentStatus,
)
