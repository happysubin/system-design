package com.pglab.payment.authorization

import com.pglab.payment.allocation.PaymentAllocation
import com.pglab.payment.allocation.PaymentAllocationStatus
import com.pglab.payment.ledger.LedgerEntry
import com.pglab.payment.ledger.LedgerEntryType
import com.pglab.payment.order.PaymentOrder
import com.pglab.payment.order.PaymentOrderStatus
import com.pglab.payment.shared.Money

class PartialCancellationService {
    fun cancel(command: PartialCancellationCommand): PartialCancellationResult {
        // 취소 핵심 검증과 잔액 감소는 Authorization이 책임진다.
        // 서비스는 그 결과를 원장과 상위 집계 상태에 반영하는 역할을 맡는다.
        command.authorization.cancel(command.cancelAmount)

        // 취소는 기존 승인 기록을 덮어쓰지 않고 별도 원장 사실로 남긴다.
        // 그래야 부분취소 누적 이력이 감사/정산 관점에서 추적 가능하다.
        val ledgerEntry = LedgerEntry(
            paymentOrder = command.order,
            paymentAllocation = command.allocation,
            authorization = command.authorization,
            type = LedgerEntryType.CANCELLED,
            amount = command.cancelAmount,
            referenceTransactionId = command.authorization.pgTransactionId,
            description = "partial cancellation",
        )

        // allocation 아래 여러 승인 수단이 있을 수 있으므로,
        // 특정 authorization 하나만 보고 상위 상태를 결정하면 안 된다.
        // 전체 authorization의 남은 취소 가능 잔액 합으로 상위 상태를 재집계한다.
        val allocationRemaining = command.allAuthorizations.sumOf { it.remainingCancelableAmount.amount }
        if (allocationRemaining == 0L) {
            // 더 이상 취소 가능한 잔액이 없으면 allocation/order 전체가 사실상 모두 취소된 상태다.
            command.allocation.status = PaymentAllocationStatus.CANCELED
            command.order.status = PaymentOrderStatus.CANCELED
        } else {
            // 일부 잔액이 남아 있으면 부분취소 상태를 유지한다.
            command.allocation.status = PaymentAllocationStatus.PARTIALLY_CANCELED
            command.order.status = PaymentOrderStatus.PARTIALLY_CANCELED
        }

        // 호출자는 반환된 authorization/ledger/order 상태를 그대로 후속 저장 또는 응답에 사용할 수 있다.
        return PartialCancellationResult(
            order = command.order,
            allocation = command.allocation,
            authorization = command.authorization,
            ledgerEntry = ledgerEntry,
        )
    }
}

data class PartialCancellationCommand(
    val order: PaymentOrder,
    val allocation: PaymentAllocation,
    val authorization: Authorization,
    val cancelAmount: Money,
    val allAuthorizations: List<Authorization> = listOf(authorization),
)

data class PartialCancellationResult(
    val order: PaymentOrder,
    val allocation: PaymentAllocation,
    val authorization: Authorization,
    val ledgerEntry: LedgerEntry,
)
