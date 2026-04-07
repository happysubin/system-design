package com.paymentlab.payment.application

import com.paymentlab.payment.domain.PaymentStatus
import com.paymentlab.payment.infrastructure.persistence.PaymentAttemptRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class PendingPaymentReconciliationScheduler(
    private val paymentAttemptRepository: PaymentAttemptRepository,
    private val paymentApplicationService: PaymentApplicationService,
) {
    @Scheduled(fixedDelay = 300000)
    fun reconcilePendingPayments() {
        paymentAttemptRepository.findAllByStatus(PaymentStatus.PENDING)
            .forEach { paymentAttempt ->
                runCatching {
                    paymentApplicationService.reconcilePaymentAttempt(paymentAttempt.id)
                }
            }
    }
}
