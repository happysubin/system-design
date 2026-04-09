package com.pglab.payment.jpa

import com.pglab.payment.allocation.PaymentAllocation
import com.pglab.payment.authorization.Authorization
import com.pglab.payment.authorization.InstrumentType
import com.pglab.payment.ledger.LedgerEntry
import com.pglab.payment.ledger.LedgerEntryType
import com.pglab.payment.order.PaymentOrder
import com.pglab.payment.settlement.Settlement
import com.pglab.payment.shared.CurrencyCode
import com.pglab.payment.shared.Money
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import kotlin.test.assertNotNull

@DataJpaTest
class PaymentJpaMappingTest(
    @Autowired private val entityManager: EntityManager,
) {
    @Test
    fun `결제 도메인 엔티티는 JPA로 저장할 수 있다`() {
        val order = PaymentOrder(
            merchantId = "merchant-1",
            merchantOrderId = "order-100",
            totalAmount = Money(50_000L, CurrencyCode.KRW),
        )
        entityManager.persist(order)

        val allocation = PaymentAllocation(
            paymentOrder = order,
            payerReference = "user-A",
            allocationAmount = Money(20_000L, CurrencyCode.KRW),
            sequence = 1,
        )
        entityManager.persist(allocation)

        val authorization = Authorization(
            paymentAllocation = allocation,
            instrumentType = InstrumentType.CARD,
            requestedAmount = Money(20_000L, CurrencyCode.KRW),
            approvedAmount = Money(20_000L, CurrencyCode.KRW),
            pgTransactionId = "pg-tx-100",
        )
        entityManager.persist(authorization)

        val ledgerEntry = LedgerEntry(
            paymentOrder = order,
            paymentAllocation = allocation,
            authorization = authorization,
            type = LedgerEntryType.AUTH_CAPTURED,
            amount = Money(20_000L, CurrencyCode.KRW),
            referenceTransactionId = "pg-tx-100",
            description = "initial authorization",
        )
        entityManager.persist(ledgerEntry)

        val settlement = Settlement(
            merchantId = "merchant-1",
            grossAmount = Money(50_000L, CurrencyCode.KRW),
            feeAmount = Money(1_000L, CurrencyCode.KRW),
            netAmount = Money(49_000L, CurrencyCode.KRW),
        )
        entityManager.persist(settlement)

        entityManager.flush()
        entityManager.clear()

        assertNotNull(order.id)
        assertNotNull(allocation.id)
        assertNotNull(authorization.id)
        assertNotNull(ledgerEntry.id)
        assertNotNull(settlement.id)
    }
}
