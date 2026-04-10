package com.pglab.payment.authorization

import jakarta.persistence.EntityManager
import org.springframework.stereotype.Component

@Component
class JpaPartialCancellationWriter(
    private val entityManager: EntityManager,
) : PartialCancellationWriter {
    override fun save(result: PartialCancellationResult): PartialCancellationResult {
        entityManager.merge(result.order)
        entityManager.merge(result.allocation)
        entityManager.merge(result.authorization)
        entityManager.persist(result.ledgerEntry)
        entityManager.flush()
        return result
    }
}
