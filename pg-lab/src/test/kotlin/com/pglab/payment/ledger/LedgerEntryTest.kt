package com.pglab.payment.ledger

import com.pglab.payment.order.PaymentOrder
import com.pglab.payment.order.PaymentOrderLine
import com.pglab.payment.shared.CurrencyCode
import com.pglab.payment.shared.Money
import kotlin.test.Test
import kotlin.test.assertEquals

class LedgerEntryTest {
    @Test
    fun `원장 엔트리는 seller 기준 라인 참조와 금액을 보관한다`() {
        val order = PaymentOrder(
            merchantId = "merchant-1",
            merchantOrderId = "order-001",
            totalAmount = Money(30_000L, CurrencyCode.KRW),
        )
        val orderLine = PaymentOrderLine(
            lineReference = "line-1",
            payeeId = "seller-A",
            lineAmount = Money(30_000L, CurrencyCode.KRW),
            quantity = 1,
        ).also(order::addLine)

        val entry = LedgerEntry(
            paymentOrder = order,
            paymentOrderLine = orderLine,
            payeeId = "seller-A",
            type = LedgerEntryType.AUTH_CAPTURED,
            amount = Money(30_000L, CurrencyCode.KRW),
            referenceTransactionId = "pg-tx-001",
        )

        assertEquals(LedgerEntryType.AUTH_CAPTURED, entry.type)
        assertEquals(orderLine, entry.paymentOrderLine)
        assertEquals("seller-A", entry.payeeId)
        assertEquals(30_000L, entry.amount.amount)
    }
}
