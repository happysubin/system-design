package com.pglab.payment.settlement

import com.pglab.payment.ledger.LedgerEntryType
import com.pglab.payment.ledger.LedgerEntry
import com.pglab.payment.shared.CurrencyCode
import com.pglab.payment.shared.Money
import java.time.LocalDate

/**
 * 정산 대상 원장 데이터를 읽어 가맹점별 예정 정산 건을 생성하는 배치 서비스다.
 *
 * 이 서비스는 Spring Batch 잡 자체가 아니라,
 * "정산일 기준 전체 가맹점의 Settlement를 만든다"는 애플리케이션 유스케이스를 표현한다.
 */
class SettlementBatchService(
    private val ledgerReader: SettlementLedgerReader,
    private val settlementStore: SettlementStore,
) {
    fun createScheduledSettlements(targetDate: LocalDate): List<Settlement> {
        val settlements = ledgerReader.findSettlableEntries(targetDate)
            .groupBy { it.merchantId }
            .map { (merchantId, records) ->
                val currency = records.firstOrNull()?.ledgerEntry?.amount?.currency ?: CurrencyCode.KRW
                val grossAmount = records
                    .filter { it.ledgerEntry.type == LedgerEntryType.AUTH_CAPTURED }
                    .sumOf { it.ledgerEntry.amount.amount } - records
                    .filter { it.ledgerEntry.type == LedgerEntryType.CANCELLED || it.ledgerEntry.type == LedgerEntryType.REFUNDED }
                    .sumOf { it.ledgerEntry.amount.amount }
                val feeAmount = records
                    .filter { it.ledgerEntry.type == LedgerEntryType.FEE_BOOKED }
                    .sumOf { it.ledgerEntry.amount.amount }

                val settlement = Settlement(
                    merchantId = merchantId,
                    grossAmount = Money(grossAmount, currency),
                    feeAmount = Money(feeAmount, currency),
                    netAmount = Money(grossAmount - feeAmount, currency),
                    status = SettlementStatus.SCHEDULED,
                    scheduledDate = targetDate,
                )

                records.forEach { record ->
                    settlement.lines.add(
                        SettlementLine(
                            settlement = settlement,
                            ledgerEntry = record.ledgerEntry,
                        ),
                    )
                }

                settlement
            }

        return settlementStore.saveAll(settlements)
    }
}

data class SettlementLedgerRecord(
    val ledgerEntry: LedgerEntry,
    val merchantId: String,
)

interface SettlementLedgerReader {
    fun findSettlableEntries(targetDate: LocalDate): List<SettlementLedgerRecord>
}

interface SettlementStore {
    fun saveAll(settlements: List<Settlement>): List<Settlement>
}
