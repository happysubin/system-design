package com.pglab.payment.order

enum class PaymentOrderStatus {
    READY,
    PARTIALLY_AUTHORIZED,
    AUTHORIZED,
    PARTIALLY_CANCELED,
    CANCELED,
}
