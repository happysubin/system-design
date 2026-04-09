package com.paymentlab.payment.api.dto

/**
 * 결제 시도 생성 요청이다.
 *
 * @property orderId 결제를 시작할 주문 식별자
 * @property merchantOrderId 외부 주문서 시스템이 관리하는 주문 키
 * @property amount 결제 시작 시점의 주문 금액 스냅샷
 * @property checkoutKey 서버가 발급한 주문서 진입용 체크아웃 키이자 결제 시작 중복 방지 키
 */
data class CreatePaymentAttemptRequest(
    val orderId: Long,
    val merchantOrderId: String,
    val amount: Long,
    val checkoutKey: String,
)
