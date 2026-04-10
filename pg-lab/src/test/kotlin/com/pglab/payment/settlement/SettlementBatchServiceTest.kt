package com.pglab.payment.settlement

import com.pglab.payment.ledger.LedgerEntryType
import com.pglab.payment.shared.CurrencyCode
import com.pglab.payment.shared.Money
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class SettlementBatchServiceTest {
    @Test
    fun `정산 배치는 대상 일자의 가맹점별 정산 예정 건을 생성한다`() {
        val targetDate = LocalDate.of(2026, 4, 10)
        val reader = FakeSettlementLedgerReader(
            listOf(
                SettlementLedgerRecord(ledgerEntry(1L, LedgerEntryType.AUTH_CAPTURED, 100_000L), "merchant-1"),
                SettlementLedgerRecord(ledgerEntry(2L, LedgerEntryType.CANCELLED, 10_000L), "merchant-1"),
                SettlementLedgerRecord(ledgerEntry(3L, LedgerEntryType.FEE_BOOKED, 3_000L), "merchant-1"),
                SettlementLedgerRecord(ledgerEntry(4L, LedgerEntryType.AUTH_CAPTURED, 50_000L), "merchant-2"),
                SettlementLedgerRecord(ledgerEntry(5L, LedgerEntryType.FEE_BOOKED, 500L), "merchant-2"),
            ),
        )
        val store = FakeSettlementStore()
        val service = SettlementBatchService(reader, store)

        val settlements = service.createScheduledSettlements(targetDate)
        val settlementsByMerchant = settlements.associateBy { it.merchantId }

        assertEquals(2, settlements.size)

        val merchantOne = settlementsByMerchant.getValue("merchant-1")
        assertEquals(90_000L, merchantOne.grossAmount.amount)
        assertEquals(3_000L, merchantOne.feeAmount.amount)
        assertEquals(87_000L, merchantOne.netAmount.amount)
        assertEquals(SettlementStatus.SCHEDULED, merchantOne.status)
        assertEquals(targetDate, merchantOne.scheduledDate)
        assertEquals(3, merchantOne.lines.size)
        assertEquals(setOf(1L, 2L, 3L), merchantOne.lines.mapNotNull { it.ledgerEntry?.id }.toSet())

        val merchantTwo = settlementsByMerchant.getValue("merchant-2")
        assertEquals(50_000L, merchantTwo.grossAmount.amount)
        assertEquals(500L, merchantTwo.feeAmount.amount)
        assertEquals(49_500L, merchantTwo.netAmount.amount)
        assertEquals(SettlementStatus.SCHEDULED, merchantTwo.status)
        assertEquals(targetDate, merchantTwo.scheduledDate)
        assertEquals(2, merchantTwo.lines.size)
        assertEquals(setOf(4L, 5L), merchantTwo.lines.mapNotNull { it.ledgerEntry?.id }.toSet())

        assertEquals(2, store.savedSettlements.size)
    }

    private class FakeSettlementLedgerReader(
        private val records: List<SettlementLedgerRecord>,
    ) : SettlementLedgerReader {
        override fun findSettlableEntries(targetDate: LocalDate): List<SettlementLedgerRecord> = records
    }

    private fun ledgerEntry(id: Long, type: LedgerEntryType, amount: Long) =
        com.pglab.payment.ledger.LedgerEntry(
            id = id,
            type = type,
            amount = Money(amount, CurrencyCode.KRW),
            referenceTransactionId = "tx-$id",
            description = "test-source-$id",
        )

    private class FakeSettlementStore : SettlementStore {
        var savedSettlements: List<Settlement> = emptyList()

        override fun saveAll(settlements: List<Settlement>): List<Settlement> {
            savedSettlements = settlements
            return settlements
        }
    }
}
