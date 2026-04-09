package com.paymentlab.payment.api.dto

import com.paymentlab.payment.domain.PaymentStatus

/**
 * 결제 승인 요청 응답이다.
 *
 * @property paymentAttemptId 승인 요청을 보낸 결제 시도 식별자
 * @property status 승인 요청 이후의 현재 상태
 * @property pgTransactionId PG가 부여한 거래 식별자
 */
data class ApprovePaymentAttemptResponse(
    val paymentAttemptId: Long,
    val status: PaymentStatus,
    val pgTransactionId: String,
)
