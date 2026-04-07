package com.paymentlab.payment.application

import com.paymentlab.payment.domain.PaymentAttempt
import com.paymentlab.payment.domain.PaymentStatus
import com.paymentlab.payment.infrastructure.persistence.PaymentAttemptRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.BDDMockito.given

@ExtendWith(MockitoExtension::class)
class PendingPaymentReconciliationSchedulerTest {

    @Mock
    lateinit var paymentAttemptRepository: PaymentAttemptRepository

    @Mock
    lateinit var paymentApplicationService: PaymentApplicationService

    @Test
    fun `pending 결제 시도만 재확정 대상으로 호출한다`() {
        val scheduler = PendingPaymentReconciliationScheduler(paymentAttemptRepository, paymentApplicationService)
        given(paymentAttemptRepository.findAllByStatus(PaymentStatus.PENDING)).willReturn(
            listOf(
                PaymentAttempt(id = 1, orderId = 1, merchantOrderId = "order-1", checkoutKey = "checkout-1", amount = 1000, status = PaymentStatus.PENDING),
                PaymentAttempt(id = 2, orderId = 2, merchantOrderId = "order-2", checkoutKey = "checkout-2", amount = 2000, status = PaymentStatus.PENDING),
            ),
        )

        scheduler.reconcilePendingPayments()

        verify(paymentApplicationService).reconcilePaymentAttempt(1)
        verify(paymentApplicationService).reconcilePaymentAttempt(2)
    }

    @Test
    fun `pending 결제가 없으면 재확정을 호출하지 않는다`() {
        val scheduler = PendingPaymentReconciliationScheduler(paymentAttemptRepository, paymentApplicationService)
        given(paymentAttemptRepository.findAllByStatus(PaymentStatus.PENDING)).willReturn(emptyList())

        scheduler.reconcilePendingPayments()

        verify(paymentApplicationService, never()).reconcilePaymentAttempt(org.mockito.ArgumentMatchers.anyLong())
    }
}
