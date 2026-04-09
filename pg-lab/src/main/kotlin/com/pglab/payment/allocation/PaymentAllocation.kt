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
    val id: Long? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_order_id")
    var paymentOrder: PaymentOrder? = null,
    @Column(nullable = false)
    val payerReference: String = "",
    @Embedded
    @AttributeOverrides(
        AttributeOverride(name = "amount", column = Column(name = "allocation_amount", nullable = false)),
        AttributeOverride(name = "currency", column = Column(name = "allocation_currency", nullable = false, length = 3)),
    )
    val allocationAmount: Money = Money(0L, CurrencyCode.KRW),
    @Column(nullable = false)
    val sequence: Int = 0,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var status: PaymentAllocationStatus = PaymentAllocationStatus.READY,
)
