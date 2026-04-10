package com.pglab.payment.settlement

import com.pglab.payment.shared.CurrencyCode
import com.pglab.payment.shared.Money
import java.time.LocalDate
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class PayoutServiceTest {
    @Test
    fun `지급 요청을 시작하면 정산은 처리중 상태가 되고 지급 시도가 생성된다`() {
        val settlement = Settlement(
            merchantId = "merchant-1",
            grossAmount = Money(100_000L, CurrencyCode.KRW),
            feeAmount = Money(3_000L, CurrencyCode.KRW),
            netAmount = Money(97_000L, CurrencyCode.KRW),
            status = SettlementStatus.SCHEDULED,
            scheduledDate = LocalDate.of(2026, 4, 10),
        )
        val service = PayoutService()

        val payout = service.requestPayout(
            settlement = settlement,
            bankCode = "081",
            bankAccountNumber = "110-123-456789",
            accountHolderName = "상점A",
            bankTransferRequestId = "payout-req-001",
            requestedAt = OffsetDateTime.parse("2026-04-10T09:00:00+09:00"),
        )

        assertEquals(SettlementStatus.PROCESSING, settlement.status)
        assertEquals(PayoutStatus.REQUESTED, payout.status)
        assertEquals(97_000L, payout.requestedAmount.amount)
    }

    @Test
    fun `지급 전송이 완료되면 지급 시도는 전송 상태와 전송 시각을 가진다`() {
        val service = PayoutService()
        val payout = service.requestPayout(
            settlement = settlement(),
            bankCode = "081",
            bankAccountNumber = "110-123-456789",
            accountHolderName = "상점A",
            bankTransferRequestId = "payout-req-001",
            requestedAt = OffsetDateTime.parse("2026-04-10T09:00:00+09:00"),
        )
        val sentAt = OffsetDateTime.parse("2026-04-10T09:01:00+09:00")

        service.markSent(payout, sentAt)

        assertEquals(PayoutStatus.SENT, payout.status)
        assertEquals(sentAt, payout.sentAt)
    }

    @Test
    fun `지급 성공이 기록되면 정산은 완료 상태가 된다`() {
        val settlement = settlement()
        val service = PayoutService()
        val payout = service.requestPayout(
            settlement = settlement,
            bankCode = "081",
            bankAccountNumber = "110-123-456789",
            accountHolderName = "상점A",
            bankTransferRequestId = "payout-req-001",
            requestedAt = OffsetDateTime.parse("2026-04-10T09:00:00+09:00"),
        )
        val completedAt = OffsetDateTime.parse("2026-04-10T09:03:00+09:00")

        service.markSucceeded(payout, "bank-tx-001", completedAt)

        assertEquals(PayoutStatus.SUCCEEDED, payout.status)
        assertEquals("bank-tx-001", payout.bankTransferTransactionId)
        assertEquals(completedAt, payout.completedAt)
        assertEquals(SettlementStatus.PAID, settlement.status)
        assertEquals(completedAt, settlement.settledAt)
    }

    @Test
    fun `지급 실패가 기록되면 정산은 실패 상태가 된다`() {
        val settlement = settlement()
        val service = PayoutService()
        val payout = service.requestPayout(
            settlement = settlement,
            bankCode = "081",
            bankAccountNumber = "110-123-456789",
            accountHolderName = "상점A",
            bankTransferRequestId = "payout-req-001",
            requestedAt = OffsetDateTime.parse("2026-04-10T09:00:00+09:00"),
        )
        val failedAt = OffsetDateTime.parse("2026-04-10T09:05:00+09:00")

        service.markFailed(payout, "BANK_TIMEOUT", "은행 응답 지연", failedAt)

        assertEquals(PayoutStatus.FAILED, payout.status)
        assertEquals("BANK_TIMEOUT", payout.failureCode)
        assertEquals("은행 응답 지연", payout.failureReason)
        assertEquals(failedAt, payout.completedAt)
        assertEquals(SettlementStatus.FAILED, settlement.status)
    }

    private fun settlement() = Settlement(
        merchantId = "merchant-1",
        grossAmount = Money(100_000L, CurrencyCode.KRW),
        feeAmount = Money(3_000L, CurrencyCode.KRW),
        netAmount = Money(97_000L, CurrencyCode.KRW),
        status = SettlementStatus.SCHEDULED,
        scheduledDate = LocalDate.of(2026, 4, 10),
    )
}
