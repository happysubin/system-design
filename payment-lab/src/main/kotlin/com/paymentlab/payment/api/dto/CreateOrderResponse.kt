package com.paymentlab.payment.api.dto

/**
 * 주문 생성 응답이다.
 *
 * @property orderId 내부 주문 식별자
 * @property merchantOrderId 서버가 발급한 외부 주문 키
 * @property amount 주문 금액
 */
data class CreateOrderResponse(
    val orderId: Long,
    val merchantOrderId: String,
    val amount: Long,
)
