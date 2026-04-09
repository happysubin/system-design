package com.pglab.payment.allocation

import com.pglab.payment.order.PaymentOrder
import com.pglab.payment.shared.CurrencyCode
import com.pglab.payment.shared.Money
import jakarta.persistence.AttributeOverride
import jakarta.persistence.AttributeOverrides
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

/**
 * 하나의 결제 주문을 실제 부담 단위로 나눈다.
 *
 * 이 엔티티를 두는 이유는 주문 전체와 실제 부담 주체를 분리하기 위해서다.
 * 더치페이에서는 한 주문 아래 여러 payer가 생기고,
 * 복합결제에서는 한 payer의 부담분 아래 다시 여러 승인 건이 생길 수 있다.
 *
 * 즉 PaymentAllocation은 "누가 얼마를 부담하는가"를 표현하는 축이며,
 * Authorization은 그 부담분에 대해 "어떤 수단으로 실제 승인이 났는가"를 표현한다.
 */
@Entity
@Table(name = "payment_allocations")
class PaymentAllocation(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    /**
     * 데이터베이스에서 사용하는 내부 식별자다.
     *
     * 같은 PaymentOrder 아래 여러 allocation이 생길 수 있으므로,
     * 각 부담 단위를 안정적으로 식별하기 위한 별도 키가 필요하다.
     */
    val id: Long? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_order_id")
    /**
     * 이 부담 단위가 속한 상위 결제 주문이다.
     *
     * allocation은 항상 PaymentOrder의 일부이며,
     * 단독으로 존재하는 결제 시도 단위가 아니라 주문 내부 분해 결과라는 점을 나타낸다.
     */
    var paymentOrder: PaymentOrder? = null,
    @Column(nullable = false)
    /**
     * 실제 부담 주체를 식별하는 참조값이다.
     *
     * 회원 ID, 사용자 키, 임시 참여자 토큰 등 구현 상황에 따라 다양한 식별자를 담을 수 있다.
     * 더치페이에서는 누가 얼마를 책임지는지 구분하는 핵심 키가 된다.
     */
    val payerReference: String = "",
    @Embedded
    @AttributeOverrides(
        AttributeOverride(name = "amount", column = Column(name = "allocation_amount", nullable = false)),
        AttributeOverride(name = "currency", column = Column(name = "allocation_currency", nullable = false, length = 3)),
    )
    /**
     * 이 부담 단위가 실제로 책임지는 금액이다.
     *
     * PaymentOrder.totalAmount는 여러 allocation의 합으로 설명되어야 하므로,
     * 주문 전체를 분해했을 때 이 값이 분담 구조의 기준이 된다.
     */
    val allocationAmount: Money = Money(0L, CurrencyCode.KRW),
    @Column(nullable = false)
    /**
     * 같은 주문 안에서 allocation을 안정적으로 정렬하거나 표시하기 위한 순번이다.
     *
     * 더치페이 UI나 후속 처리에서 사용자에게 보여준 입력 순서를 보존할 때 유용하다.
     */
    val sequence: Int = 0,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    /**
     * 부담 단위 기준 상태다.
     *
     * 주문 전체 상태도 아니고 개별 승인 상태도 아니며,
     * 특정 payer의 부담분이 현재 어디까지 처리되었는지 요약하는 상태값이다.
     */
    var status: PaymentAllocationStatus = PaymentAllocationStatus.READY,
)
