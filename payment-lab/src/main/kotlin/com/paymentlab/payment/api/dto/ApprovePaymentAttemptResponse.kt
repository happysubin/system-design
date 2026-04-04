package com.paymentlab.payment.api.dto

import com.paymentlab.payment.domain.PaymentStatus

data class ApprovePaymentAttemptResponse(
    val paymentAttemptId: Long,
    val status: PaymentStatus,
    val pgTransactionId: String,
)
