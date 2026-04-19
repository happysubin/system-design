package com.pglab.payment.api

import com.pglab.payment.settlement.SettlementBatchService
import com.pglab.payment.shared.CurrencyCode
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/api/settlements")
class SettlementBatchController(
    private val settlementBatchService: SettlementBatchService,
) {
    @PostMapping("/batch")
    fun createBatch(@RequestBody request: SettlementBatchApiRequest): SettlementBatchApiResponse {
        val settlements = settlementBatchService.createScheduledSettlements(LocalDate.parse(request.targetDate))

        return SettlementBatchApiResponse(
            targetDate = request.targetDate,
            settlementCount = settlements.size,
            payeeCount = settlements.map { it.payeeId }.distinct().size,
            totalGrossAmount = summarizeSingleCurrencyAmount(
                settlements.map { it.grossAmount.currency }.distinct(),
                settlements.map { it.grossAmount.amount },
                "mixed settlement gross currencies cannot be summarized into a single total",
            ),
            totalNetAmount = summarizeSingleCurrencyAmount(
                settlements.map { it.netAmount.currency }.distinct(),
                settlements.map { it.netAmount.amount },
                "mixed settlement net currencies cannot be summarized into a single total",
            ),
        )
    }

    private fun summarizeSingleCurrencyAmount(
        currencies: List<CurrencyCode>,
        amounts: List<Long>,
        errorMessage: String,
    ): Long {
        require(currencies.size <= 1) {
            errorMessage
        }
        return amounts.sum()
    }
}

data class SettlementBatchApiRequest(
    val targetDate: String,
)

data class SettlementBatchApiResponse(
    val targetDate: String,
    val settlementCount: Int,
    val payeeCount: Int,
    val totalGrossAmount: Long,
    val totalNetAmount: Long,
)
