package com.pglab.payment.authorization

import com.pglab.payment.allocation.PaymentAllocation
import com.pglab.payment.allocation.PaymentAllocationStatus
import com.pglab.payment.ledger.LedgerEntry
import com.pglab.payment.ledger.LedgerEntryType
import com.pglab.payment.order.PaymentOrder
import com.pglab.payment.order.PaymentOrderStatus
import com.pglab.payment.order.PaymentOrderLine
import com.pglab.payment.shared.CurrencyCode
import com.pglab.payment.shared.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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
                allocationAuthorizations = context.authorizations,
                orderAuthorizations = context.authorizations,
            ),
        )

        assertEquals(5_000L, result.authorization.remainingRefundableAmount.amount)
        assertEquals(2, result.ledgerEntries.size)
        assertEquals(listOf(LedgerEntryType.REFUNDED, LedgerEntryType.REFUNDED), result.ledgerEntries.map { it.type })
        assertEquals(listOf("seller-A", "seller-B"), result.ledgerEntries.map { it.payeeId })
        assertEquals(listOf(3_000L, 2_000L), result.ledgerEntries.map { it.amount.amount })
        assertEquals(5_000L, result.ledgerEntries.sumOf { it.amount.amount })
        assertEquals(listOf("line-1", "line-2"), result.ledgerEntries.map { it.paymentOrderLine?.lineReference })
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
                allocationAuthorizations = context.authorizations,
                orderAuthorizations = context.authorizations,
            ),
        )
        val finalResult = service.refund(
            RefundCommand(
                order = context.order,
                allocation = context.allocation,
                authorization = context.authorizations.first { it.instrumentType == InstrumentType.CARD },
                refundAmount = Money(10_000L, CurrencyCode.KRW),
                allocationAuthorizations = context.authorizations,
                orderAuthorizations = context.authorizations,
            ),
        )

        assertEquals(PaymentAllocationStatus.CANCELED, finalResult.allocation.status)
        assertEquals(PaymentOrderStatus.CANCELED, finalResult.order.status)
    }

    @Test
    fun `부분환불은 line portion sequence 기준으로 잔여 금액을 마지막 seller에 배정한다`() {
        val context = mixedAuthorizedContext(addCardLinePortionsOutOfOrder = true)
        val service = RefundService(FakeRefundWriter())

        val result = service.refund(
            RefundCommand(
                order = context.order,
                allocation = context.allocation,
                authorization = context.authorizations.first { it.instrumentType == InstrumentType.CARD },
                refundAmount = Money(1L, CurrencyCode.KRW),
                allocationAuthorizations = context.authorizations,
                orderAuthorizations = context.authorizations,
            ),
        )

        assertEquals(listOf("line-2"), result.ledgerEntries.map { it.paymentOrderLine?.lineReference })
        assertEquals(listOf(1L), result.ledgerEntries.map { it.amount.amount })
    }

    @Test
    fun `반복 부분환불은 기존 환불 원장을 반영해 seller별 남은 금액 한도 안에서만 배분한다`() {
        val context = singleAuthorizationContext(cardAmountA = 6L, cardAmountB = 4L)
        val service = RefundService(FakeRefundWriter())
        val authorization = context.authorizations.single()
        val priorLedgerEntries = mutableListOf<LedgerEntry>()

        repeat(4) {
            val result = service.refund(
                RefundCommand(
                    order = context.order,
                    allocation = context.allocation,
                    authorization = authorization,
                    refundAmount = Money(1L, CurrencyCode.KRW),
                    allocationAuthorizations = context.authorizations,
                    orderAuthorizations = context.authorizations,
                    existingNegativeLedgerEntries = priorLedgerEntries.toList(),
                ),
            )
            priorLedgerEntries.addAll(result.ledgerEntries)
        }

        val fifthResult = service.refund(
            RefundCommand(
                order = context.order,
                allocation = context.allocation,
                authorization = authorization,
                refundAmount = Money(1L, CurrencyCode.KRW),
                allocationAuthorizations = context.authorizations,
                orderAuthorizations = context.authorizations,
                existingNegativeLedgerEntries = priorLedgerEntries,
            ),
        )

        assertEquals(listOf("seller-A"), fifthResult.ledgerEntries.map { it.payeeId })
        assertEquals(listOf(1L), fifthResult.ledgerEntries.map { it.amount.amount })
        assertEquals(1L, fifthResult.ledgerEntries.sumOf { it.amount.amount })
    }

    @Test
    fun `부분환불은 기존 취소 원장도 seller별 잔여 한도 계산에 반영한다`() {
        val context = singleAuthorizationContext(cardAmountA = 6L, cardAmountB = 4L)
        val service = RefundService(FakeRefundWriter())
        val authorization = context.authorizations.single()
        val existingCanceledLedger = LedgerEntry(
            paymentOrder = context.order,
            paymentAllocation = context.allocation,
            authorization = authorization,
            paymentOrderLine = context.order.lines.first { it.lineReference == "line-2" },
            payeeId = "seller-B",
            type = LedgerEntryType.CANCELLED,
            amount = Money(4L, CurrencyCode.KRW),
            referenceTransactionId = authorization.pgTransactionId,
            description = "existing cancellation",
        )

        val result = service.refund(
            RefundCommand(
                order = context.order,
                allocation = context.allocation,
                authorization = authorization,
                refundAmount = Money(1L, CurrencyCode.KRW),
                allocationAuthorizations = context.authorizations,
                orderAuthorizations = context.authorizations,
                existingNegativeLedgerEntries = listOf(existingCanceledLedger),
            ),
        )

        assertEquals(listOf("seller-A"), result.ledgerEntries.map { it.payeeId })
        assertEquals(listOf(1L), result.ledgerEntries.map { it.amount.amount })
    }

    @Test
    fun `부분환불은 0원 요청을 거부한다`() {
        val context = mixedAuthorizedContext()
        val service = RefundService(FakeRefundWriter())

        val exception = assertFailsWith<IllegalArgumentException> {
            service.refund(
                RefundCommand(
                    order = context.order,
                    allocation = context.allocation,
                    authorization = context.authorizations.first { it.instrumentType == InstrumentType.CARD },
                    refundAmount = Money(0L, CurrencyCode.KRW),
                    allocationAuthorizations = context.authorizations,
                    orderAuthorizations = context.authorizations,
                ),
            )
        }

        assertEquals("refund amount must be greater than zero", exception.message)
    }

    @Test
    fun `멀티 allocation 주문에서 한 allocation만 전액 환불되면 allocation만 취소 완료이고 주문은 부분취소 상태다`() {
        val context = multiAllocationContext()
        val service = RefundService(FakeRefundWriter())

        val result = service.refund(
            RefundCommand(
                order = context.order,
                allocation = context.targetAllocation,
                authorization = context.targetAuthorization,
                refundAmount = Money(50_000L, CurrencyCode.KRW),
                allocationAuthorizations = listOf(context.targetAuthorization),
                orderAuthorizations = context.orderAuthorizations,
            ),
        )

        assertEquals(PaymentAllocationStatus.CANCELED, result.allocation.status)
        assertEquals(PaymentOrderStatus.PARTIALLY_CANCELED, result.order.status)
    }

    private fun mixedAuthorizedContext(addCardLinePortionsOutOfOrder: Boolean = false): MixedAuthorizedContext {
        return singleAuthorizationContext(
            bankAmount = 40_000L,
            cardAmountA = 6_000L,
            cardAmountB = 4_000L,
            addCardLinePortionsOutOfOrder = addCardLinePortionsOutOfOrder,
        )
    }

    private fun singleAuthorizationContext(
        bankAmount: Long? = null,
        cardAmountA: Long,
        cardAmountB: Long,
        addCardLinePortionsOutOfOrder: Boolean = false,
    ): MixedAuthorizedContext {
        val totalAmount = (bankAmount ?: 0L) + cardAmountA + cardAmountB
        val order = PaymentOrder(
            merchantId = "merchant-1",
            merchantOrderId = "order-refund-001",
            totalAmount = Money(totalAmount, CurrencyCode.KRW),
            status = PaymentOrderStatus.AUTHORIZED,
        )
        val sellerALine = PaymentOrderLine(
            lineReference = "line-1",
            payeeId = "seller-A",
            lineAmount = Money((bankAmount?.let { it * 3 / 5 } ?: 0L) + cardAmountA, CurrencyCode.KRW),
            quantity = 1,
        )
        val sellerBLine = PaymentOrderLine(
            lineReference = "line-2",
            payeeId = "seller-B",
            lineAmount = Money((bankAmount?.let { it * 2 / 5 } ?: 0L) + cardAmountB, CurrencyCode.KRW),
            quantity = 1,
        )
        order.addLine(sellerALine)
        order.addLine(sellerBLine)
        val allocation = PaymentAllocation(
            paymentOrder = order,
            payerReference = "payer-A",
            allocationAmount = Money(totalAmount, CurrencyCode.KRW),
            sequence = 1,
            status = PaymentAllocationStatus.AUTHORIZED,
        )
        val cardAuthorization = Authorization(
            paymentAllocation = allocation,
            instrumentType = InstrumentType.CARD,
            requestedAmount = Money(cardAmountA + cardAmountB, CurrencyCode.KRW),
            approvedAmount = Money(cardAmountA + cardAmountB, CurrencyCode.KRW),
            pgTransactionId = "card-auth-001",
            approvalCode = "card-appr-001",
        )
        val cardLinePortions = listOf(
            AuthorizationLinePortion(
                paymentOrderLine = sellerALine,
                payeeId = "seller-A",
                amount = Money(cardAmountA, CurrencyCode.KRW),
                sequence = 1,
            ),
            AuthorizationLinePortion(
                paymentOrderLine = sellerBLine,
                payeeId = "seller-B",
                amount = Money(cardAmountB, CurrencyCode.KRW),
                sequence = 2,
            ),
        )
        val cardPortionsToAttach = if (addCardLinePortionsOutOfOrder) cardLinePortions.reversed() else cardLinePortions
        cardPortionsToAttach.forEach(cardAuthorization::addLinePortion)

        val authorizations = mutableListOf<Authorization>()
        if (bankAmount != null) {
            val bankAuthorization = Authorization(
                paymentAllocation = allocation,
                instrumentType = InstrumentType.BANK_ACCOUNT,
                requestedAmount = Money(bankAmount, CurrencyCode.KRW),
                approvedAmount = Money(bankAmount, CurrencyCode.KRW),
                pgTransactionId = "bank-auth-001",
            )
            bankAuthorization.addLinePortion(
                AuthorizationLinePortion(
                    paymentOrderLine = sellerALine,
                    payeeId = "seller-A",
                    amount = Money(bankAmount * 3 / 5, CurrencyCode.KRW),
                    sequence = 1,
                ),
            )
            bankAuthorization.addLinePortion(
                AuthorizationLinePortion(
                    paymentOrderLine = sellerBLine,
                    payeeId = "seller-B",
                    amount = Money(bankAmount * 2 / 5, CurrencyCode.KRW),
                    sequence = 2,
                ),
            )
            authorizations += bankAuthorization
        }
        authorizations += cardAuthorization

        return MixedAuthorizedContext(order, allocation, authorizations)
    }

    private data class MixedAuthorizedContext(
        val order: PaymentOrder,
        val allocation: PaymentAllocation,
        val authorizations: List<Authorization>,
    )

    private fun multiAllocationContext(): MultiAllocationContext {
        val order = PaymentOrder(
            merchantId = "merchant-1",
            merchantOrderId = "order-refund-multi-allocation",
            totalAmount = Money(100_000L, CurrencyCode.KRW),
            status = PaymentOrderStatus.AUTHORIZED,
        )
        val sellerALine = PaymentOrderLine(
            lineReference = "line-1",
            payeeId = "seller-A",
            lineAmount = Money(50_000L, CurrencyCode.KRW),
            quantity = 1,
        )
        val sellerBLine = PaymentOrderLine(
            lineReference = "line-2",
            payeeId = "seller-B",
            lineAmount = Money(50_000L, CurrencyCode.KRW),
            quantity = 1,
        )
        order.addLine(sellerALine)
        order.addLine(sellerBLine)

        val firstAllocation = PaymentAllocation(
            paymentOrder = order,
            payerReference = "payer-A",
            allocationAmount = Money(50_000L, CurrencyCode.KRW),
            sequence = 1,
            status = PaymentAllocationStatus.AUTHORIZED,
        )
        val secondAllocation = PaymentAllocation(
            paymentOrder = order,
            payerReference = "payer-B",
            allocationAmount = Money(50_000L, CurrencyCode.KRW),
            sequence = 2,
            status = PaymentAllocationStatus.AUTHORIZED,
        )

        val firstAuthorization = Authorization(
            paymentAllocation = firstAllocation,
            instrumentType = InstrumentType.CARD,
            requestedAmount = Money(50_000L, CurrencyCode.KRW),
            approvedAmount = Money(50_000L, CurrencyCode.KRW),
            pgTransactionId = "card-auth-first-allocation",
        )
        firstAuthorization.addLinePortion(
            AuthorizationLinePortion(
                paymentOrderLine = sellerALine,
                payeeId = "seller-A",
                amount = Money(50_000L, CurrencyCode.KRW),
                sequence = 1,
            ),
        )

        val secondAuthorization = Authorization(
            paymentAllocation = secondAllocation,
            instrumentType = InstrumentType.BANK_ACCOUNT,
            requestedAmount = Money(50_000L, CurrencyCode.KRW),
            approvedAmount = Money(50_000L, CurrencyCode.KRW),
            pgTransactionId = "bank-auth-second-allocation",
        )
        secondAuthorization.addLinePortion(
            AuthorizationLinePortion(
                paymentOrderLine = sellerBLine,
                payeeId = "seller-B",
                amount = Money(50_000L, CurrencyCode.KRW),
                sequence = 1,
            ),
        )

        return MultiAllocationContext(
            order = order,
            targetAllocation = firstAllocation,
            targetAuthorization = firstAuthorization,
            orderAuthorizations = listOf(firstAuthorization, secondAuthorization),
        )
    }

    private data class MultiAllocationContext(
        val order: PaymentOrder,
        val targetAllocation: PaymentAllocation,
        val targetAuthorization: Authorization,
        val orderAuthorizations: List<Authorization>,
    )

    private class FakeRefundWriter : RefundWriter {
        val savedResults = mutableListOf<RefundResult>()

        override fun save(result: RefundResult): RefundResult {
            savedResults.add(result)
            return result
        }
    }
}
