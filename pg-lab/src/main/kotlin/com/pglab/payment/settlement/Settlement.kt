package com.pglab.payment.settlement

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
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * 원장에 기록된 금액 흐름을 정산 관점으로 묶는 단위다.
 *
 * 승인과 취소는 고객 결제 흐름의 사실이고,
 * 정산은 그 결과를 가맹점 지급 관점으로 다시 해석한 결과다.
 * 따라서 정산 엔티티는 결제 성공 여부 자체보다
 * 얼마를 언제 지급 예정/완료로 볼 것인지에 초점을 맞춘다.
 */
@Entity
@Table(name = "settlements")
class Settlement(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(nullable = false)
    val merchantId: String = "",
    @Embedded
    @AttributeOverrides(
        AttributeOverride(name = "amount", column = Column(name = "gross_amount", nullable = false)),
        AttributeOverride(name = "currency", column = Column(name = "gross_currency", nullable = false, length = 3)),
    )
    val grossAmount: Money = Money(0L, CurrencyCode.KRW),
    @Embedded
    @AttributeOverrides(
        AttributeOverride(name = "amount", column = Column(name = "fee_amount", nullable = false)),
        AttributeOverride(name = "currency", column = Column(name = "fee_currency", nullable = false, length = 3)),
    )
    val feeAmount: Money = Money(0L, CurrencyCode.KRW),
    @Embedded
    @AttributeOverrides(
        AttributeOverride(name = "amount", column = Column(name = "net_amount", nullable = false)),
        AttributeOverride(name = "currency", column = Column(name = "net_currency", nullable = false, length = 3)),
    )
    val netAmount: Money = Money(0L, CurrencyCode.KRW),
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var status: SettlementStatus = SettlementStatus.READY,
    @Column
    val scheduledDate: LocalDate? = null,
    @Column
    val settledAt: OffsetDateTime? = null,
)
