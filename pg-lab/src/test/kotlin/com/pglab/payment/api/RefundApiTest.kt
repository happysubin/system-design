package com.pglab.payment.api

import com.pglab.payment.allocation.PaymentAllocation
import com.pglab.payment.authorization.Authorization
import com.pglab.payment.authorization.AuthorizationLinePortion
import com.pglab.payment.authorization.InstrumentType
import com.pglab.payment.ledger.LedgerEntry
import com.pglab.payment.ledger.LedgerEntryType
import com.pglab.payment.order.PaymentOrder
import com.pglab.payment.order.PaymentOrderLine
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

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RefundApiTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val entityManager: EntityManager,
) {
    @Test
    fun `환불 API는 대상 승인에 환불을 반영하고 요약 응답을 반환한다`() {
        val authorization = prepareAuthorization()

        mockMvc.perform(
            post("/api/payments/authorizations/${authorization.id}/refund")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "refundAmount": 5000,
                      "currency": "KRW"
                    }
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.orderStatus").value("PARTIALLY_CANCELED"))
            .andExpect(jsonPath("$.allocationStatus").value("PARTIALLY_CANCELED"))
            .andExpect(jsonPath("$.ledgerEntryType").value("REFUNDED"))
            .andExpect(jsonPath("$.ledgerEntryCount").value(2))
            .andExpect(jsonPath("$.refundAmount").value(5000))
    }

    @Test
    fun `환불 API는 기존 취소 이력으로 소진된 seller에 zero ledger를 만들지 않는다`() {
        val authorization = prepareAuthorization(amountA = 6L, amountB = 4L)
        val allocation = requireNotNull(authorization.paymentAllocation)
        val order = requireNotNull(allocation.paymentOrder)
        entityManager.persist(
            LedgerEntry(
                paymentOrder = order,
                paymentAllocation = allocation,
                authorization = authorization,
                paymentOrderLine = order.lines.first { it.lineReference == "line-2" },
                payeeId = "seller-B",
                type = LedgerEntryType.CANCELLED,
                amount = Money(4L, CurrencyCode.KRW),
                referenceTransactionId = authorization.pgTransactionId,
                description = "existing cancellation",
            ),
        )
        entityManager.flush()
        entityManager.clear()

        mockMvc.perform(
            post("/api/payments/authorizations/${authorization.id}/refund")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "refundAmount": 1,
                      "currency": "KRW"
                    }
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.ledgerEntryType").value("REFUNDED"))
            .andExpect(jsonPath("$.ledgerEntryCount").value(1))
            .andExpect(jsonPath("$.refundAmount").value(1))
    }

    @Test
    fun `환불 API는 멀티 allocation 주문에서 주문 상태를 전체 승인 기준으로 다시 계산한다`() {
        val targetAuthorization = prepareMultiAllocationAuthorization(pgTransactionId = "card-auth-api-multi-refund-1")

        mockMvc.perform(
            post("/api/payments/authorizations/${targetAuthorization.id}/refund")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "refundAmount": 50000,
                      "currency": "KRW"
                    }
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.orderStatus").value("PARTIALLY_CANCELED"))
            .andExpect(jsonPath("$.allocationStatus").value("CANCELED"))
            .andExpect(jsonPath("$.ledgerEntryCount").value(1))
            .andExpect(jsonPath("$.refundAmount").value(50000))
    }

    private fun prepareAuthorization(amountA: Long = 6_000L, amountB: Long = 4_000L): Authorization {
        val order = PaymentOrder(
            merchantId = "merchant-1",
            merchantOrderId = "order-api-refund-001",
            totalAmount = Money(40_000L + amountA + amountB, CurrencyCode.KRW),
        )
        val sellerALine = PaymentOrderLine(
            lineReference = "line-1",
            payeeId = "seller-A",
            lineAmount = Money(24_000L + amountA, CurrencyCode.KRW),
            quantity = 1,
        )
        val sellerBLine = PaymentOrderLine(
            lineReference = "line-2",
            payeeId = "seller-B",
            lineAmount = Money(16_000L + amountB, CurrencyCode.KRW),
            quantity = 1,
        )
        order.addLine(sellerALine)
        order.addLine(sellerBLine)
        entityManager.persist(order)

        val allocation = PaymentAllocation(
            paymentOrder = order,
            payerReference = "payer-A",
            allocationAmount = Money(40_000L + amountA + amountB, CurrencyCode.KRW),
            sequence = 1,
        )
        entityManager.persist(allocation)

        val authorization = Authorization(
            paymentAllocation = allocation,
            instrumentType = InstrumentType.CARD,
            requestedAmount = Money(amountA + amountB, CurrencyCode.KRW),
            approvedAmount = Money(amountA + amountB, CurrencyCode.KRW),
            pgTransactionId = "card-auth-api-refund-001",
            approvalCode = "card-appr-api-refund-001",
        )
        authorization.addLinePortion(
            AuthorizationLinePortion(
                paymentOrderLine = sellerALine,
                payeeId = "seller-A",
                amount = Money(amountA, CurrencyCode.KRW),
                sequence = 1,
            ),
        )
        authorization.addLinePortion(
            AuthorizationLinePortion(
                paymentOrderLine = sellerBLine,
                payeeId = "seller-B",
                amount = Money(amountB, CurrencyCode.KRW),
                sequence = 2,
            ),
        )
        entityManager.persist(authorization)
        entityManager.flush()
        entityManager.clear()

        return authorization
    }

    private fun prepareMultiAllocationAuthorization(pgTransactionId: String): Authorization {
        val order = PaymentOrder(
            merchantId = "merchant-1",
            merchantOrderId = "order-api-refund-multi-allocation",
            totalAmount = Money(100_000L, CurrencyCode.KRW),
        )
        val sellerALine = PaymentOrderLine(
            lineReference = "line-1",
            payeeId = "seller-A",
            lineAmount = Money(50_000L, CurrencyCode.KRW),
            quantity = 1,
        )
        val sellerBLine = PaymentOrderLine(
            lineReference = "line-2",
            payeeId = "seller-B",
            lineAmount = Money(50_000L, CurrencyCode.KRW),
            quantity = 1,
        )
        order.addLine(sellerALine)
        order.addLine(sellerBLine)
        entityManager.persist(order)

        val firstAllocation = PaymentAllocation(
            paymentOrder = order,
            payerReference = "payer-A",
            allocationAmount = Money(50_000L, CurrencyCode.KRW),
            sequence = 1,
        )
        val secondAllocation = PaymentAllocation(
            paymentOrder = order,
            payerReference = "payer-B",
            allocationAmount = Money(50_000L, CurrencyCode.KRW),
            sequence = 2,
        )
        entityManager.persist(firstAllocation)
        entityManager.persist(secondAllocation)

        val firstAuthorization = Authorization(
            paymentAllocation = firstAllocation,
            instrumentType = InstrumentType.CARD,
            requestedAmount = Money(50_000L, CurrencyCode.KRW),
            approvedAmount = Money(50_000L, CurrencyCode.KRW),
            pgTransactionId = pgTransactionId,
            approvalCode = "card-appr-api-multi-refund-1",
        )
        firstAuthorization.addLinePortion(
            AuthorizationLinePortion(
                paymentOrderLine = sellerALine,
                payeeId = "seller-A",
                amount = Money(50_000L, CurrencyCode.KRW),
                sequence = 1,
            ),
        )
        entityManager.persist(firstAuthorization)

        val secondAuthorization = Authorization(
            paymentAllocation = secondAllocation,
            instrumentType = InstrumentType.BANK_ACCOUNT,
            requestedAmount = Money(50_000L, CurrencyCode.KRW),
            approvedAmount = Money(50_000L, CurrencyCode.KRW),
            pgTransactionId = "bank-auth-api-multi-refund-2",
            approvalCode = "bank-appr-api-multi-refund-2",
        )
        secondAuthorization.addLinePortion(
            AuthorizationLinePortion(
                paymentOrderLine = sellerBLine,
                payeeId = "seller-B",
                amount = Money(50_000L, CurrencyCode.KRW),
                sequence = 1,
            ),
        )
        entityManager.persist(secondAuthorization)

        entityManager.flush()
        entityManager.clear()

        return firstAuthorization
    }
}
