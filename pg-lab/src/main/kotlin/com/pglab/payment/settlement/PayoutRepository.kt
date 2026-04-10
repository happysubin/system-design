package com.pglab.payment.settlement

import org.springframework.data.jpa.repository.JpaRepository

interface PayoutRepository : JpaRepository<Payout, Long> {
    fun findAllBySettlementId(settlementId: Long): List<Payout>
}
