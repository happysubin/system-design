package com.pglab.payment.authorization

import jakarta.persistence.EntityManager
import org.springframework.stereotype.Component

@Component
class JpaRefundWriter(
    private val entityManager: EntityManager,
) : RefundWriter {
    override fun save(result: RefundResult): RefundResult {
        entityManager.merge(result.order)
        entityManager.merge(result.allocation)
        entityManager.merge(result.authorization)
        result.ledgerEntries.forEach(entityManager::persist)
        entityManager.flush()
        return result
    }
}
