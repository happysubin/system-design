package com.pglab.payment.order

import com.pglab.payment.shared.CurrencyCode
import com.pglab.payment.shared.Money
import kotlin.test.Test
import kotlin.test.assertEquals

class PaymentOrderLineTest {
    @Test
    fun `주문 라인은 정산 대상 판매자와 금액을 보관한다`() {
        val order = PaymentOrder(
            merchantId = "platform-merchant",
            merchantOrderId = "order-001",
            totalAmount = Money(50_000L, CurrencyCode.KRW),
        )
        val line = PaymentOrderLine(
            lineReference = "line-1",
            payeeId = "seller-A",
            lineAmount = Money(30_000L, CurrencyCode.KRW),
            quantity = 1,
        )

        order.addLine(line)

        assertEquals("seller-A", line.payeeId)
        assertEquals(30_000L, line.lineAmount.amount)
        assertEquals(order, line.paymentOrder)
    }
}
