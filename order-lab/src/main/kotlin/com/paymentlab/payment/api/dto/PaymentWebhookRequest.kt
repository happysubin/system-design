package com.paymentlab.payment.api.dto

/**
 * PG 웹훅 요청 payload다.
 *
 * @property merchantOrderId PG와 대사하는 주문 키
 * @property pgTransactionId PG 거래 식별자
 * @property secret 토스 웹훅 검증용 secret
 * @property result PG가 통지한 성공/실패 결과
 */
data class PaymentWebhookRequest(
    val merchantOrderId: String,
    val pgTransactionId: String,
    val secret: String,
    val result: String,
)
