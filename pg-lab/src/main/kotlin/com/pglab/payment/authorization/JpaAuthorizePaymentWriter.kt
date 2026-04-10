package com.pglab.payment.authorization

import jakarta.persistence.EntityManager
import org.springframework.stereotype.Component

@Component
class JpaAuthorizePaymentWriter(
    private val entityManager: EntityManager,
) : AuthorizePaymentWriter {
    override fun save(result: AuthorizePaymentResult): AuthorizePaymentResult {
        entityManager.persist(result.order)
        result.allocations.forEach(entityManager::persist)
        result.authorizations.forEach(entityManager::persist)
        result.ledgerEntries.forEach(entityManager::persist)
        entityManager.flush()

        return result
    }
}
