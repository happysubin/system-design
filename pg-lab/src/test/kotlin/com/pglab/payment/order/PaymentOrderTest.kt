package com.pglab.payment.order

import com.pglab.payment.shared.CurrencyCode
import com.pglab.payment.shared.Money
import kotlin.test.Test
import kotlin.test.assertEquals

class PaymentOrderTest {
    @Test
    fun `결제 주문은 총액과 초기 상태를 보관한다`() {
        val order = PaymentOrder(
            merchantOrderId = "order-001",
            totalAmount = Money(50_000L, CurrencyCode.KRW),
        )

        assertEquals(PaymentOrderStatus.READY, order.status)
        assertEquals(50_000L, order.totalAmount.amount)
    }
}
