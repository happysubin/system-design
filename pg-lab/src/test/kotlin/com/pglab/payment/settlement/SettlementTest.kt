package com.pglab.payment.settlement

import com.pglab.payment.ledger.LedgerEntry
import com.pglab.payment.ledger.LedgerEntryType
import com.pglab.payment.shared.CurrencyCode
import com.pglab.payment.shared.Money
import java.time.LocalDate
import java.time.OffsetDateTime
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

    @Test
    fun `정산은 근거 원장 라인을 보관할 수 있다`() {
        val settlement = Settlement(
            merchantId = "merchant-1",
            grossAmount = Money(50_000L, CurrencyCode.KRW),
            feeAmount = Money(1_000L, CurrencyCode.KRW),
            netAmount = Money(49_000L, CurrencyCode.KRW),
        )
        val ledgerEntry = LedgerEntry(
            type = LedgerEntryType.AUTH_CAPTURED,
            amount = Money(50_000L, CurrencyCode.KRW),
            referenceTransactionId = "tx-1",
            description = "settlement source",
        )

        val settlementLine = SettlementLine(
            settlement = settlement,
            ledgerEntry = ledgerEntry,
        )

        assertEquals(settlement, settlementLine.settlement)
        assertEquals(ledgerEntry, settlementLine.ledgerEntry)
    }

    @Test
    fun `정산은 지급 처리를 시작하면 처리중 상태가 된다`() {
        val settlement = Settlement(
            merchantId = "merchant-1",
            grossAmount = Money(50_000L, CurrencyCode.KRW),
            feeAmount = Money(1_000L, CurrencyCode.KRW),
            netAmount = Money(49_000L, CurrencyCode.KRW),
            status = SettlementStatus.SCHEDULED,
            scheduledDate = LocalDate.of(2026, 4, 10),
        )

        settlement.markProcessing()

        assertEquals(SettlementStatus.PROCESSING, settlement.status)
    }

    @Test
    fun `정산은 지급이 완료되면 완료 상태와 완료 시각을 가진다`() {
        val completedAt = OffsetDateTime.parse("2026-04-10T12:00:00+09:00")
        val settlement = Settlement(
            merchantId = "merchant-1",
            grossAmount = Money(50_000L, CurrencyCode.KRW),
            feeAmount = Money(1_000L, CurrencyCode.KRW),
            netAmount = Money(49_000L, CurrencyCode.KRW),
            status = SettlementStatus.PROCESSING,
        )

        settlement.markPaid(completedAt)

        assertEquals(SettlementStatus.PAID, settlement.status)
        assertEquals(completedAt, settlement.settledAt)
    }

    @Test
    fun `정산은 지급 실패를 기록할 수 있다`() {
        val settlement = Settlement(
            merchantId = "merchant-1",
            grossAmount = Money(50_000L, CurrencyCode.KRW),
            feeAmount = Money(1_000L, CurrencyCode.KRW),
            netAmount = Money(49_000L, CurrencyCode.KRW),
            status = SettlementStatus.PROCESSING,
        )

        settlement.markFailed()

        assertEquals(SettlementStatus.FAILED, settlement.status)
    }
}
