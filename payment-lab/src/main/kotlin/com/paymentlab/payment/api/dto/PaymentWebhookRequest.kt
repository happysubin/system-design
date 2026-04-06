package com.paymentlab.payment.api.dto

/**
 * PG 웹훅 요청 payload다.
 *
 * @property pgTransactionId PG 거래 식별자
 * @property result PG가 통지한 성공/실패 결과
 */
data class PaymentWebhookRequest(
    val pgTransactionId: String,
    val result: String,
)
