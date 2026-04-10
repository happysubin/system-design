package com.pglab.payment.settlement

import com.pglab.payment.ledger.LedgerEntry
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/**
 * 특정 Settlement가 어떤 원장 사실들을 근거로 계산되었는지 보존하는 연결 엔티티다.
 *
 * Settlement는 집계 결과를 저장하고,
 * SettlementLine은 그 집계에 포함된 source ledger fact를 추적 가능하게 만든다.
 */
@Entity
@Table(
    name = "settlement_lines",
    uniqueConstraints = [UniqueConstraint(name = "uk_settlement_lines_ledger_entry", columnNames = ["ledger_entry_id"])],
)
class SettlementLine(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "settlement_id", nullable = false)
    val settlement: Settlement? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ledger_entry_id", nullable = false)
    val ledgerEntry: LedgerEntry? = null,
)
