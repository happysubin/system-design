package com.pglab.payment.ledger

import com.pglab.payment.allocation.PaymentAllocation
import com.pglab.payment.authorization.Authorization
import com.pglab.payment.order.PaymentOrder
import com.pglab.payment.order.PaymentOrderLine
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
import java.time.OffsetDateTime

/**
 * 승인, 취소, 환불, 수수료, 정산 같은 금전 사실을 append-only로 남기는 공식 원장 엔트리다.
 *
 * 현재 상태 조회는 다른 엔티티가 도와줄 수 있지만,
 * 금액 변화의 진실은 이 엔티티의 누적 기록으로 판단한다.
 * 따라서 이 엔티티는 상태를 덮어쓰는 용도가 아니라,
 * 어떤 사건이 언제 어떤 금액으로 발생했는지를 계속 추가하는 장부 역할을 맡는다.
 */
@Entity
@Table(name = "ledger_entries")
class LedgerEntry(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    /**
     * 데이터베이스에서 사용하는 내부 식별자다.
     *
     * 원장 엔트리는 append-only로 계속 누적되므로,
     * 각 금전 사실을 독립적으로 참조하기 위한 안정적인 키가 필요하다.
     */
    val id: Long? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_order_id")
    /**
     * 이 원장 사실이 어느 상위 결제 주문에 속하는지 나타낸다.
     *
     * 주문 기준 집계나 고객 결제 흐름 추적은 이 연결을 통해 이루어진다.
     */
    var paymentOrder: PaymentOrder? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_allocation_id")
    /**
     * 이 금전 사실이 어느 부담 단위에 대응하는지 나타낸다.
     *
     * 더치페이나 부담자별 정산 집계를 하려면 allocation 단위 연결이 필요하다.
     */
    var paymentAllocation: PaymentAllocation? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "authorization_id")
    /**
     * 이 원장 사실이 어느 승인 결과와 직접 연결되는지 나타낸다.
     *
     * 승인, 취소, 환불처럼 승인 단위로 따라가야 하는 이벤트는 이 연결을 통해 추적한다.
     */
    var authorization: Authorization? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_order_line_id")
    var paymentOrderLine: PaymentOrderLine? = null,
    @Column(nullable = false)
    val payeeId: String = "",
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    /**
     * 이 엔트리가 어떤 금전 사실인지 구분하는 분류값이다.
     *
     * 같은 금액이라도 승인 확정인지, 취소인지, 환불인지에 따라 집계 의미가 달라지므로
     * 원장 타입은 후속 계산의 핵심 기준이 된다.
     */
    val type: LedgerEntryType = LedgerEntryType.AUTH_CAPTURED,
    @Embedded
    @AttributeOverrides(
        AttributeOverride(name = "amount", column = Column(name = "entry_amount", nullable = false)),
        AttributeOverride(name = "currency", column = Column(name = "entry_currency", nullable = false, length = 3)),
    )
    /**
     * 이 원장 엔트리가 기록하는 실제 금액 변화다.
     *
     * 부호 기반 계산 대신 타입과 함께 해석하는 구조를 상정하고 있으므로,
     * 집계 로직은 이 값과 type을 함께 읽어야 한다.
     */
    val amount: Money = Money(0L, CurrencyCode.KRW),
    @Column(nullable = false)
    /**
     * 이 금전 사실이 비즈니스적으로 발생한 시각이다.
     *
     * 정산 기간 계산, 시간순 원장 재구성, 사후 감사 추적에 직접 사용된다.
     */
    val occurredAt: OffsetDateTime = OffsetDateTime.now(),
    @Column(nullable = false)
    /**
     * 외부 거래나 내부 원장 흐름을 대조하기 위한 참조 식별자다.
     *
     * 같은 authorization 아래 여러 원장 엔트리가 생기더라도,
     * 외부 시스템 로그와 연결할 때는 별도 참조 키가 유용하다.
     */
    val referenceTransactionId: String = "",
    @Column(nullable = false)
    /**
     * 운영자 또는 후속 로직이 엔트리의 맥락을 이해하기 위한 보조 설명이다.
     *
     * 계산 기준이 되는 핵심 필드는 아니지만,
     * 장애 분석이나 수동 검토에서 엔트리 생성 배경을 파악하는 데 도움을 준다.
     */
    val description: String = "",
) {
    init {
        paymentOrderLine?.let { orderLine ->
            require(payeeId == orderLine.payeeId) {
                "ledger entry payee must match payment order line payee"
            }
            require(amount.currency == orderLine.lineAmount.currency) {
                "ledger entry currency must match payment order line currency"
            }
            paymentOrder?.let { order ->
                require(orderLine.paymentOrder == order) {
                    "ledger entry payment order line must belong to the same payment order"
                }
            }
        }
    }
}
