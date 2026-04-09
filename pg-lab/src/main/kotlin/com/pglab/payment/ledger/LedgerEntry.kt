package com.pglab.payment.ledger

import com.pglab.payment.allocation.PaymentAllocation
import com.pglab.payment.authorization.Authorization
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
    val id: Long? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_order_id")
    var paymentOrder: PaymentOrder? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_allocation_id")
    var paymentAllocation: PaymentAllocation? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "authorization_id")
    var authorization: Authorization? = null,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    val type: LedgerEntryType = LedgerEntryType.AUTH_CAPTURED,
    @Embedded
    @AttributeOverrides(
        AttributeOverride(name = "amount", column = Column(name = "entry_amount", nullable = false)),
        AttributeOverride(name = "currency", column = Column(name = "entry_currency", nullable = false, length = 3)),
    )
    val amount: Money = Money(0L, CurrencyCode.KRW),
    @Column(nullable = false)
    val occurredAt: OffsetDateTime = OffsetDateTime.now(),
    @Column(nullable = false)
    val referenceTransactionId: String = "",
    @Column(nullable = false)
    val description: String = "",
)
