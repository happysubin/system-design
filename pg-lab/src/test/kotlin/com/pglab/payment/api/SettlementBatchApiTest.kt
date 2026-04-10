package com.pglab.payment.api

import com.pglab.payment.allocation.PaymentAllocation
import com.pglab.payment.authorization.Authorization
import com.pglab.payment.authorization.InstrumentType
import com.pglab.payment.ledger.LedgerEntry
import com.pglab.payment.ledger.LedgerEntryType
import com.pglab.payment.order.PaymentOrder
import com.pglab.payment.shared.CurrencyCode
import com.pglab.payment.shared.Money
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SettlementBatchApiTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val entityManager: EntityManager,
) {
    @Test
    fun `정산 배치 API는 대상 날짜의 settlement를 생성하고 요약 응답을 반환한다`() {
        prepareLedgerEntries()

        mockMvc.perform(
            post("/api/settlements/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "targetDate": "2026-04-10"
                    }
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.targetDate").value("2026-04-10"))
            .andExpect(jsonPath("$.settlementCount").value(1))
            .andExpect(jsonPath("$.merchantCount").value(1))
            .andExpect(jsonPath("$.totalGrossAmount").value(50000))
            .andExpect(jsonPath("$.totalNetAmount").value(49000))
    }

    private fun prepareLedgerEntries() {
        val order = PaymentOrder(
            merchantId = "merchant-1",
            merchantOrderId = "order-settlement-api-001",
            totalAmount = Money(50_000L, CurrencyCode.KRW),
        )
        entityManager.persist(order)

        val allocation = PaymentAllocation(
            paymentOrder = order,
            payerReference = "payer-A",
            allocationAmount = Money(50_000L, CurrencyCode.KRW),
            sequence = 1,
        )
        entityManager.persist(allocation)

        val authorization = Authorization(
            paymentAllocation = allocation,
            instrumentType = InstrumentType.CARD,
            requestedAmount = Money(50_000L, CurrencyCode.KRW),
            approvedAmount = Money(50_000L, CurrencyCode.KRW),
            pgTransactionId = "settlement-auth-001",
        )
        entityManager.persist(authorization)

        entityManager.persist(
            LedgerEntry(
                paymentOrder = order,
                paymentAllocation = allocation,
                authorization = authorization,
                type = LedgerEntryType.AUTH_CAPTURED,
                amount = Money(50_000L, CurrencyCode.KRW),
                occurredAt = OffsetDateTime.parse("2026-04-10T10:00:00+09:00"),
                referenceTransactionId = "settlement-auth-001",
                description = "settlement source auth",
            ),
        )
        entityManager.persist(
            LedgerEntry(
                paymentOrder = order,
                paymentAllocation = allocation,
                authorization = authorization,
                type = LedgerEntryType.FEE_BOOKED,
                amount = Money(1_000L, CurrencyCode.KRW),
                occurredAt = OffsetDateTime.parse("2026-04-10T10:05:00+09:00"),
                referenceTransactionId = "settlement-fee-001",
                description = "settlement source fee",
            ),
        )

        entityManager.flush()
        entityManager.clear()
    }
}
