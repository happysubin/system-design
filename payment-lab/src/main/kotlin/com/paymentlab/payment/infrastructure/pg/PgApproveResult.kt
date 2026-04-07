package com.paymentlab.payment.infrastructure.pg

data class PgApproveResult(
    val pgTransactionId: String,
    val webhookSecret: String,
)
