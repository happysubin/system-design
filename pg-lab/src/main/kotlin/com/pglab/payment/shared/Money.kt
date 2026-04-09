package com.pglab.payment.shared

import jakarta.persistence.Column
import jakarta.persistence.Embeddable

/**
 * 도메인 전반에서 공통으로 사용하는 금액 값 객체다.
 *
 * 현재 모델은 KRW 중심의 정수 금액을 다루므로 `Long`을 사용한다.
 * 결제, 승인, 원장, 정산이 모두 동일한 표현을 써야 집계와 비교 규칙이 흔들리지 않기 때문에
 * 금액과 통화를 항상 함께 들고 다니도록 강제한다.
 */
@Embeddable
data class Money(
    @Column(name = "amount", nullable = false)
    val amount: Long,
    val currency: CurrencyCode,
) {
    init {
        require(amount >= 0) { "amount must be zero or positive" }
    }
}
