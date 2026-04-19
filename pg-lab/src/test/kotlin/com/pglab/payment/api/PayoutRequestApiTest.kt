package com.pglab.payment.api

import com.pglab.payment.settlement.Settlement
import com.pglab.payment.settlement.SettlementStatus
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
import java.time.LocalDate
import java.time.OffsetDateTime

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PayoutRequestApiTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val entityManager: EntityManager,
) {
    @Test
    fun `지급 요청 API는 settlement를 처리중 상태로 바꾸고 payout를 생성한다`() {
        val settlement = prepareSettlement()

        mockMvc.perform(
            post("/api/settlements/${settlement.id}/payouts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "bankCode": "081",
                      "bankAccountNumber": "110-123-456789",
                      "accountHolderName": "상점A",
                      "bankTransferRequestId": "payout-api-001",
                      "requestedAt": "2026-04-10T09:00:00+09:00"
                    }
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.settlementStatus").value("PROCESSING"))
            .andExpect(jsonPath("$.payoutStatus").value("REQUESTED"))
            .andExpect(jsonPath("$.retryCount").value(0))
            .andExpect(jsonPath("$.requestedAmount").value(97000))
    }

    private fun prepareSettlement(): Settlement {
        val settlement = Settlement(
            payeeId = "payee-1",
            grossAmount = Money(100_000L, CurrencyCode.KRW),
            feeAmount = Money(3_000L, CurrencyCode.KRW),
            netAmount = Money(97_000L, CurrencyCode.KRW),
            status = SettlementStatus.SCHEDULED,
            scheduledDate = LocalDate.of(2026, 4, 10),
        )
        entityManager.persist(settlement)
        entityManager.flush()
        entityManager.clear()
        return settlement
    }
}
