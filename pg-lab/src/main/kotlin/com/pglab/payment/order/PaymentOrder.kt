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
    /**
     * 데이터베이스에서 사용하는 내부 식별자다.
     *
     * 외부 주문 번호와 분리된 surrogate key로 두어,
     * 외부 시스템 식별자 규칙이 바뀌어도 영속성 식별 체계가 흔들리지 않게 한다.
     */
    val id: Long? = null,
    @Column(nullable = false, unique = true)
    /**
     * 가맹점 또는 외부 주문 시스템이 들고 있는 결제 주문 식별자다.
     *
     * 고객 문의, 웹훅 추적, 외부 주문 시스템과의 대조는 이 값 기준으로 이루어질 가능성이 높다.
     */
    val merchantOrderId: String = "",
    @Embedded
    @AttributeOverrides(
        AttributeOverride(name = "amount", column = Column(name = "total_amount", nullable = false)),
        AttributeOverride(name = "currency", column = Column(name = "currency", nullable = false, length = 3)),
    )
    /**
     * 주문 전체 기준의 결제 대상 총액이다.
     *
     * 더치페이나 복합결제로 내부 흐름이 여러 갈래로 나뉘더라도,
     * 최종적으로는 모든 allocation 합계가 이 값과 일치해야 한다.
     */
    val totalAmount: Money = Money(0L, CurrencyCode.KRW),
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    /**
     * 주문 관점의 상위 상태다.
     *
     * 개별 승인 건의 세부 상태를 그대로 복제하는 값이 아니라,
     * 주문 전체가 현재 어느 단계에 와 있는지 요약해서 보여주는 용도다.
     */
    var status: PaymentOrderStatus = PaymentOrderStatus.READY,
)
