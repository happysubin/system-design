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
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.OffsetDateTime

/**
 * 실제 송금 시도와 그 결과를 기록하는 지급 엔티티다.
 *
 * Settlement가 계산된 지급 대상 금액을 표현한다면,
 * Payout은 그 돈을 실제 계좌로 송금하려고 시도한 실행 이력을 표현한다.
 */
@Entity
@Table(name = "payouts")
class Payout(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "settlement_id", nullable = false)
    val settlement: Settlement? = null,
    @Embedded
    @AttributeOverrides(
        AttributeOverride(name = "amount", column = Column(name = "requested_amount", nullable = false)),
        AttributeOverride(name = "currency", column = Column(name = "requested_currency", nullable = false, length = 3)),
    )
    val requestedAmount: Money = Money(0L, CurrencyCode.KRW),
    @Column(nullable = false, length = 16)
    val bankCode: String = "",
    @Column(nullable = false, length = 64)
    val bankAccountNumber: String = "",
    @Column(nullable = false, length = 128)
    val accountHolderName: String = "",
    @Column(nullable = false, unique = true, length = 128)
    val bankTransferRequestId: String = "",
    @Column(length = 128)
    var bankTransferTransactionId: String? = null,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var status: PayoutStatus = PayoutStatus.REQUESTED,
    @Column(nullable = false)
    val requestedAt: OffsetDateTime = OffsetDateTime.now(),
    @Column
    var sentAt: OffsetDateTime? = null,
    @Column
    var completedAt: OffsetDateTime? = null,
    @Column(length = 64)
    var failureCode: String? = null,
    @Column(length = 1000)
    var failureReason: String? = null,
    @Column(nullable = false)
    val retryCount: Int = 0,
) {
    fun markSent(sentAt: OffsetDateTime) {
        status = PayoutStatus.SENT
        this.sentAt = sentAt
    }

    fun markSucceeded(bankTransferTransactionId: String, completedAt: OffsetDateTime) {
        status = PayoutStatus.SUCCEEDED
        this.bankTransferTransactionId = bankTransferTransactionId
        this.completedAt = completedAt
    }

    fun markFailed(failureCode: String, failureReason: String, completedAt: OffsetDateTime) {
        status = PayoutStatus.FAILED
        this.failureCode = failureCode
        this.failureReason = failureReason
        this.completedAt = completedAt
    }
}
