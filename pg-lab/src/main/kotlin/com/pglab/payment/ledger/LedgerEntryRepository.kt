package com.pglab.payment.ledger

import org.springframework.data.jpa.repository.JpaRepository

interface LedgerEntryRepository : JpaRepository<LedgerEntry, Long> {
    fun findAllByAuthorizationIdAndTypeIn(authorizationId: Long, types: Collection<LedgerEntryType>): List<LedgerEntry>
}
