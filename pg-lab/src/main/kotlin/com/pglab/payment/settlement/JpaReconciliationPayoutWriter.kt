package com.pglab.payment.settlement

import org.springframework.stereotype.Component

@Component
class JpaReconciliationPayoutWriter(
    private val payoutRepository: PayoutRepository,
) : ReconciliationPayoutWriter {
    override fun saveAll(payouts: List<Payout>): List<Payout> = payoutRepository.saveAll(payouts)
}
