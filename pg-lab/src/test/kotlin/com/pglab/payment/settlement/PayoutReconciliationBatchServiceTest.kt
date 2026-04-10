package com.pglab.payment.settlement

import com.pglab.payment.shared.CurrencyCode
import com.pglab.payment.shared.Money
import java.time.LocalDate
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class PayoutReconciliationBatchServiceTest {
    @Test
    fun `정합성 점검 배치는 upstream 성공이 확인되면 지급과 정산을 성공으로 확정한다`() {
        val settlement = settlement()
        val payout = payout(settlement)
        payout.markReconciling()

        val service = PayoutReconciliationBatchService(
            payoutReader = FakePayoutReader(listOf(payout)),
            upstreamReader = FakeUpstreamReader(
                mapOf(
                    payout.bankTransferRequestId to UpstreamPayoutCheckResult(
                        status = UpstreamPayoutStatus.SUCCEEDED,
                        bankTransferTransactionId = "bank-tx-001",
                        checkedAt = OffsetDateTime.parse("2026-04-10T10:00:00+09:00"),
                    ),
                ),
            ),
        )

        service.reconcile()

        assertEquals(PayoutStatus.SUCCEEDED, payout.status)
        assertEquals("bank-tx-001", payout.bankTransferTransactionId)
        assertEquals(SettlementStatus.PAID, settlement.status)
    }

    @Test
    fun `정합성 점검 배치는 여전히 모르는 건을 계속 정합성 점검 상태로 유지한다`() {
        val settlement = settlement()
        val payout = payout(settlement)
        payout.markReconciling()

        val service = PayoutReconciliationBatchService(
            payoutReader = FakePayoutReader(listOf(payout)),
            upstreamReader = FakeUpstreamReader(
                mapOf(
                    payout.bankTransferRequestId to UpstreamPayoutCheckResult(
                        status = UpstreamPayoutStatus.UNKNOWN,
                        checkedAt = OffsetDateTime.parse("2026-04-10T10:00:00+09:00"),
                    ),
                ),
            ),
        )

        service.reconcile()

        assertEquals(PayoutStatus.RECONCILING, payout.status)
        assertEquals(SettlementStatus.PROCESSING, settlement.status)
    }

    private class FakePayoutReader(
        private val payouts: List<Payout>,
    ) : ReconciliationPayoutReader {
        override fun findReconciliationTargets(): List<Payout> = payouts
    }

    private class FakeUpstreamReader(
        private val results: Map<String, UpstreamPayoutCheckResult>,
    ) : UpstreamPayoutReader {
        override fun check(bankTransferRequestId: String): UpstreamPayoutCheckResult =
            results.getValue(bankTransferRequestId)
    }

    private fun settlement() = Settlement(
        merchantId = "merchant-1",
        grossAmount = Money(100_000L, CurrencyCode.KRW),
        feeAmount = Money(3_000L, CurrencyCode.KRW),
        netAmount = Money(97_000L, CurrencyCode.KRW),
        status = SettlementStatus.PROCESSING,
        scheduledDate = LocalDate.of(2026, 4, 10),
    )

    private fun payout(settlement: Settlement) = Payout(
        settlement = settlement,
        requestedAmount = Money(97_000L, CurrencyCode.KRW),
        bankCode = "081",
        bankAccountNumber = "110-123-456789",
        accountHolderName = "상점A",
        bankTransferRequestId = "payout-req-001",
        requestedAt = OffsetDateTime.parse("2026-04-10T09:00:00+09:00"),
    )
}
