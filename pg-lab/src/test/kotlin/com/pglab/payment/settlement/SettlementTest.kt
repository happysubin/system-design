package com.pglab.payment.settlement

import com.pglab.payment.shared.CurrencyCode
import com.pglab.payment.shared.Money
import kotlin.test.Test
import kotlin.test.assertEquals

class SettlementTest {
    @Test
    fun `정산은 총액 수수료 순액을 보관한다`() {
        val settlement = Settlement(
            merchantId = "merchant-1",
            grossAmount = Money(50_000L, CurrencyCode.KRW),
            feeAmount = Money(1_000L, CurrencyCode.KRW),
            netAmount = Money(49_000L, CurrencyCode.KRW),
        )

        assertEquals(49_000L, settlement.netAmount.amount)
        assertEquals(SettlementStatus.READY, settlement.status)
    }
}
