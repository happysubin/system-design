package com.paymentlab.payment.api.dto

import com.paymentlab.payment.domain.PaymentStatus

/**
 * 재확정 요청 응답이다.
 *
 * @property paymentAttemptId 재조회 후 확정된 결제 시도 식별자
 * @property status 재조회 후 최종 상태
 */
data class ReconcilePaymentAttemptResponse(
    val paymentAttemptId: Long,
    val status: PaymentStatus,
)
