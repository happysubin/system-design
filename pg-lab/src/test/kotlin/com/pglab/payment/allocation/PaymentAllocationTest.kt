package com.pglab.payment.allocation

import com.pglab.payment.shared.CurrencyCode
import com.pglab.payment.shared.Money
import kotlin.test.Test
import kotlin.test.assertEquals

class PaymentAllocationTest {
    @Test
    fun `부담 단위는 부담 주체와 금액을 보관한다`() {
        val allocation = PaymentAllocation(
            payerReference = "user-A",
            allocationAmount = Money(20_000L, CurrencyCode.KRW),
            sequence = 1,
        )

        assertEquals("user-A", allocation.payerReference)
        assertEquals(20_000L, allocation.allocationAmount.amount)
        assertEquals(PaymentAllocationStatus.READY, allocation.status)
    }
}
