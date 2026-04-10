package com.pglab.payment.api

import com.pglab.payment.allocation.PaymentAllocation
import com.pglab.payment.authorization.Authorization
import com.pglab.payment.authorization.InstrumentType
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
            .andExpect(jsonPath("$.refundAmount").value(5000))
    }

    private fun prepareAuthorization(): Authorization {
        val order = PaymentOrder(
            merchantId = "merchant-1",
            merchantOrderId = "order-api-refund-001",
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
            requestedAmount = Money(10_000L, CurrencyCode.KRW),
            approvedAmount = Money(10_000L, CurrencyCode.KRW),
            pgTransactionId = "card-auth-api-refund-001",
            approvalCode = "card-appr-api-refund-001",
        )
        entityManager.persist(authorization)
        entityManager.flush()
        entityManager.clear()

        return authorization
    }
}
