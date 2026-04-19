package com.pglab.payment.order

import com.pglab.payment.shared.CurrencyCode
import com.pglab.payment.shared.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PaymentOrderTest {
    @Test
    fun `결제 주문은 가맹점 식별자 총액과 초기 상태를 보관한다`() {
        val order = PaymentOrder(
            merchantId = "merchant-1",
            merchantOrderId = "order-001",
            totalAmount = Money(50_000L, CurrencyCode.KRW),
        )

        assertEquals("merchant-1", order.merchantId)
        assertEquals(PaymentOrderStatus.READY, order.status)
        assertEquals(50_000L, order.totalAmount.amount)
    }

    @Test
    fun `결제 주문은 주문 라인을 추가하면 연관관계를 함께 맞춘다`() {
        val order = PaymentOrder(
            merchantId = "platform-merchant",
            merchantOrderId = "order-002",
            totalAmount = Money(50_000L, CurrencyCode.KRW),
        )
        val firstLine = PaymentOrderLine(
            lineReference = "line-1",
            payeeId = "seller-A",
            lineAmount = Money(30_000L, CurrencyCode.KRW),
            quantity = 1,
        )
        val secondLine = PaymentOrderLine(
            lineReference = "line-2",
            payeeId = "seller-B",
            lineAmount = Money(20_000L, CurrencyCode.KRW),
            quantity = 2,
        )

        order.addLine(firstLine)
        order.addLine(secondLine)

        assertEquals(2, order.lines.size)
        assertEquals(listOf("seller-A", "seller-B"), order.lines.map { it.payeeId })
        assertEquals(order, firstLine.paymentOrder)
        assertEquals(order, secondLine.paymentOrder)
    }

    @Test
    fun `결제 주문은 주문 라인을 제거하면 연관관계를 함께 해제한다`() {
        val order = PaymentOrder(
            merchantId = "platform-merchant",
            merchantOrderId = "order-003",
            totalAmount = Money(30_000L, CurrencyCode.KRW),
        )
        val line = PaymentOrderLine(
            lineReference = "line-1",
            payeeId = "seller-A",
            lineAmount = Money(30_000L, CurrencyCode.KRW),
            quantity = 1,
        )

        order.addLine(line)
        order.removeLine(line)

        assertEquals(emptyList(), order.lines)
        assertEquals(null, line.paymentOrder)
    }

    @Test
    fun `이미 다른 주문에 속한 주문 라인은 추가할 수 없다`() {
        val firstOrder = PaymentOrder(
            merchantId = "platform-merchant",
            merchantOrderId = "order-004",
            totalAmount = Money(30_000L, CurrencyCode.KRW),
        )
        val secondOrder = PaymentOrder(
            merchantId = "platform-merchant",
            merchantOrderId = "order-005",
            totalAmount = Money(30_000L, CurrencyCode.KRW),
        )
        val line = PaymentOrderLine(
            lineReference = "line-1",
            payeeId = "seller-A",
            lineAmount = Money(30_000L, CurrencyCode.KRW),
            quantity = 1,
        )

        firstOrder.addLine(line)

        assertFailsWith<IllegalArgumentException> {
            secondOrder.addLine(line)
        }
    }
}
