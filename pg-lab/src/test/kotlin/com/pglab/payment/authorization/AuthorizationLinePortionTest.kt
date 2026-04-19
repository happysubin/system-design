package com.pglab.payment.authorization

import com.pglab.payment.allocation.PaymentAllocation
import com.pglab.payment.order.PaymentOrder
import com.pglab.payment.order.PaymentOrderLine
import com.pglab.payment.shared.CurrencyCode
import com.pglab.payment.shared.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AuthorizationLinePortionTest {
    @Test
    fun `승인 라인 귀속분은 판매자 스냅샷과 금액을 보관한다`() {
        val order = PaymentOrder(
            merchantId = "platform",
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
        val allocation = PaymentAllocation(
            paymentOrder = order,
            payerReference = "payer-A",
            allocationAmount = Money(50_000L, CurrencyCode.KRW),
            sequence = 1,
        )
        val authorization = Authorization(
            paymentAllocation = allocation,
            instrumentType = InstrumentType.CARD,
            requestedAmount = Money(50_000L, CurrencyCode.KRW),
            approvedAmount = Money(50_000L, CurrencyCode.KRW),
            pgTransactionId = "pg-1",
        )
        val portion = AuthorizationLinePortion(
            paymentOrderLine = line,
            payeeId = "seller-A",
            amount = Money(30_000L, CurrencyCode.KRW),
            sequence = 1,
        )

        authorization.addLinePortion(portion)

        assertEquals("seller-A", portion.payeeId)
        assertEquals(30_000L, portion.amount.amount)
        assertEquals(line, portion.paymentOrderLine)
        assertEquals(authorization, portion.authorization)
    }

    @Test
    fun `승인 라인 귀속분은 주문 라인과 다른 판매자 스냅샷을 가질 수 없다`() {
        val order = PaymentOrder(
            merchantId = "platform",
            merchantOrderId = "order-002",
            totalAmount = Money(30_000L, CurrencyCode.KRW),
        )
        val line = PaymentOrderLine(
            lineReference = "line-1",
            payeeId = "seller-A",
            lineAmount = Money(30_000L, CurrencyCode.KRW),
            quantity = 1,
        )
        order.addLine(line)

        assertFailsWith<IllegalArgumentException> {
            AuthorizationLinePortion(
                paymentOrderLine = line,
                payeeId = "seller-B",
                amount = Money(30_000L, CurrencyCode.KRW),
                sequence = 1,
            )
        }
    }
}
