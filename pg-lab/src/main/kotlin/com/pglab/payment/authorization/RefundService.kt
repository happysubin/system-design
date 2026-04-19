package com.pglab.payment.authorization

import com.pglab.payment.allocation.PaymentAllocation
import com.pglab.payment.allocation.PaymentAllocationStatus
import com.pglab.payment.ledger.LedgerEntry
import com.pglab.payment.ledger.LedgerEntryType
import com.pglab.payment.order.PaymentOrder
import com.pglab.payment.order.PaymentOrderStatus
import com.pglab.payment.shared.Money
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Service
class RefundService(
    private val writer: RefundWriter,
) {
    @Transactional
    fun refund(command: RefundCommand): RefundResult {
        // 환불은 취소와 비슷해 보이지만 "남은 환불 가능 금액"을 기준으로 판단한다.
        // 따라서 먼저 authorization 단위 잔액을 줄여서 환불 가능 범위를 확정한다.
        refundAuthorization(command.authorization, command.refundAmount)

        val ledgerEntries = createLedgerEntries(command)

        // 상위 상태는 특정 authorization 하나가 아니라 allocation 전체 기준으로 재계산한다.
        // 모든 authorization의 환불 가능 잔액이 0이면 allocation/order 전체가 사실상 전부 환불된 상태다.
        val allocationRemaining = command.allocationAuthorizations.sumOf { it.remainingRefundableAmount.amount }
        val orderRemaining = command.orderAuthorizations.sumOf { it.remainingRefundableAmount.amount }
        if (allocationRemaining == 0L) {
            command.allocation.status = PaymentAllocationStatus.CANCELED
        } else {
            command.allocation.status = PaymentAllocationStatus.PARTIALLY_CANCELED
        }

        command.order.status = if (orderRemaining == 0L) {
            PaymentOrderStatus.CANCELED
        } else {
            PaymentOrderStatus.PARTIALLY_CANCELED
        }

        // 결과 객체는 환불 후 달라진 authorization과 원장, 상위 상태를 한 번에 묶어준다.
        val result = RefundResult(
            order = command.order,
            allocation = command.allocation,
            authorization = command.authorization,
            ledgerEntries = ledgerEntries,
        )

        return writer.save(result)
    }

    private fun createLedgerEntries(command: RefundCommand): List<LedgerEntry> {
        val orderedLinePortions = command.authorization.linePortions
            .sortedWith(compareBy<AuthorizationLinePortion>({ it.sequence }, { it.paymentOrderLine?.lineReference ?: "" }, { it.payeeId }))
        require(orderedLinePortions.isNotEmpty()) {
            "authorization line portions must exist for refund"
        }

        val existingNegativeAmountsByLineReference = command.existingNegativeLedgerEntries
            .asSequence()
            .filter { it.type == LedgerEntryType.CANCELLED || it.type == LedgerEntryType.REFUNDED }
            .groupBy { it.paymentOrderLine?.lineReference ?: "${it.payeeId}#${it.referenceTransactionId}" }
            .mapValues { (_, entries) -> entries.sumOf { it.amount.amount } }

        val remainingAmountsByLineReference = orderedLinePortions.associate { linePortion ->
            val lineReference = linePortion.paymentOrderLine?.lineReference ?: "${linePortion.payeeId}#${linePortion.sequence}"
            lineReference to (linePortion.amount.amount - (existingNegativeAmountsByLineReference[lineReference] ?: 0L)).coerceAtLeast(0L)
        }
        val activeLineReferences = orderedLinePortions
            .mapNotNull { linePortion ->
                val lineReference = linePortion.paymentOrderLine?.lineReference ?: return@mapNotNull null
                if ((remainingAmountsByLineReference[lineReference] ?: 0L) > 0L) lineReference else null
            }
        val totalRemainingAmount = activeLineReferences.sumOf { remainingAmountsByLineReference[it] ?: 0L }
        require(command.refundAmount.amount <= totalRemainingAmount) {
            "refund amount must not exceed remaining line portion amount"
        }

        val occurredAt = OffsetDateTime.now()
        var remainingAmountToAssign = command.refundAmount.amount
        val lastActiveLineReference = activeLineReferences.lastOrNull()

        return orderedLinePortions.mapNotNull { linePortion ->
            val lineReference = linePortion.paymentOrderLine?.lineReference ?: "${linePortion.payeeId}#${linePortion.sequence}"
            val remainingLineAmount = remainingAmountsByLineReference[lineReference] ?: 0L
            val splitAmount = if (remainingLineAmount == 0L) {
                0L
            } else if (lineReference == lastActiveLineReference) {
                remainingAmountToAssign
            } else {
                command.refundAmount.amount * remainingLineAmount / totalRemainingAmount
            }
            remainingAmountToAssign -= splitAmount

            if (splitAmount == 0L) {
                return@mapNotNull null
            }

            LedgerEntry(
                paymentOrder = command.order,
                paymentAllocation = command.allocation,
                authorization = command.authorization,
                paymentOrderLine = linePortion.paymentOrderLine,
                payeeId = linePortion.payeeId,
                type = LedgerEntryType.REFUNDED,
                amount = Money(splitAmount, command.refundAmount.currency),
                occurredAt = occurredAt,
                referenceTransactionId = command.authorization.pgTransactionId,
                description = "refund",
            )
        }
    }

    private fun refundAuthorization(authorization: Authorization, refundAmount: Money) {
        require(refundAmount.amount > 0L) {
            "refund amount must be greater than zero"
        }
        // 환불도 취소와 마찬가지로 통화가 다르면 같은 authorization 잔액에서 차감할 수 없다.
        require(refundAmount.currency == authorization.remainingRefundableAmount.currency) {
            "refund currency must match remaining refundable currency"
        }
        // 이미 환불한 금액을 초과해서 또 환불하면 실제 돈 이동과 로컬 잔액이 틀어지므로 차단한다.
        require(refundAmount.amount <= authorization.remainingRefundableAmount.amount) {
            "refund amount must not exceed remaining refundable amount"
        }

        // 환불 가능 잔액에서 실제 환불 금액만큼 차감하여 다음 환불 가능 범위를 갱신한다.
        authorization.remainingRefundableAmount = Money(
            authorization.remainingRefundableAmount.amount - refundAmount.amount,
            authorization.remainingRefundableAmount.currency,
        )
    }
}

interface RefundWriter {
    fun save(result: RefundResult): RefundResult
}

data class RefundCommand(
    val order: PaymentOrder,
    val allocation: PaymentAllocation,
    val authorization: Authorization,
    val refundAmount: Money,
    val allocationAuthorizations: List<Authorization> = listOf(authorization),
    val orderAuthorizations: List<Authorization> = allocationAuthorizations,
    val existingNegativeLedgerEntries: List<LedgerEntry> = emptyList(),
)

data class RefundResult(
    val order: PaymentOrder,
    val allocation: PaymentAllocation,
    val authorization: Authorization,
    val ledgerEntries: List<LedgerEntry>,
)
