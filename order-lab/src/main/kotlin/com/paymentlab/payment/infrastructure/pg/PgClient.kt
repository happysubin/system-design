package com.paymentlab.payment.infrastructure.pg

interface PgClient {
    fun approve(paymentAttemptId: Long, merchantOrderId: String, amount: Long): PgApproveResult
    fun query(pgTransactionId: String): String
    fun queryByMerchantOrderId(merchantOrderId: String): String
}
