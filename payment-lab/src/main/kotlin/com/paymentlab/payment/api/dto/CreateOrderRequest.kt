package com.paymentlab.payment.api.dto

/**
 * 주문 생성 요청이다.
 *
 * @property amount 주문 금액
 */
data class CreateOrderRequest(
    val amount: Long,
)
