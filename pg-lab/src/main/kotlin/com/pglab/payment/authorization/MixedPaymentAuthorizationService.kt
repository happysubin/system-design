package com.pglab.payment.authorization

import com.pglab.payment.allocation.PaymentAllocation
import com.pglab.payment.allocation.PaymentAllocationStatus
import com.pglab.payment.ledger.LedgerEntry
import com.pglab.payment.ledger.LedgerEntryType
import com.pglab.payment.order.PaymentOrder
import com.pglab.payment.order.PaymentOrderStatus
import com.pglab.payment.shared.Money
import java.time.OffsetDateTime

class MixedPaymentAuthorizationService {
    fun authorize(command: MixedPaymentAuthorizationCommand): MixedPaymentAuthorizationResult {
        // 혼합결제는 "한 주문 안에 여러 승인 수단이 공존한다"는 유즈케이스이므로,
        // 최소 한 개 이상의 승인 요청이 반드시 있어야 한다.
        require(command.authorizationRequests.isNotEmpty()) { "authorizationRequests must not be empty" }

        // 각 수단별 요청 금액 합이 주문 총액과 다르면,
        // 주문/부담단위/승인 결과를 일관되게 만들 수 없으므로 바로 차단한다.
        val requestedTotal = command.authorizationRequests.sumOf { it.requestedAmount.amount }
        require(requestedTotal == command.totalAmount.amount) { "requested amount sum must match total amount" }

        // 지금 모델은 하나의 주문 안에서 통화가 섞이지 않는다고 가정한다.
        // 따라서 모든 요청/승인 금액의 통화는 order 총액 통화와 같아야 한다.
        require(command.authorizationRequests.all { it.requestedAmount.currency == command.totalAmount.currency }) {
            "all requested currencies must match total amount currency"
        }
        require(command.authorizationRequests.all { it.approvedAmount.currency == command.totalAmount.currency }) {
            "all approved currencies must match total amount currency"
        }
        require(command.authorizationRequests.all { it.approvedAmount.amount <= it.requestedAmount.amount }) {
            "approved amount must not exceed requested amount"
        }

        // 최종적으로 얼마가 승인됐는지 합산해서,
        // 주문과 부담단위가 완전 승인인지 부분 승인인지 상위 상태를 먼저 결정한다.
        val approvedTotal = command.authorizationRequests.sumOf { it.approvedAmount.amount }
        val finalStatus = if (approvedTotal == command.totalAmount.amount) {
            PaymentOrderStatus.AUTHORIZED
        } else {
            PaymentOrderStatus.PARTIALLY_AUTHORIZED
        }
        val allocationStatus = if (approvedTotal == command.totalAmount.amount) {
            PaymentAllocationStatus.AUTHORIZED
        } else {
            PaymentAllocationStatus.PARTIALLY_AUTHORIZED
        }

        // PaymentOrder는 "무엇을 얼마 결제하려는가"라는 상위 비즈니스 의도를 표현한다.
        val order = PaymentOrder(
            merchantId = command.merchantId,
            merchantOrderId = command.merchantOrderId,
            totalAmount = command.totalAmount,
            status = finalStatus,
        )

        // 이번 유즈케이스는 "한 사람의 결제를 여러 수단으로 나누는 혼합결제" 기준이므로,
        // allocation은 1개만 만들고 그 아래에 여러 Authorization을 붙인다.
        val allocation = PaymentAllocation(
            paymentOrder = order,
            payerReference = command.payerReference,
            allocationAmount = command.totalAmount,
            sequence = 1,
            status = allocationStatus,
        )

        // 각 수단별 승인 결과를 Authorization으로 만든다.
        // 이 단계에서는 외부 PG 응답을 이미 받은 상태라고 가정하고, 그 결과를 도메인 객체로 조립한다.
        val authorizations = command.authorizationRequests.map { request ->
            Authorization(
                paymentAllocation = allocation,
                instrumentType = request.instrumentType,
                requestedAmount = request.requestedAmount,
                approvedAmount = request.approvedAmount,
                pgTransactionId = request.pgTransactionId,
                approvalCode = request.approvalCode,
                approvedAt = request.approvedAt,
            )
        }

        // 승인 사실은 append-only 원장에도 각각 남긴다.
        // 이후 정산/환불/취소는 이 원장 기록을 근거로 집계된다.
        val ledgerEntries = authorizations.map { authorization ->
            LedgerEntry(
                paymentOrder = order,
                paymentAllocation = allocation,
                authorization = authorization,
                type = LedgerEntryType.AUTH_CAPTURED,
                amount = authorization.approvedAmount,
                referenceTransactionId = authorization.pgTransactionId,
                description = "mixed payment authorization",
            )
        }

        // 유즈케이스 결과는 아직 영속화되지 않은 순수 도메인 조립 결과다.
        // 상위 계층이 이 결과를 저장하거나 후속 처리에 넘길 수 있도록 묶어서 반환한다.
        return MixedPaymentAuthorizationResult(
            order = order,
            allocation = allocation,
            authorizations = authorizations,
            ledgerEntries = ledgerEntries,
        )
    }
}

data class MixedPaymentAuthorizationCommand(
    val merchantId: String,
    val merchantOrderId: String,
    val payerReference: String,
    val totalAmount: Money,
    val authorizationRequests: List<MixedAuthorizationRequest>,
)

data class MixedAuthorizationRequest(
    val instrumentType: InstrumentType,
    val requestedAmount: Money,
    val approvedAmount: Money,
    val pgTransactionId: String,
    val approvalCode: String? = null,
    val approvedAt: OffsetDateTime? = null,
)

data class MixedPaymentAuthorizationResult(
    val order: PaymentOrder,
    val allocation: PaymentAllocation,
    val authorizations: List<Authorization>,
    val ledgerEntries: List<LedgerEntry>,
)
