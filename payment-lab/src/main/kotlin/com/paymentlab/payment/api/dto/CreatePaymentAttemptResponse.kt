package com.paymentlab.payment.api.dto

import com.paymentlab.payment.domain.PaymentStatus

/**
 * 결제 시도 생성 응답이다.
 *
 * @property paymentAttemptId 생성되거나 재사용된 결제 시도 식별자
 * @property orderId 결제 시도가 연결된 주문 식별자
 * @property status 현재 결제 시도 상태
 */
data class CreatePaymentAttemptResponse(
    val paymentAttemptId: Long,
    val orderId: Long,
    val status: PaymentStatus,
)
