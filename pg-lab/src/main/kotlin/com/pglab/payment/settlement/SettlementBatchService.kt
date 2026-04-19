package com.pglab.payment.settlement

import com.pglab.payment.ledger.LedgerEntryType
import com.pglab.payment.ledger.LedgerEntry
import com.pglab.payment.shared.CurrencyCode
import com.pglab.payment.shared.Money
import org.springframework.stereotype.Service
import java.time.LocalDate

/**
 * 정산 대상 원장 데이터를 읽어 payee별 예정 정산 건을 생성하는 배치 서비스다.
 *
 * 이 서비스는 Spring Batch 잡 자체가 아니라,
 * "정산일 기준 전체 payee의 Settlement를 만든다"는 애플리케이션 유스케이스를 표현한다.
 */
@Service
class SettlementBatchService(
    private val ledgerReader: SettlementLedgerReader,
    private val settlementStore: SettlementStore,
) {
    fun createScheduledSettlements(targetDate: LocalDate): List<Settlement> {
        // 먼저 정산 기준일에 정산 가능한 원장 사실들을 모두 읽는다.
        // 이 단계에서는 아직 가맹점별 정산 건으로 묶이지 않은 상태다.
        val settlements = ledgerReader.findSettlableEntries(targetDate)
            .groupBy { it.payeeId to it.ledgerEntry.amount.currency }
            .map { groupedRecords ->
                val (groupingKey, records) = groupedRecords
                val (payeeId, currency) = groupingKey
                // 같은 정산건 안에서는 통화가 같다고 가정하므로 첫 원장 통화를 대표값으로 사용한다.
                // gross는 승인 총액에서 취소/환불로 빠진 금액을 제외한 실제 정산 대상 기준 총액이다.
                val grossAmount = records
                    .filter { it.ledgerEntry.type == LedgerEntryType.AUTH_CAPTURED }
                    .sumOf { it.ledgerEntry.amount.amount } - records
                    .filter { it.ledgerEntry.type == LedgerEntryType.CANCELLED || it.ledgerEntry.type == LedgerEntryType.REFUNDED }
                    .sumOf { it.ledgerEntry.amount.amount }

                // fee는 정산 과정에서 차감될 수수료 원장을 따로 모은 값이다.
                val feeAmount = records
                    .filter { it.ledgerEntry.type == LedgerEntryType.FEE_BOOKED }
                    .sumOf { it.ledgerEntry.amount.amount }

                // 이 시점의 Settlement는 "실제 지급 완료"가 아니라
                // 정산 예정 건이 계산되었다는 의미이므로 SCHEDULED로 생성한다.
                val settlement = Settlement(
                    payeeId = payeeId,
                    grossAmount = Money(grossAmount, currency),
                    feeAmount = Money(feeAmount, currency),
                    netAmount = Money(grossAmount - feeAmount, currency),
                    status = SettlementStatus.SCHEDULED,
                    scheduledDate = targetDate,
                )

                // SettlementLine으로 어떤 원장 사실들이 이번 정산건 계산에 포함되었는지 보존한다.
                records.forEach { record ->
                    settlement.addLine(
                        SettlementLine(
                            ledgerEntry = record.ledgerEntry,
                        ),
                    )
                }

                settlement
            }

        // 만들어진 정산건들은 저장 포트를 통해 영속화한다.
        return settlementStore.saveAll(settlements)
    }
}

data class SettlementLedgerRecord(
    val ledgerEntry: LedgerEntry,
    val payeeId: String,
)

interface SettlementLedgerReader {
    fun findSettlableEntries(targetDate: LocalDate): List<SettlementLedgerRecord>
}

interface SettlementStore {
    fun saveAll(settlements: List<Settlement>): List<Settlement>
}
