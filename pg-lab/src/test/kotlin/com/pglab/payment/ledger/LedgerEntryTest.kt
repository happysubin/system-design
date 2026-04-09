package com.pglab.payment.ledger

import com.pglab.payment.shared.CurrencyCode
import com.pglab.payment.shared.Money
import kotlin.test.Test
import kotlin.test.assertEquals

class LedgerEntryTest {
    @Test
    fun `원장 엔트리는 이벤트 종류와 금액을 보관한다`() {
        val entry = LedgerEntry(
            type = LedgerEntryType.AUTH_CAPTURED,
            amount = Money(30_000L, CurrencyCode.KRW),
            referenceTransactionId = "pg-tx-001",
        )

        assertEquals(LedgerEntryType.AUTH_CAPTURED, entry.type)
        assertEquals(30_000L, entry.amount.amount)
    }
}
