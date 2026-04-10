package com.pglab.payment.settlement

import com.pglab.payment.shared.CurrencyCode
import com.pglab.payment.shared.Money
import kotlin.test.Test
import kotlin.test.assertEquals

class PayoutTest {
    @Test
    fun `지급 시도는 정산 금액과 초기 상태를 보관한다`() {
        val settlement = Settlement(
            merchantId = "merchant-1",
            grossAmount = Money(100_000L, CurrencyCode.KRW),
            feeAmount = Money(3_000L, CurrencyCode.KRW),
            netAmount = Money(97_000L, CurrencyCode.KRW),
        )

        val payout = Payout(
            settlement = settlement,
            requestedAmount = Money(97_000L, CurrencyCode.KRW),
            bankCode = "081",
            bankAccountNumber = "110-123-456789",
            accountHolderName = "상점A",
            bankTransferRequestId = "payout-req-001",
        )

        assertEquals(settlement, payout.settlement)
        assertEquals(97_000L, payout.requestedAmount.amount)
        assertEquals(PayoutStatus.REQUESTED, payout.status)
        assertEquals(0, payout.retryCount)
    }
}
