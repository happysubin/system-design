package com.paymentlab.payment.infrastructure.pg

interface PgClient {
    fun approve(paymentAttemptId: Long, merchantOrderId: String, amount: Long): String
    fun query(pgTransactionId: String): String
}
