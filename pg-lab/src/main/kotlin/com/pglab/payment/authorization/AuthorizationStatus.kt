package com.pglab.payment.authorization

enum class AuthorizationStatus {
    READY,
    APPROVED,
    PARTIALLY_CANCELED,
    CANCELED,
    REFUNDED,
}
