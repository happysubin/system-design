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
class PartialCancellationService(
    private val writer: PartialCancellationWriter,
) {
    @Transactional
    fun cancel(command: PartialCancellationCommand): PartialCancellationResult {
        // 취소 핵심 검증과 잔액 감소는 Authorization이 책임진다.
        // 서비스는 그 결과를 원장과 상위 집계 상태에 반영하는 역할을 맡는다.
        command.authorization.cancel(command.cancelAmount)

        val ledgerEntries = createLedgerEntries(command)

        // allocation 아래 여러 승인 수단이 있을 수 있으므로,
        // 특정 authorization 하나만 보고 상위 상태를 결정하면 안 된다.
        // 전체 authorization의 남은 취소 가능 잔액 합으로 상위 상태를 재집계한다.
        val allocationRemaining = command.allocationAuthorizations.sumOf { it.remainingCancelableAmount.amount }
        val orderRemaining = command.orderAuthorizations.sumOf { it.remainingCancelableAmount.amount }
        if (allocationRemaining == 0L) {
            // 더 이상 취소 가능한 잔액이 없으면 allocation/order 전체가 사실상 모두 취소된 상태다.
            command.allocation.status = PaymentAllocationStatus.CANCELED
        } else {
            // 일부 잔액이 남아 있으면 부분취소 상태를 유지한다.
            command.allocation.status = PaymentAllocationStatus.PARTIALLY_CANCELED
        }

        command.order.status = if (orderRemaining == 0L) {
            PaymentOrderStatus.CANCELED
        } else {
            PaymentOrderStatus.PARTIALLY_CANCELED
        }

        // 호출자는 반환된 authorization/ledger/order 상태를 그대로 후속 저장 또는 응답에 사용할 수 있다.
        val result = PartialCancellationResult(
            order = command.order,
            allocation = command.allocation,
            authorization = command.authorization,
            ledgerEntries = ledgerEntries,
        )

        return writer.save(result)
    }

    private fun createLedgerEntries(command: PartialCancellationCommand): List<LedgerEntry> {
        val orderedLinePortions = command.authorization.linePortions
            .sortedWith(compareBy<AuthorizationLinePortion>({ it.sequence }, { it.paymentOrderLine?.lineReference ?: "" }, { it.payeeId }))
        require(orderedLinePortions.isNotEmpty()) {
            "authorization line portions must exist for partial cancellation"
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
        require(command.cancelAmount.amount <= totalRemainingAmount) {
            "cancel amount must not exceed remaining line portion amount"
        }

        val occurredAt = OffsetDateTime.now()
        var remainingAmountToAssign = command.cancelAmount.amount
        val lastActiveLineReference = activeLineReferences.lastOrNull()

        return orderedLinePortions.mapNotNull { linePortion ->
            val lineReference = linePortion.paymentOrderLine?.lineReference ?: "${linePortion.payeeId}#${linePortion.sequence}"
            val remainingLineAmount = remainingAmountsByLineReference[lineReference] ?: 0L
            val splitAmount = if (remainingLineAmount == 0L) {
                0L
            } else if (lineReference == lastActiveLineReference) {
                remainingAmountToAssign
            } else {
                command.cancelAmount.amount * remainingLineAmount / totalRemainingAmount
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
                type = LedgerEntryType.CANCELLED,
                amount = Money(splitAmount, command.cancelAmount.currency),
                occurredAt = occurredAt,
                referenceTransactionId = command.authorization.pgTransactionId,
                description = "partial cancellation",
            )
        }
    }
}

interface PartialCancellationWriter {
    fun save(result: PartialCancellationResult): PartialCancellationResult
}

data class PartialCancellationCommand(
    val order: PaymentOrder,
    val allocation: PaymentAllocation,
    val authorization: Authorization,
    val cancelAmount: Money,
    val allocationAuthorizations: List<Authorization> = listOf(authorization),
    val orderAuthorizations: List<Authorization> = allocationAuthorizations,
    val existingNegativeLedgerEntries: List<LedgerEntry> = emptyList(),
)

data class PartialCancellationResult(
    val order: PaymentOrder,
    val allocation: PaymentAllocation,
    val authorization: Authorization,
    val ledgerEntries: List<LedgerEntry>,
)
