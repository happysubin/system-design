package com.pglab.payment.settlement

import com.pglab.payment.ledger.LedgerEntryType
import com.pglab.payment.shared.CurrencyCode
import com.pglab.payment.shared.Money
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class SettlementBatchServiceTest {
    @Test
    fun `정산 배치는 대상 일자의 payee별 정산 예정 건을 생성한다`() {
        val targetDate = LocalDate.of(2026, 4, 10)
        val reader = FakeSettlementLedgerReader(
            listOf(
                SettlementLedgerRecord(ledgerEntry(1L, "payee-A", LedgerEntryType.AUTH_CAPTURED, 100_000L), "payee-A"),
                SettlementLedgerRecord(ledgerEntry(2L, "payee-A", LedgerEntryType.CANCELLED, 10_000L), "payee-A"),
                SettlementLedgerRecord(ledgerEntry(3L, "payee-A", LedgerEntryType.FEE_BOOKED, 3_000L), "payee-A"),
                SettlementLedgerRecord(ledgerEntry(4L, "payee-B", LedgerEntryType.AUTH_CAPTURED, 50_000L), "payee-B"),
                SettlementLedgerRecord(ledgerEntry(5L, "payee-B", LedgerEntryType.FEE_BOOKED, 500L), "payee-B"),
            ),
        )
        val store = FakeSettlementStore()
        val service = SettlementBatchService(reader, store)

        val settlements = service.createScheduledSettlements(targetDate)
        val settlementsByPayee = settlements.associateBy { it.payeeId }

        assertEquals(2, settlements.size)

        val payeeA = settlementsByPayee.getValue("payee-A")
        assertEquals(90_000L, payeeA.grossAmount.amount)
        assertEquals(3_000L, payeeA.feeAmount.amount)
        assertEquals(87_000L, payeeA.netAmount.amount)
        assertEquals(SettlementStatus.SCHEDULED, payeeA.status)
        assertEquals(targetDate, payeeA.scheduledDate)
        assertEquals(3, payeeA.lines.size)
        assertEquals(setOf(1L, 2L, 3L), payeeA.lines.mapNotNull { it.ledgerEntry?.id }.toSet())

        val payeeB = settlementsByPayee.getValue("payee-B")
        assertEquals(50_000L, payeeB.grossAmount.amount)
        assertEquals(500L, payeeB.feeAmount.amount)
        assertEquals(49_500L, payeeB.netAmount.amount)
        assertEquals(SettlementStatus.SCHEDULED, payeeB.status)
        assertEquals(targetDate, payeeB.scheduledDate)
        assertEquals(2, payeeB.lines.size)
        assertEquals(setOf(4L, 5L), payeeB.lines.mapNotNull { it.ledgerEntry?.id }.toSet())

        assertEquals(2, store.savedSettlements.size)
    }

    @Test
    fun `정산 배치는 같은 payee라도 통화가 다르면 별도 정산 건으로 분리한다`() {
        val targetDate = LocalDate.of(2026, 4, 10)
        val reader = FakeSettlementLedgerReader(
            listOf(
                SettlementLedgerRecord(ledgerEntry(1L, "payee-A", LedgerEntryType.AUTH_CAPTURED, 100_000L, CurrencyCode.KRW), "payee-A"),
                SettlementLedgerRecord(ledgerEntry(2L, "payee-A", LedgerEntryType.FEE_BOOKED, 3_000L, CurrencyCode.KRW), "payee-A"),
                SettlementLedgerRecord(ledgerEntry(3L, "payee-A", LedgerEntryType.AUTH_CAPTURED, 80_000L, CurrencyCode.USD), "payee-A"),
                SettlementLedgerRecord(ledgerEntry(4L, "payee-A", LedgerEntryType.FEE_BOOKED, 500L, CurrencyCode.USD), "payee-A"),
            ),
        )
        val store = FakeSettlementStore()
        val service = SettlementBatchService(reader, store)

        val settlements = service.createScheduledSettlements(targetDate)

        assertEquals(2, settlements.size)

        val krwSettlement = settlements.single { it.grossAmount.currency == CurrencyCode.KRW }
        assertEquals("payee-A", krwSettlement.payeeId)
        assertEquals(100_000L, krwSettlement.grossAmount.amount)
        assertEquals(3_000L, krwSettlement.feeAmount.amount)
        assertEquals(97_000L, krwSettlement.netAmount.amount)
        assertEquals(setOf(1L, 2L), krwSettlement.lines.mapNotNull { it.ledgerEntry?.id }.toSet())

        val usdSettlement = settlements.single { it.grossAmount.currency == CurrencyCode.USD }
        assertEquals("payee-A", usdSettlement.payeeId)
        assertEquals(80_000L, usdSettlement.grossAmount.amount)
        assertEquals(500L, usdSettlement.feeAmount.amount)
        assertEquals(79_500L, usdSettlement.netAmount.amount)
        assertEquals(setOf(3L, 4L), usdSettlement.lines.mapNotNull { it.ledgerEntry?.id }.toSet())
    }

    private class FakeSettlementLedgerReader(
        private val records: List<SettlementLedgerRecord>,
    ) : SettlementLedgerReader {
        override fun findSettlableEntries(targetDate: LocalDate): List<SettlementLedgerRecord> = records
    }

    private fun ledgerEntry(
        id: Long,
        payeeId: String,
        type: LedgerEntryType,
        amount: Long,
        currency: CurrencyCode = CurrencyCode.KRW,
    ) =
        com.pglab.payment.ledger.LedgerEntry(
            id = id,
            payeeId = payeeId,
            type = type,
            amount = Money(amount, currency),
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
