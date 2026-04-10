package com.pglab.payment.api

import com.pglab.payment.settlement.Payout
import com.pglab.payment.settlement.PayoutStatus
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
class PayoutReconciliationApiTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val entityManager: EntityManager,
) {
    @Test
    fun `정합성 점검 API는 reconciling payout를 재확인하고 요약 응답을 반환한다`() {
        prepareReconcilingPayout()

        mockMvc.perform(
            post("/api/payouts/reconciliation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.processedCount").value(1))
            .andExpect(jsonPath("$.succeededCount").value(1))
            .andExpect(jsonPath("$.failedCount").value(0))
            .andExpect(jsonPath("$.stillReconcilingCount").value(0))
    }

    private fun prepareReconcilingPayout() {
        val settlement = Settlement(
            merchantId = "merchant-1",
            grossAmount = Money(100_000L, CurrencyCode.KRW),
            feeAmount = Money(3_000L, CurrencyCode.KRW),
            netAmount = Money(97_000L, CurrencyCode.KRW),
            status = SettlementStatus.PROCESSING,
            scheduledDate = LocalDate.of(2026, 4, 10),
        )
        entityManager.persist(settlement)

        val payout = Payout(
            settlement = settlement,
            requestedAmount = Money(97_000L, CurrencyCode.KRW),
            bankCode = "081",
            bankAccountNumber = "110-123-456789",
            accountHolderName = "상점A",
            bankTransferRequestId = "payout-req-001",
            requestedAt = OffsetDateTime.parse("2026-04-10T09:00:00+09:00"),
            status = PayoutStatus.RECONCILING,
            reconcilingSince = OffsetDateTime.parse("2026-04-10T09:30:00+09:00"),
        )
        entityManager.persist(payout)

        entityManager.flush()
        entityManager.clear()
    }
}
