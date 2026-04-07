package com.paymentlab.payment.domain

enum class PaymentStatus {
    READY,
    AUTH_REQUESTED,
    PENDING,
    DECLINED,
    DONE,
    FAILED,
    CANCELLED,
    EXPIRED,
}
