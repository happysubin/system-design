package com.pglab.payment.domain

import com.pglab.payment.authorization.Authorization
import com.pglab.payment.authorization.InstrumentType
import com.pglab.payment.shared.CurrencyCode
import com.pglab.payment.shared.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PaymentDomainInvariantTest {
    @Test
    fun `부분 취소는 남은 취소 가능 금액을 감소시킨다`() {
        val authorization = Authorization(
            instrumentType = InstrumentType.CARD,
            requestedAmount = Money(30_000L, CurrencyCode.KRW),
            approvedAmount = Money(30_000L, CurrencyCode.KRW),
            pgTransactionId = "pg-tx-001",
        )

        authorization.cancel(Money(10_000L, CurrencyCode.KRW))

        assertEquals(20_000L, authorization.remainingCancelableAmount.amount)
    }

    @Test
    fun `취소 금액은 남은 취소 가능 금액을 초과할 수 없다`() {
        val authorization = Authorization(
            instrumentType = InstrumentType.CARD,
            requestedAmount = Money(30_000L, CurrencyCode.KRW),
            approvedAmount = Money(30_000L, CurrencyCode.KRW),
            pgTransactionId = "pg-tx-002",
        )

        assertFailsWith<IllegalArgumentException> {
            authorization.cancel(Money(40_000L, CurrencyCode.KRW))
        }
    }
}
