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

class RefundServiceTest {
    @Test
    fun `부분 환불 시 환불 가능 잔액이 줄고 환불 원장이 생성된다`() {
        val context = mixedAuthorizedContext()
        val writer = FakeRefundWriter()
        val service = RefundService(writer)

        val result = service.refund(
            RefundCommand(
                order = context.order,
                allocation = context.allocation,
                authorization = context.authorizations.first { it.instrumentType == InstrumentType.CARD },
                refundAmount = Money(5_000L, CurrencyCode.KRW),
                allAuthorizations = context.authorizations,
            ),
        )

        assertEquals(5_000L, result.authorization.remainingRefundableAmount.amount)
        assertEquals(LedgerEntryType.REFUNDED, result.ledgerEntry.type)
        assertEquals(5_000L, result.ledgerEntry.amount.amount)
        assertEquals(PaymentAllocationStatus.PARTIALLY_CANCELED, result.allocation.status)
        assertEquals(PaymentOrderStatus.PARTIALLY_CANCELED, result.order.status)
        assertEquals(1, writer.savedResults.size)
    }

    @Test
    fun `allocation 전체가 환불되면 주문과 부담단위는 취소 완료 상태가 된다`() {
        val context = mixedAuthorizedContext()
        val service = RefundService(FakeRefundWriter())

        service.refund(
            RefundCommand(
                order = context.order,
                allocation = context.allocation,
                authorization = context.authorizations.first { it.instrumentType == InstrumentType.BANK_ACCOUNT },
                refundAmount = Money(40_000L, CurrencyCode.KRW),
                allAuthorizations = context.authorizations,
            ),
        )
        val finalResult = service.refund(
            RefundCommand(
                order = context.order,
                allocation = context.allocation,
                authorization = context.authorizations.first { it.instrumentType == InstrumentType.CARD },
                refundAmount = Money(10_000L, CurrencyCode.KRW),
                allAuthorizations = context.authorizations,
            ),
        )

        assertEquals(PaymentAllocationStatus.CANCELED, finalResult.allocation.status)
        assertEquals(PaymentOrderStatus.CANCELED, finalResult.order.status)
    }

    private fun mixedAuthorizedContext(): MixedAuthorizedContext {
        val order = PaymentOrder(
            merchantId = "merchant-1",
            merchantOrderId = "order-refund-001",
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

    private class FakeRefundWriter : RefundWriter {
        val savedResults = mutableListOf<RefundResult>()

        override fun save(result: RefundResult): RefundResult {
            savedResults.add(result)
            return result
        }
    }
}
