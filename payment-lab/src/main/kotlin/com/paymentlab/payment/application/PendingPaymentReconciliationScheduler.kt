package com.paymentlab.payment.application

import com.paymentlab.payment.domain.PaymentStatus
import com.paymentlab.payment.infrastructure.persistence.PaymentAttemptRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
/**
 * `PENDING` 상태로 남아 있는 결제 시도를 주기적으로 재확정하는 스케줄러다.
 *
 * 웹훅이 늦거나 누락되거나,
 * PG 응답이 불명확해서 결제를 즉시 확정하지 못한 경우를
 * 백그라운드에서 다시 조회해 상태를 맞추는 역할을 한다.
 */
class PendingPaymentReconciliationScheduler(
    private val paymentAttemptRepository: PaymentAttemptRepository,
    private val paymentFacade: PaymentFacade,
) {
    @Scheduled(fixedDelay = 300000)
    fun reconcilePendingPayments() {
        paymentAttemptRepository.findAllByStatus(PaymentStatus.PENDING)
            .forEach { paymentAttempt ->
                runCatching {
                    paymentFacade.reconcilePaymentAttempt(paymentAttempt.id)
                }
            }
    }
}
