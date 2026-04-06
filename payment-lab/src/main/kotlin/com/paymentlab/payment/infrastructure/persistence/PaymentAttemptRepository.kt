package com.paymentlab.payment.infrastructure.persistence

import com.paymentlab.payment.domain.PaymentAttempt
import org.springframework.data.jpa.repository.JpaRepository

interface PaymentAttemptRepository : JpaRepository<PaymentAttempt, Long> {
    fun findByCheckoutKey(checkoutKey: String): PaymentAttempt?
    fun findByPgTransactionId(pgTransactionId: String): PaymentAttempt?
}
