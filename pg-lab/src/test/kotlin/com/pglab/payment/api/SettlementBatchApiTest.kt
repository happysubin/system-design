package com.pglab.payment.api

import com.pglab.payment.allocation.PaymentAllocation
import com.pglab.payment.authorization.Authorization
import com.pglab.payment.authorization.InstrumentType
import com.pglab.payment.ledger.LedgerEntry
import com.pglab.payment.ledger.LedgerEntryType
import com.pglab.payment.order.PaymentOrder
import com.pglab.payment.order.PaymentOrderLine
import com.pglab.payment.shared.CurrencyCode
import com.pglab.payment.shared.Money
import jakarta.servlet.ServletException
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
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SettlementBatchApiTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val entityManager: EntityManager,
) {
    @Test
    fun `정산 배치 API는 대상 날짜의 payee별 settlement를 생성하고 요약 응답을 반환한다`() {
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
            .andExpect(jsonPath("$.settlementCount").value(2))
            .andExpect(jsonPath("$.payeeCount").value(2))
            .andExpect(jsonPath("$.totalGrossAmount").value(50000))
            .andExpect(jsonPath("$.totalNetAmount").value(49000))
    }

    @Test
    fun `정산 배치 API는 서로 다른 통화 settlement를 하나의 총액으로 합산하지 않는다`() {
        prepareMixedCurrencyLedgerEntries()

        val exception = assertFailsWith<ServletException> {
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
        }

        assertEquals(
            "mixed settlement gross currencies cannot be summarized into a single total",
            exception.cause?.message,
        )
    }

    private fun prepareLedgerEntries() {
        val order = PaymentOrder(
            merchantId = "merchant-1",
            merchantOrderId = "order-settlement-api-001",
            totalAmount = Money(50_000L, CurrencyCode.KRW),
        )
        order.addLine(
            PaymentOrderLine(
                lineReference = "line-1",
                payeeId = "seller-A",
                lineAmount = Money(30_000L, CurrencyCode.KRW),
                quantity = 1,
            ),
        )
        order.addLine(
            PaymentOrderLine(
                lineReference = "line-2",
                payeeId = "seller-B",
                lineAmount = Money(20_000L, CurrencyCode.KRW),
                quantity = 1,
            ),
        )
        entityManager.persist(order)

        val sellerALine = order.lines.first { it.payeeId == "seller-A" }
        val sellerBLine = order.lines.first { it.payeeId == "seller-B" }

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
                paymentOrderLine = sellerALine,
                payeeId = "seller-A",
                type = LedgerEntryType.AUTH_CAPTURED,
                amount = Money(30_000L, CurrencyCode.KRW),
                occurredAt = OffsetDateTime.parse("2026-04-10T10:00:00+09:00"),
                referenceTransactionId = "settlement-auth-001-A",
                description = "settlement source auth seller A",
            ),
        )
        entityManager.persist(
            LedgerEntry(
                paymentOrder = order,
                paymentAllocation = allocation,
                authorization = authorization,
                paymentOrderLine = sellerBLine,
                payeeId = "seller-B",
                type = LedgerEntryType.AUTH_CAPTURED,
                amount = Money(20_000L, CurrencyCode.KRW),
                occurredAt = OffsetDateTime.parse("2026-04-10T10:01:00+09:00"),
                referenceTransactionId = "settlement-auth-001-B",
                description = "settlement source auth seller B",
            ),
        )
        entityManager.persist(
            LedgerEntry(
                paymentOrder = order,
                paymentAllocation = allocation,
                authorization = authorization,
                paymentOrderLine = sellerALine,
                payeeId = "seller-A",
                type = LedgerEntryType.FEE_BOOKED,
                amount = Money(1_000L, CurrencyCode.KRW),
                occurredAt = OffsetDateTime.parse("2026-04-10T10:05:00+09:00"),
                referenceTransactionId = "settlement-fee-001-A",
                description = "settlement source fee seller A",
            ),
        )

        entityManager.flush()
        entityManager.clear()
    }

    private fun prepareMixedCurrencyLedgerEntries() {
        prepareLedgerEntries()

        val usdOrder = PaymentOrder(
            merchantId = "merchant-2",
            merchantOrderId = "order-settlement-api-usd-001",
            totalAmount = Money(20L, CurrencyCode.USD),
        )
        val usdLine = PaymentOrderLine(
            lineReference = "usd-line-1",
            payeeId = "seller-C",
            lineAmount = Money(20L, CurrencyCode.USD),
            quantity = 1,
        )
        usdOrder.addLine(usdLine)
        entityManager.persist(usdOrder)

        val usdAllocation = PaymentAllocation(
            paymentOrder = usdOrder,
            payerReference = "payer-usd",
            allocationAmount = Money(20L, CurrencyCode.USD),
            sequence = 1,
        )
        entityManager.persist(usdAllocation)

        val usdAuthorization = Authorization(
            paymentAllocation = usdAllocation,
            instrumentType = InstrumentType.CARD,
            requestedAmount = Money(20L, CurrencyCode.USD),
            approvedAmount = Money(20L, CurrencyCode.USD),
            pgTransactionId = "settlement-auth-usd-001",
        )
        entityManager.persist(usdAuthorization)

        entityManager.persist(
            LedgerEntry(
                paymentOrder = usdOrder,
                paymentAllocation = usdAllocation,
                authorization = usdAuthorization,
                paymentOrderLine = usdLine,
                payeeId = "seller-C",
                type = LedgerEntryType.AUTH_CAPTURED,
                amount = Money(20L, CurrencyCode.USD),
                occurredAt = OffsetDateTime.parse("2026-04-10T11:00:00+09:00"),
                referenceTransactionId = "settlement-auth-usd-001-C",
                description = "settlement source auth seller C usd",
            ),
        )

        entityManager.flush()
        entityManager.clear()
    }
}
