package com.paymentlab.payment.api.dto

import com.paymentlab.payment.domain.PaymentStatus

/**
 * 웹훅 처리 결과 응답이다.
 *
 * @property paymentAttemptId 최종 상태를 반영한 결제 시도 식별자
 * @property status 웹훅 처리 후 결제 시도 상태
 */
data class PaymentWebhookResponse(
    val paymentAttemptId: Long,
    val status: PaymentStatus,
)
