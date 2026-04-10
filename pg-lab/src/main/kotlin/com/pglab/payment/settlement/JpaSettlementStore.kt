package com.pglab.payment.settlement

import jakarta.persistence.EntityManager
import org.springframework.stereotype.Component

@Component
class JpaSettlementStore(
    private val entityManager: EntityManager,
) : SettlementStore {
    override fun saveAll(settlements: List<Settlement>): List<Settlement> {
        settlements.forEach(entityManager::persist)
        entityManager.flush()
        return settlements
    }
}
