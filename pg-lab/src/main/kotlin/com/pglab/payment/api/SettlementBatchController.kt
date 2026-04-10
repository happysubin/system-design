package com.pglab.payment.api

import com.pglab.payment.settlement.SettlementBatchService
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
            merchantCount = settlements.map { it.merchantId }.distinct().size,
            totalGrossAmount = settlements.sumOf { it.grossAmount.amount },
            totalNetAmount = settlements.sumOf { it.netAmount.amount },
        )
    }
}

data class SettlementBatchApiRequest(
    val targetDate: String,
)

data class SettlementBatchApiResponse(
    val targetDate: String,
    val settlementCount: Int,
    val merchantCount: Int,
    val totalGrossAmount: Long,
    val totalNetAmount: Long,
)
