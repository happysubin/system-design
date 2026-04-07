package com.paymentlab.payment.infrastructure.persistence

import com.paymentlab.payment.domain.PaymentAttempt
import com.paymentlab.payment.domain.PaymentStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface PaymentAttemptRepository : JpaRepository<PaymentAttempt, Long> {
    fun findByCheckoutKey(checkoutKey: String): PaymentAttempt?
    fun findByPgTransactionId(pgTransactionId: String): PaymentAttempt?

    @Modifying
    @Query(
        """
        update PaymentAttempt pa
        set pa.status = :nextStatus,
            pa.pgTransactionId = :pgTransactionId,
            pa.webhookSecret = :webhookSecret
        where pa.id = :paymentAttemptId
          and pa.status = :currentStatus
        """,
    )
    fun updateStatusAndPgTransactionIdAndWebhookSecretIfCurrentStatus(
        @Param("paymentAttemptId") paymentAttemptId: Long,
        @Param("currentStatus") currentStatus: PaymentStatus,
        @Param("nextStatus") nextStatus: PaymentStatus,
        @Param("pgTransactionId") pgTransactionId: String,
        @Param("webhookSecret") webhookSecret: String,
    ): Int

    @Modifying
    @Query(
        """
        update PaymentAttempt pa
        set pa.status = :nextStatus
        where pa.id = :paymentAttemptId
          and pa.status = :currentStatus
        """,
    )
    fun updateStatusIfCurrentStatus(
        @Param("paymentAttemptId") paymentAttemptId: Long,
        @Param("currentStatus") currentStatus: PaymentStatus,
        @Param("nextStatus") nextStatus: PaymentStatus,
    ): Int

    @Modifying
    @Query(
        """
        update PaymentAttempt pa
        set pa.status = :nextStatus
        where pa.pgTransactionId = :pgTransactionId
          and pa.status = :currentStatus
        """,
    )
    fun updateStatusByPgTransactionIdIfCurrentStatus(
        @Param("pgTransactionId") pgTransactionId: String,
        @Param("currentStatus") currentStatus: PaymentStatus,
        @Param("nextStatus") nextStatus: PaymentStatus,
    ): Int
}
