package com.pglab.payment.authorization

import com.pglab.payment.allocation.PaymentAllocation
import com.pglab.payment.allocation.PaymentAllocationStatus
import com.pglab.payment.ledger.LedgerEntryType
import com.pglab.payment.order.PaymentOrder
import com.pglab.payment.order.PaymentOrderStatus
import com.pglab.payment.shared.CurrencyCode
import com.pglab.payment.shared.Money
import kotlin.test.Test
import kotlin.test.assertEquals

class PartialCancellationServiceTest {
    @Test
    fun `부분취소 시 승인 잔액이 줄고 취소 원장이 생성된다`() {
        val context = mixedAuthorizedContext()
        val service = PartialCancellationService()

        val result = service.cancel(
            PartialCancellationCommand(
                order = context.order,
                allocation = context.allocation,
                authorization = context.authorizations.first { it.instrumentType == InstrumentType.CARD },
                cancelAmount = Money(5_000L, CurrencyCode.KRW),
            ),
        )

        assertEquals(5_000L, result.authorization.remainingCancelableAmount.amount)
        assertEquals(AuthorizationStatus.PARTIALLY_CANCELED, result.authorization.status)
        assertEquals(LedgerEntryType.CANCELLED, result.ledgerEntry.type)
        assertEquals(5_000L, result.ledgerEntry.amount.amount)
        assertEquals(PaymentAllocationStatus.PARTIALLY_CANCELED, result.allocation.status)
        assertEquals(PaymentOrderStatus.PARTIALLY_CANCELED, result.order.status)
    }

    @Test
    fun `allocation 전체가 취소되면 주문과 부담단위는 취소 완료 상태가 된다`() {
        val context = mixedAuthorizedContext()
        val service = PartialCancellationService()

        service.cancel(
            PartialCancellationCommand(
                order = context.order,
                allocation = context.allocation,
                authorization = context.authorizations.first { it.instrumentType == InstrumentType.BANK_ACCOUNT },
                cancelAmount = Money(40_000L, CurrencyCode.KRW),
            ),
        )
        val finalResult = service.cancel(
            PartialCancellationCommand(
                order = context.order,
                allocation = context.allocation,
                authorization = context.authorizations.first { it.instrumentType == InstrumentType.CARD },
                cancelAmount = Money(10_000L, CurrencyCode.KRW),
            ),
        )

        assertEquals(PaymentAllocationStatus.CANCELED, finalResult.allocation.status)
        assertEquals(PaymentOrderStatus.CANCELED, finalResult.order.status)
    }

    private fun mixedAuthorizedContext(): MixedAuthorizedContext {
        val order = PaymentOrder(
            merchantId = "merchant-1",
            merchantOrderId = "order-cancel-001",
            totalAmount = Money(50_000L, CurrencyCode.KRW),
            status = PaymentOrderStatus.AUTHORIZED,
        )
        val allocation = PaymentAllocation(
            paymentOrder = order,
            payerReference = "payer-A",
            allocationAmount = Money(50_000L, CurrencyCode.KRW),
            sequence = 1,
            status = PaymentAllocationStatus.AUTHORIZED,
        )
        val bankAuthorization = Authorization(
            paymentAllocation = allocation,
            instrumentType = InstrumentType.BANK_ACCOUNT,
            requestedAmount = Money(40_000L, CurrencyCode.KRW),
            approvedAmount = Money(40_000L, CurrencyCode.KRW),
            pgTransactionId = "bank-auth-001",
        )
        val cardAuthorization = Authorization(
            paymentAllocation = allocation,
            instrumentType = InstrumentType.CARD,
            requestedAmount = Money(10_000L, CurrencyCode.KRW),
            approvedAmount = Money(10_000L, CurrencyCode.KRW),
            pgTransactionId = "card-auth-001",
            approvalCode = "card-appr-001",
        )

        return MixedAuthorizedContext(order, allocation, listOf(bankAuthorization, cardAuthorization))
    }

    private data class MixedAuthorizedContext(
        val order: PaymentOrder,
        val allocation: PaymentAllocation,
        val authorizations: List<Authorization>,
    )
}
