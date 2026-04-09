package com.pglab.payment.authorization

import com.pglab.payment.allocation.PaymentAllocation
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
    val id: Long? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_allocation_id")
    var paymentAllocation: PaymentAllocation? = null,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    val instrumentType: InstrumentType = InstrumentType.CARD,
    @Embedded
    @AttributeOverrides(
        AttributeOverride(name = "amount", column = Column(name = "requested_amount", nullable = false)),
        AttributeOverride(name = "currency", column = Column(name = "requested_currency", nullable = false, length = 3)),
    )
    val requestedAmount: Money = Money(0L, CurrencyCode.KRW),
    @Embedded
    @AttributeOverrides(
        AttributeOverride(name = "amount", column = Column(name = "approved_amount", nullable = false)),
        AttributeOverride(name = "currency", column = Column(name = "approved_currency", nullable = false, length = 3)),
    )
    val approvedAmount: Money = Money(0L, CurrencyCode.KRW),
    @Embedded
    @AttributeOverrides(
        AttributeOverride(name = "amount", column = Column(name = "remaining_cancelable_amount", nullable = false)),
        AttributeOverride(name = "currency", column = Column(name = "remaining_cancelable_currency", nullable = false, length = 3)),
    )
    var remainingCancelableAmount: Money = approvedAmount,
    @Embedded
    @AttributeOverrides(
        AttributeOverride(name = "amount", column = Column(name = "remaining_refundable_amount", nullable = false)),
        AttributeOverride(name = "currency", column = Column(name = "remaining_refundable_currency", nullable = false, length = 3)),
    )
    var remainingRefundableAmount: Money = approvedAmount,
    @Column(nullable = false, unique = true)
    val pgTransactionId: String = "",
    @Column
    val approvalCode: String? = null,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var status: AuthorizationStatus = AuthorizationStatus.APPROVED,
    @Column
    val approvedAt: OffsetDateTime? = null,
) {
    /**
     * 승인 금액 일부를 취소 처리한다.
     *
     * 원장 관점의 취소 사실은 별도 LedgerEntry로 남아야 하고,
     * 이 메서드는 그 결과를 운영 조회용 잔액과 상태에 반영하는 최소 도메인 규칙을 담는다.
     */
    fun cancel(amount: Money) {
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
