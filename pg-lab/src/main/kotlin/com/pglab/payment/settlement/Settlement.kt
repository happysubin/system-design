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
    /**
     * 데이터베이스에서 사용하는 내부 식별자다.
     *
     * 정산 배치나 지급 단위를 독립적으로 참조하기 위한 키이며,
     * 주문/승인 식별자와는 별도의 정산 문맥을 나타낸다.
     */
    val id: Long? = null,
    @Column(nullable = false)
    /**
     * 정산 대상이 되는 가맹점 식별자다.
     *
     * 결제는 고객 관점 흐름이지만 정산은 가맹점 관점 집계이므로,
     * 정산 단위에서는 merchant 식별이 별도 축이 된다.
     */
    val merchantId: String = "",
    @Embedded
    @AttributeOverrides(
        AttributeOverride(name = "amount", column = Column(name = "gross_amount", nullable = false)),
        AttributeOverride(name = "currency", column = Column(name = "gross_currency", nullable = false, length = 3)),
    )
    /**
     * 정산 기준 총액이다.
     *
     * 수수료나 차감 항목을 반영하기 전의 원시 금액으로,
     * 이후 feeAmount와 netAmount를 해석하는 출발점 역할을 한다.
     */
    val grossAmount: Money = Money(0L, CurrencyCode.KRW),
    @Embedded
    @AttributeOverrides(
        AttributeOverride(name = "amount", column = Column(name = "fee_amount", nullable = false)),
        AttributeOverride(name = "currency", column = Column(name = "fee_currency", nullable = false, length = 3)),
    )
    /**
     * 정산 과정에서 차감되는 수수료 금액이다.
     *
     * 가맹점 지급액과 PG 수익 또는 비용 구분을 만들기 위해 별도로 보관한다.
     */
    val feeAmount: Money = Money(0L, CurrencyCode.KRW),
    @Embedded
    @AttributeOverrides(
        AttributeOverride(name = "amount", column = Column(name = "net_amount", nullable = false)),
        AttributeOverride(name = "currency", column = Column(name = "net_currency", nullable = false, length = 3)),
    )
    /**
     * 실제 지급 대상이 되는 순액이다.
     *
     * grossAmount에서 수수료와 기타 차감을 반영한 결과로,
     * 정산 완료 시 가맹점에 지급되는 실질 금액을 의미한다.
     */
    val netAmount: Money = Money(0L, CurrencyCode.KRW),
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    /**
     * 정산 준비, 예정, 완료 같은 지급 관점 상태다.
     *
     * 결제 성공 여부와는 별개로,
     * 가맹점 지급 프로세스가 어느 단계에 있는지 표현한다.
     */
    var status: SettlementStatus = SettlementStatus.READY,
    @Column
    /**
     * 정산이 예정된 영업일 또는 지급 예정일이다.
     *
     * 실제 완료 시각과 분리해서 보관함으로써,
     * 예정 대비 지연 여부나 배치 스케줄 상태를 표현할 수 있다.
     */
    val scheduledDate: LocalDate? = null,
    @Column
    /**
     * 정산이 실제로 완료된 시각이다.
     *
     * 예정일과 비교하거나 지급 완료 감사 추적을 남길 때 기준이 된다.
     */
    val settledAt: OffsetDateTime? = null,
)
