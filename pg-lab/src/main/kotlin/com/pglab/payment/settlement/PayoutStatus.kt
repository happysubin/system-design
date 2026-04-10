package com.pglab.payment.settlement

enum class PayoutStatus {
    REQUESTED,
    SENT,
    RECONCILING,
    SUCCEEDED,
    FAILED,
    CANCELED,
}
