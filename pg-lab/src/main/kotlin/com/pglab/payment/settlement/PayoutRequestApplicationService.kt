package com.pglab.payment.settlement

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Service
class PayoutRequestApplicationService(
    private val settlementRepository: SettlementRepository,
    private val payoutRepository: PayoutRepository,
    private val payoutService: PayoutService,
) {
    @Transactional
    fun requestPayout(
        settlementId: Long,
        bankCode: String,
        bankAccountNumber: String,
        accountHolderName: String,
        bankTransferRequestId: String,
        requestedAt: OffsetDateTime,
    ): PayoutRequestResult {
        val settlement = settlementRepository.findById(settlementId)
            .orElseThrow { IllegalArgumentException("settlement not found") }
        val existingPayouts = payoutRepository.findAllBySettlementId(settlementId)

        val payout = payoutService.requestPayout(
            settlement = settlement,
            bankCode = bankCode,
            bankAccountNumber = bankAccountNumber,
            accountHolderName = accountHolderName,
            bankTransferRequestId = bankTransferRequestId,
            requestedAt = requestedAt,
            existingPayouts = existingPayouts,
        )

        settlementRepository.save(settlement)
        val savedPayout = payoutRepository.save(payout)

        return PayoutRequestResult(
            settlement = settlement,
            payout = savedPayout,
        )
    }
}

data class PayoutRequestResult(
    val settlement: Settlement,
    val payout: Payout,
)
