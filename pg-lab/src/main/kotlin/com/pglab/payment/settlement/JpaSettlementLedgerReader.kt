package com.pglab.payment.settlement

import com.pglab.payment.ledger.LedgerEntry
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.OffsetDateTime

@Component
class JpaSettlementLedgerReader(
    private val entityManager: EntityManager,
) : SettlementLedgerReader {
    override fun findSettlableEntries(targetDate: LocalDate): List<SettlementLedgerRecord> {
        val start = targetDate.atStartOfDay().atOffset(java.time.ZoneOffset.ofHours(9))
        val end = targetDate.plusDays(1).atStartOfDay().atOffset(java.time.ZoneOffset.ofHours(9))

        val entries = entityManager.createQuery(
            """
            select le
            from LedgerEntry le
            left join SettlementLine sl on sl.ledgerEntry = le
            where sl.id is null
              and le.occurredAt >= :start
              and le.occurredAt < :end
            """.trimIndent(),
            LedgerEntry::class.java,
        )
            .setParameter("start", start)
            .setParameter("end", end)
            .resultList

        return entries.map { SettlementLedgerRecord(it, it.payeeId) }
    }
}
