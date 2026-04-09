package com.pglab.payment.order

import com.pglab.payment.shared.CurrencyCode
import com.pglab.payment.shared.Money
import jakarta.persistence.AttributeOverride
import jakarta.persistence.AttributeOverrides
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * 고객이나 가맹점이 인식하는 상위 결제 주문이다.
 *
 * 이 엔티티는 "무엇을 얼마 결제하려고 하는가"라는 비즈니스 의도를 표현한다.
 * 실제 승인 결과나 원장 기록까지 직접 품지 않고,
 * 여러 부담 단위와 여러 결제수단을 묶는 최상위 컨텍스트 역할에 집중한다.
 *
 * 따라서 더치페이와 복합결제처럼 한 주문 아래에 여러 결제 흐름이 생기더라도
 * 주문 자체의 총액과 상위 상태는 이 엔티티 하나로 추적할 수 있어야 한다.
 */
@Entity
@Table(name = "payment_orders")
class PaymentOrder(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(nullable = false, unique = true)
    val merchantOrderId: String = "",
    @Embedded
    @AttributeOverrides(
        AttributeOverride(name = "amount", column = Column(name = "total_amount", nullable = false)),
        AttributeOverride(name = "currency", column = Column(name = "currency", nullable = false, length = 3)),
    )
    val totalAmount: Money = Money(0L, CurrencyCode.KRW),
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var status: PaymentOrderStatus = PaymentOrderStatus.READY,
)
