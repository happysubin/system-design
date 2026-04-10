package com.pglab.payment.settlement

enum class SettlementStatus {
    READY,
    SCHEDULED,
    PROCESSING,
    PAID,
    FAILED,
    PARTIALLY_PAID,
}
