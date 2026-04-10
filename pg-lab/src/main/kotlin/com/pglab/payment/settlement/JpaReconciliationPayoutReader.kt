package com.pglab.payment.settlement

import org.springframework.stereotype.Component

@Component
class JpaReconciliationPayoutReader(
    private val payoutRepository: PayoutRepository,
) : ReconciliationPayoutReader {
    override fun findReconciliationTargets(): List<Payout> =
        payoutRepository.findAll().filter { it.status == PayoutStatus.RECONCILING }
}
