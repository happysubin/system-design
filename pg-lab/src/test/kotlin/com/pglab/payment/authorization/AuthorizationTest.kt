package com.pglab.payment.authorization

import com.pglab.payment.shared.CurrencyCode
import com.pglab.payment.shared.Money
import kotlin.test.Test
import kotlin.test.assertEquals

class AuthorizationTest {
    @Test
    fun `승인 결과는 승인 금액으로 취소 가능 잔액을 초기화한다`() {
        val authorization = Authorization(
            instrumentType = InstrumentType.CARD,
            requestedAmount = Money(30_000L, CurrencyCode.KRW),
            approvedAmount = Money(30_000L, CurrencyCode.KRW),
            pgTransactionId = "pg-tx-001",
        )

        assertEquals(30_000L, authorization.remainingCancelableAmount.amount)
        assertEquals(AuthorizationStatus.APPROVED, authorization.status)
    }
}
