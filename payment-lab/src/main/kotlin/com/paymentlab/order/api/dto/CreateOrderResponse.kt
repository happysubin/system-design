package com.paymentlab.order.api.dto

/**
 * 주문 생성 응답이다.
 *
 * @property orderId 내부 주문 식별자
 * @property merchantOrderId 서버가 발급한 외부 주문 키
 * @property checkoutKey 결제 시작 전에 사용할 checkout key
 * @property amount 주문 금액
 * @property items 저장된 주문 라인 아이템 목록
 */
data class CreateOrderResponse(
    val orderId: Long,
    val merchantOrderId: String,
    val checkoutKey: String,
    val amount: Long,
    val items: List<CreateOrderResponseItem> = emptyList(),
)

data class CreateOrderResponseItem(
    val skuId: Long,
    val quantity: Int,
    val unitPrice: Long,
)
