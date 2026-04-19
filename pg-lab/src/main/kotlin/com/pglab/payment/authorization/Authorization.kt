package com.pglab.payment.authorization

import com.pglab.payment.allocation.PaymentAllocation
import com.pglab.payment.shared.CurrencyCode
import com.pglab.payment.shared.Money
import jakarta.persistence.CascadeType
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
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.time.OffsetDateTime

/**
 * 외부 결제수단이 실제로 승인한 결과를 나타낸다.
 *
 * 결제 의도는 PaymentOrder/PaymentAllocation이 맡고,
 * 이 엔티티는 카드/계좌 같은 수단별 승인 결과와 취소 가능 잔액 같은 운영 조회값을 맡는다.
 * 부분 취소나 부분 환불이 들어오면 원장에는 별도 엔트리가 쌓이고,
 * 이 엔티티는 운영 조회를 빠르게 하기 위한 요약 상태를 함께 유지한다.
 */
@Entity
@Table(name = "authorizations")
class Authorization(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    /**
     * 데이터베이스에서 사용하는 내부 식별자다.
     *
     * 외부 PG 거래번호와 분리된 내부 키를 둬야,
     * 외부 연동 식별자 변경 여부와 무관하게 시스템 내부 참조가 안정적으로 유지된다.
     */
    val id: Long? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_allocation_id")
    /**
     * 이 승인 결과가 어느 부담 단위에 속하는지 나타낸다.
     *
     * 한 allocation 아래 여러 승인 건이 생길 수 있으므로,
     * 이 연관관계는 복합결제와 재시도 시나리오를 설명하는 핵심 축이다.
     */
    var paymentAllocation: PaymentAllocation? = null,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    /**
     * 실제 승인에 사용된 결제수단 유형이다.
     *
     * 같은 allocation이라도 카드와 계좌가 함께 쓰일 수 있으므로,
     * 승인 결과는 반드시 수단 단위로 구분되어야 한다.
     */
    val instrumentType: InstrumentType = InstrumentType.CARD,
    @Embedded
    @AttributeOverrides(
        AttributeOverride(name = "amount", column = Column(name = "requested_amount", nullable = false)),
        AttributeOverride(name = "currency", column = Column(name = "requested_currency", nullable = false, length = 3)),
    )
    /**
     * 외부 결제수단에 실제로 승인 요청한 금액이다.
     *
     * 승인 성공 금액과 항상 같다고 단정하지 않고 별도 보관함으로써,
     * 부분 승인이나 승인 실패 분석이 필요한 확장 지점을 남겨둔다.
     */
    val requestedAmount: Money = Money(0L, CurrencyCode.KRW),
    @Embedded
    @AttributeOverrides(
        AttributeOverride(name = "amount", column = Column(name = "approved_amount", nullable = false)),
        AttributeOverride(name = "currency", column = Column(name = "approved_currency", nullable = false, length = 3)),
    )
    /**
     * 외부 금융망이 최종적으로 승인한 금액이다.
     *
     * 취소 가능 금액과 환불 가능 금액의 초기 기준점이 되며,
     * 부분 취소/환불 누적 한도를 판단할 때도 이 값이 기준이 된다.
     */
    val approvedAmount: Money = Money(0L, CurrencyCode.KRW),
    @Embedded
    @AttributeOverrides(
        AttributeOverride(name = "amount", column = Column(name = "remaining_cancelable_amount", nullable = false)),
        AttributeOverride(name = "currency", column = Column(name = "remaining_cancelable_currency", nullable = false, length = 3)),
    )
    /**
     * 아직 취소 가능한 잔액이다.
     *
     * 원장의 진실을 대체하는 값은 아니지만,
     * 운영 조회에서 "현재 얼마까지 취소 가능한가"를 즉시 보여주기 위한 요약 필드다.
     */
    var remainingCancelableAmount: Money = approvedAmount,
    @Embedded
    @AttributeOverrides(
        AttributeOverride(name = "amount", column = Column(name = "remaining_refundable_amount", nullable = false)),
        AttributeOverride(name = "currency", column = Column(name = "remaining_refundable_currency", nullable = false, length = 3)),
    )
    /**
     * 아직 환불 가능한 잔액이다.
     *
     * 환불도 원장 엔트리 누적으로 판단해야 하지만,
     * 실시간 운영 화면과 검증 로직에서는 이 값을 빠른 조회용 캐시처럼 활용할 수 있다.
     */
    var remainingRefundableAmount: Money = approvedAmount,
    @Column(nullable = false, unique = true)
    /**
     * PG 내부 또는 외부 연동에서 이 승인 결과를 대표하는 거래 식별자다.
     *
     * 원장 참조, 취소 요청, 장애 분석, 외부 로그 대조 같은 작업에서 중심 키 역할을 한다.
     */
    val pgTransactionId: String = "",
    @Column
    /**
     * 사람이 확인하거나 외부 시스템과 대조할 때 쓰는 승인 코드다.
     *
     * 카드 승인번호처럼 고객센터나 운영 대응에서 직접 확인하는 값을 담는 자리를 의도한다.
     */
    val approvalCode: String? = null,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    /**
     * 승인 결과 기준의 현재 운영 상태다.
     *
     * 이 값은 원장에 이미 기록된 사건을 빠르게 요약해서 보여주기 위한 상태이며,
     * append-only 원장 자체를 대체하지는 않는다.
     */
    var status: AuthorizationStatus = AuthorizationStatus.APPROVED,
    @Column
    /**
     * 승인 확정 시각이다.
     *
     * 승인 시점 추적, 취소 허용 시간 계산, 외부 거래 대조 같은 후속 로직의 기준 시각이 된다.
     */
    val approvedAt: OffsetDateTime? = null,
    @OneToMany(mappedBy = "authorizationInternal", cascade = [CascadeType.ALL], orphanRemoval = true)
    private val linePortionsInternal: MutableList<AuthorizationLinePortion> = mutableListOf(),
) {
    val linePortions: List<AuthorizationLinePortion>
        get() = linePortionsInternal

    fun addLinePortion(linePortion: AuthorizationLinePortion) {
        require(linePortion.authorization == null || linePortion.authorization == this) {
            "authorization line portion already belongs to another authorization"
        }

        val allocationOrder = requireNotNull(paymentAllocation?.paymentOrder) {
            "authorization payment allocation must belong to a payment order"
        }
        require(linePortion.paymentOrderLine?.paymentOrder == allocationOrder) {
            "authorization line portion must belong to the same payment order as the authorization"
        }

        val updatedPortionAmount = linePortionsInternal.sumOf { it.amount.amount } + linePortion.amount.amount
        require(updatedPortionAmount <= approvedAmount.amount) {
            "authorization line portions must not exceed approved amount"
        }
        require(linePortionsInternal.none { it.paymentOrderLine == linePortion.paymentOrderLine }) {
            "authorization must not contain multiple line portions for the same order line"
        }

        if (linePortionsInternal.contains(linePortion)) {
            return
        }

        linePortion.attachTo(this)
        linePortionsInternal.add(linePortion)
    }

    fun removeLinePortion(linePortion: AuthorizationLinePortion) {
        if (linePortionsInternal.remove(linePortion)) {
            linePortion.detach()
        }
    }

    /**
     * 승인 금액 일부를 취소 처리한다.
     *
     * 원장 관점의 취소 사실은 별도 LedgerEntry로 남아야 하고,
     * 이 메서드는 그 결과를 운영 조회용 잔액과 상태에 반영하는 최소 도메인 규칙을 담는다.
     */
    fun cancel(amount: Money) {
        require(amount.amount > 0L) {
            "cancel amount must be greater than zero"
        }
        require(amount.currency == remainingCancelableAmount.currency) {
            "cancel currency must match remaining cancelable currency"
        }
        require(amount.amount <= remainingCancelableAmount.amount) {
            "cancel amount must not exceed remaining cancelable amount"
        }

        val remainingAmount = remainingCancelableAmount.amount - amount.amount
        remainingCancelableAmount = Money(remainingAmount, remainingCancelableAmount.currency)
        status = if (remainingAmount == 0L) {
            AuthorizationStatus.CANCELED
        } else {
            AuthorizationStatus.PARTIALLY_CANCELED
        }
    }
}
