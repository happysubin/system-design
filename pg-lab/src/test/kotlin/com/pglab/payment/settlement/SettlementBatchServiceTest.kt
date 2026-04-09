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
                SettlementLedgerRecord("merchant-1", LedgerEntryType.AUTH_CAPTURED, Money(100_000L, CurrencyCode.KRW)),
                SettlementLedgerRecord("merchant-1", LedgerEntryType.CANCELLED, Money(10_000L, CurrencyCode.KRW)),
                SettlementLedgerRecord("merchant-1", LedgerEntryType.FEE_BOOKED, Money(3_000L, CurrencyCode.KRW)),
                SettlementLedgerRecord("merchant-2", LedgerEntryType.AUTH_CAPTURED, Money(50_000L, CurrencyCode.KRW)),
                SettlementLedgerRecord("merchant-2", LedgerEntryType.FEE_BOOKED, Money(500L, CurrencyCode.KRW)),
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

        val merchantTwo = settlementsByMerchant.getValue("merchant-2")
        assertEquals(50_000L, merchantTwo.grossAmount.amount)
        assertEquals(500L, merchantTwo.feeAmount.amount)
        assertEquals(49_500L, merchantTwo.netAmount.amount)
        assertEquals(SettlementStatus.SCHEDULED, merchantTwo.status)
        assertEquals(targetDate, merchantTwo.scheduledDate)

        assertEquals(2, store.savedSettlements.size)
    }

    private class FakeSettlementLedgerReader(
        private val records: List<SettlementLedgerRecord>,
    ) : SettlementLedgerReader {
        override fun findSettlableEntries(targetDate: LocalDate): List<SettlementLedgerRecord> = records
    }

    private class FakeSettlementStore : SettlementStore {
        var savedSettlements: List<Settlement> = emptyList()

        override fun saveAll(settlements: List<Settlement>): List<Settlement> {
            savedSettlements = settlements
            return settlements
        }
    }
}
