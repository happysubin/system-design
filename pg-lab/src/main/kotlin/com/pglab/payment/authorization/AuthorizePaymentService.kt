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
class AuthorizePaymentService(
    private val writer: AuthorizePaymentWriter,
) {
    @Transactional
    fun authorize(command: AuthorizePaymentCommand): AuthorizePaymentResult {
        // 실무형 일반화 버전에서는 allocation이 최소 1개 이상 있어야 한다.
        // allocation이 비어 있으면 order 총액을 누가 어떤 구조로 부담하는지 설명할 수 없다.
        require(command.allocations.isNotEmpty()) { "allocations must not be empty" }

        // 먼저 order 전체 기준으로 allocation 합이 정확히 맞는지 검증한다.
        // 이 검증이 깨지면 더치페이/혼합결제 구조가 총액 기준에서 이미 틀어진 상태다.
        val allocationTotal = command.allocations.sumOf { it.allocationAmount.amount }
        require(allocationTotal == command.totalAmount.amount) { "allocation sum must match total amount" }

        // 한 주문 안에서는 통화를 섞지 않는다고 가정하므로,
        // allocation 금액 통화는 모두 order 통화와 같아야 한다.
        require(command.allocations.all { it.allocationAmount.currency == command.totalAmount.currency }) {
            "all allocation currencies must match total amount currency"
        }

        command.allocations.forEach { allocation ->
            // 각 allocation 내부에서도 authorization 요청 금액 합이 allocation 금액과 맞아야 한다.
            // 그래야 "누가 얼마를 부담한다"와 "그 부담분을 어떤 수단으로 결제한다"가 일치한다.
            val requestedTotal = allocation.authorizations.sumOf { it.requestedAmount.amount }
            require(requestedTotal == allocation.allocationAmount.amount) {
                "authorization sum must match allocation amount"
            }

            // 일반형 authorize 서비스도 order 단위 통화 일관성을 유지한다.
            require(allocation.authorizations.all { it.requestedAmount.currency == command.totalAmount.currency }) {
                "all requested currencies must match total amount currency"
            }
            require(allocation.authorizations.all { it.approvedAmount.currency == command.totalAmount.currency }) {
                "all approved currencies must match total amount currency"
            }
            require(allocation.authorizations.all { it.approvedAmount.amount <= it.requestedAmount.amount }) {
                "approved amount must not exceed requested amount"
            }
        }

        // allocation 전체 승인 합을 모아 order가 완전 승인인지 부분 승인인지 상위 상태를 계산한다.
        val orderApprovedTotal = command.allocations.sumOf { allocation ->
            allocation.authorizations.sumOf { it.approvedAmount.amount }
        }

        // PaymentOrder는 거래 전체 상위 컨텍스트이므로 먼저 한 번만 만든다.
        val order = PaymentOrder(
            merchantId = command.merchantId,
            merchantOrderId = command.merchantOrderId,
            totalAmount = command.totalAmount,
            status = if (orderApprovedTotal == command.totalAmount.amount) {
                PaymentOrderStatus.AUTHORIZED
            } else {
                PaymentOrderStatus.PARTIALLY_AUTHORIZED
            },
        )

        // 그 다음 각 payer/부담 단위를 allocation으로 만든다.
        // sequence는 입력 순서를 유지해 이후 표시/추적 시 안정적인 정렬 기준이 되게 한다.
        val allocationModels = command.allocations.mapIndexed { index, request ->
            val allocationApprovedTotal = request.authorizations.sumOf { it.approvedAmount.amount }
            PaymentAllocation(
                paymentOrder = order,
                payerReference = request.payerReference,
                allocationAmount = request.allocationAmount,
                sequence = index + 1,
                status = if (allocationApprovedTotal == request.allocationAmount.amount) {
                    PaymentAllocationStatus.AUTHORIZED
                } else {
                    PaymentAllocationStatus.PARTIALLY_AUTHORIZED
                },
            )
        }

        // 각 allocation 아래에 실제 승인 결과를 authorization으로 생성한다.
        // 즉 order -> allocations -> authorizations의 3단 구조를 메모리 안에서 먼저 조립한다.
        val authorizations = command.allocations.zip(allocationModels).flatMap { (allocationRequest, allocationModel) ->
            allocationRequest.authorizations.map { authorizationRequest ->
                Authorization(
                    paymentAllocation = allocationModel,
                    instrumentType = authorizationRequest.instrumentType,
                    requestedAmount = authorizationRequest.requestedAmount,
                    approvedAmount = authorizationRequest.approvedAmount,
                    pgTransactionId = authorizationRequest.pgTransactionId,
                    approvalCode = authorizationRequest.approvalCode,
                    approvedAt = authorizationRequest.approvedAt,
                )
            }
        }

        // 승인 사실은 이후 정산/환불/취소의 기준이 되므로,
        // 각 authorization마다 AUTH_CAPTURED 원장을 반드시 남긴다.
        val ledgerEntries = authorizations.map { authorization ->
            LedgerEntry(
                paymentOrder = order,
                paymentAllocation = authorization.paymentAllocation,
                authorization = authorization,
                type = LedgerEntryType.AUTH_CAPTURED,
                amount = authorization.approvedAmount,
                referenceTransactionId = authorization.pgTransactionId,
                description = "payment authorization",
            )
        }

        // 결과는 상위 order와 하위 allocations/authorizations/ledgerEntries를 함께 돌려줘서,
        // 호출자가 영속화나 후속 처리에 전체 aggregate tree를 그대로 사용할 수 있게 한다.
        val result = AuthorizePaymentResult(
            order = order,
            allocations = allocationModels,
            authorizations = authorizations,
            ledgerEntries = ledgerEntries,
        )

        return writer.save(result)
    }
}

interface AuthorizePaymentWriter {
    fun save(result: AuthorizePaymentResult): AuthorizePaymentResult
}

data class AuthorizePaymentCommand(
    val merchantId: String,
    val merchantOrderId: String,
    val totalAmount: Money,
    val allocations: List<AllocationAuthorizationRequest>,
)

data class AllocationAuthorizationRequest(
    val payerReference: String,
    val allocationAmount: Money,
    val authorizations: List<AuthorizationRequest>,
)

data class AuthorizationRequest(
    val instrumentType: InstrumentType,
    val requestedAmount: Money,
    val approvedAmount: Money,
    val pgTransactionId: String,
    val approvalCode: String? = null,
    val approvedAt: OffsetDateTime? = null,
)

data class AuthorizePaymentResult(
    val order: PaymentOrder,
    val allocations: List<PaymentAllocation>,
    val authorizations: List<Authorization>,
    val ledgerEntries: List<LedgerEntry>,
)
