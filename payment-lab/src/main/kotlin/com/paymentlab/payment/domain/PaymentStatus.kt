package com.paymentlab.payment.domain

enum class PaymentStatus {
    READY,
    AUTH_REQUESTED,
    PENDING,
    DONE,
    FAILED,
    CANCELLED,
    EXPIRED,
}
