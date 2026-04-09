package com.pglab.payment.ledger

enum class LedgerEntryType {
    AUTH_CAPTURED,
    CANCELLED,
    REFUNDED,
    FEE_BOOKED,
    SETTLEMENT_CONFIRMED,
}
