package com.paymentlab.payment.infrastructure.pg

enum class PgApproveOutcome {
    PENDING,
    DECLINED,
}

data class PgApproveResult(
    val pgTransactionId: String,
    val webhookSecret: String,
    val outcome: PgApproveOutcome,
)
