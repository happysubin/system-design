package com.pglab.payment.authorization

import com.pglab.payment.allocation.PaymentAllocationStatus
import com.pglab.payment.ledger.LedgerEntryType
import com.pglab.payment.order.PaymentOrderStatus
import com.pglab.payment.shared.CurrencyCode
import com.pglab.payment.shared.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MixedPaymentAuthorizationServiceTest {
    @Test
    fun `혼합결제 승인 성공 시 주문 부담단위 승인 원장이 함께 생성된다`() {
        val service = MixedPaymentAuthorizationService()

        val result = service.authorize(
            MixedPaymentAuthorizationCommand(
                merchantId = "merchant-1",
                merchantOrderId = "order-001",
                payerReference = "payer-A",
                totalAmount = Money(50_000L, CurrencyCode.KRW),
                authorizationRequests = listOf(
                    MixedAuthorizationRequest(
                        instrumentType = InstrumentType.BANK_ACCOUNT,
                        requestedAmount = Money(40_000L, CurrencyCode.KRW),
                        approvedAmount = Money(40_000L, CurrencyCode.KRW),
                        pgTransactionId = "bank-tx-001",
                    ),
                    MixedAuthorizationRequest(
                        instrumentType = InstrumentType.CARD,
                        requestedAmount = Money(10_000L, CurrencyCode.KRW),
                        approvedAmount = Money(10_000L, CurrencyCode.KRW),
                        pgTransactionId = "card-tx-001",
                        approvalCode = "card-appr-001",
                    ),
                ),
            ),
        )

        assertEquals(PaymentOrderStatus.AUTHORIZED, result.order.status)
        assertEquals(PaymentAllocationStatus.AUTHORIZED, result.allocation.status)
        assertEquals(2, result.authorizations.size)
        assertEquals(2, result.ledgerEntries.size)
        assertEquals(setOf(InstrumentType.BANK_ACCOUNT, InstrumentType.CARD), result.authorizations.map { it.instrumentType }.toSet())
        assertEquals(setOf(LedgerEntryType.AUTH_CAPTURED), result.ledgerEntries.map { it.type }.toSet())
    }

    @Test
    fun `수단별 요청 금액 합이 총 결제금액과 다르면 실패한다`() {
        val service = MixedPaymentAuthorizationService()

        assertFailsWith<IllegalArgumentException> {
            service.authorize(
                MixedPaymentAuthorizationCommand(
                    merchantId = "merchant-1",
                    merchantOrderId = "order-002",
                    payerReference = "payer-A",
                    totalAmount = Money(50_000L, CurrencyCode.KRW),
                    authorizationRequests = listOf(
                        MixedAuthorizationRequest(
                            instrumentType = InstrumentType.BANK_ACCOUNT,
                            requestedAmount = Money(30_000L, CurrencyCode.KRW),
                            approvedAmount = Money(30_000L, CurrencyCode.KRW),
                            pgTransactionId = "bank-tx-002",
                        ),
                        MixedAuthorizationRequest(
                            instrumentType = InstrumentType.CARD,
                            requestedAmount = Money(10_000L, CurrencyCode.KRW),
                            approvedAmount = Money(10_000L, CurrencyCode.KRW),
                            pgTransactionId = "card-tx-002",
                        ),
                    ),
                ),
            )
        }
    }

    @Test
    fun `수단별 승인 합이 총 결제금액보다 작으면 부분승인 상태가 된다`() {
        val service = MixedPaymentAuthorizationService()

        val result = service.authorize(
            MixedPaymentAuthorizationCommand(
                merchantId = "merchant-1",
                merchantOrderId = "order-003",
                payerReference = "payer-A",
                totalAmount = Money(50_000L, CurrencyCode.KRW),
                authorizationRequests = listOf(
                    MixedAuthorizationRequest(
                        instrumentType = InstrumentType.BANK_ACCOUNT,
                        requestedAmount = Money(40_000L, CurrencyCode.KRW),
                        approvedAmount = Money(40_000L, CurrencyCode.KRW),
                        pgTransactionId = "bank-tx-003",
                    ),
                    MixedAuthorizationRequest(
                        instrumentType = InstrumentType.CARD,
                        requestedAmount = Money(10_000L, CurrencyCode.KRW),
                        approvedAmount = Money(5_000L, CurrencyCode.KRW),
                        pgTransactionId = "card-tx-003",
                    ),
                ),
            ),
        )

        assertEquals(PaymentOrderStatus.PARTIALLY_AUTHORIZED, result.order.status)
        assertEquals(PaymentAllocationStatus.PARTIALLY_AUTHORIZED, result.allocation.status)
        assertEquals(2, result.authorizations.size)
        assertEquals(2, result.ledgerEntries.size)
        assertEquals(45_000L, result.ledgerEntries.sumOf { it.amount.amount })
    }
}
