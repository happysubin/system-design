package com.paymentlab.payment.infrastructure.pg

import org.springframework.stereotype.Component

@Component
class StubPgClient : PgClient {
    override fun approve(paymentAttemptId: Long, merchantOrderId: String, amount: Long): PgApproveResult {
        return PgApproveResult(
            pgTransactionId = "stub-pg-$paymentAttemptId",
            webhookSecret = "stub-secret-$paymentAttemptId",
        )
    }

    override fun query(pgTransactionId: String): String {
        return "SUCCESS"
    }
}
