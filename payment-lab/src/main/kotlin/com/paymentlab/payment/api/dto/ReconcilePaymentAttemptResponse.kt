package com.paymentlab.payment.api.dto

import com.paymentlab.payment.domain.PaymentStatus

data class ReconcilePaymentAttemptResponse(
    val paymentAttemptId: Long,
    val status: PaymentStatus,
)
