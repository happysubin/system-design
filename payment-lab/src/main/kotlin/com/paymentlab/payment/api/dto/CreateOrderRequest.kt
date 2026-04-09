package com.paymentlab.payment.api.dto

/**
 * 주문 생성 요청이다.
 *
 * @property amount 주문 금액
 * @property items 주문 라인 아이템 목록
 */
data class CreateOrderRequest(
    val amount: Long,
    val items: List<CreateOrderItemRequest> = emptyList(),
)

data class CreateOrderItemRequest(
    val skuId: Long,
    val quantity: Int,
    val unitPrice: Long,
)
