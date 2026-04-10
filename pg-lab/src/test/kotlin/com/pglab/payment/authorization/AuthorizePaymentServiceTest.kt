package com.pglab.payment.authorization

import com.pglab.payment.allocation.PaymentAllocationStatus
import com.pglab.payment.ledger.LedgerEntryType
import com.pglab.payment.order.PaymentOrderStatus
import com.pglab.payment.shared.CurrencyCode
import com.pglab.payment.shared.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AuthorizePaymentServiceTest {
    @Test
    fun `일반형 승인 서비스는 여러 allocation과 여러 authorization을 함께 처리한다`() {
        val service = AuthorizePaymentService()

        val result = service.authorize(
            AuthorizePaymentCommand(
                merchantId = "merchant-1",
                merchantOrderId = "order-001",
                totalAmount = Money(50_000L, CurrencyCode.KRW),
                allocations = listOf(
                    AllocationAuthorizationRequest(
                        payerReference = "payer-A",
                        allocationAmount = Money(20_000L, CurrencyCode.KRW),
                        authorizations = listOf(
                            AuthorizationRequest(
                                instrumentType = InstrumentType.CARD,
                                requestedAmount = Money(10_000L, CurrencyCode.KRW),
                                approvedAmount = Money(10_000L, CurrencyCode.KRW),
                                pgTransactionId = "card-a-001",
                            ),
                            AuthorizationRequest(
                                instrumentType = InstrumentType.BANK_ACCOUNT,
                                requestedAmount = Money(10_000L, CurrencyCode.KRW),
                                approvedAmount = Money(10_000L, CurrencyCode.KRW),
                                pgTransactionId = "bank-a-001",
                            ),
                        ),
                    ),
                    AllocationAuthorizationRequest(
                        payerReference = "payer-B",
                        allocationAmount = Money(30_000L, CurrencyCode.KRW),
                        authorizations = listOf(
                            AuthorizationRequest(
                                instrumentType = InstrumentType.CARD,
                                requestedAmount = Money(30_000L, CurrencyCode.KRW),
                                approvedAmount = Money(30_000L, CurrencyCode.KRW),
                                pgTransactionId = "card-b-001",
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertEquals(PaymentOrderStatus.AUTHORIZED, result.order.status)
        assertEquals(2, result.allocations.size)
        assertEquals(listOf(PaymentAllocationStatus.AUTHORIZED, PaymentAllocationStatus.AUTHORIZED), result.allocations.map { it.status })
        assertEquals(3, result.authorizations.size)
        assertEquals(3, result.ledgerEntries.size)
        assertEquals(setOf(LedgerEntryType.AUTH_CAPTURED), result.ledgerEntries.map { it.type }.toSet())
    }

    @Test
    fun `allocation 합이 주문 총액과 다르면 실패한다`() {
        val service = AuthorizePaymentService()

        assertFailsWith<IllegalArgumentException> {
            service.authorize(
                AuthorizePaymentCommand(
                    merchantId = "merchant-1",
                    merchantOrderId = "order-002",
                    totalAmount = Money(50_000L, CurrencyCode.KRW),
                    allocations = listOf(
                        AllocationAuthorizationRequest(
                            payerReference = "payer-A",
                            allocationAmount = Money(20_000L, CurrencyCode.KRW),
                            authorizations = listOf(
                                AuthorizationRequest(
                                    instrumentType = InstrumentType.CARD,
                                    requestedAmount = Money(20_000L, CurrencyCode.KRW),
                                    approvedAmount = Money(20_000L, CurrencyCode.KRW),
                                    pgTransactionId = "card-a-002",
                                ),
                            ),
                        ),
                    ),
                ),
            )
        }
    }

    @Test
    fun `allocation 내부 authorization 합이 allocation 금액과 다르면 실패한다`() {
        val service = AuthorizePaymentService()

        assertFailsWith<IllegalArgumentException> {
            service.authorize(
                AuthorizePaymentCommand(
                    merchantId = "merchant-1",
                    merchantOrderId = "order-003",
                    totalAmount = Money(50_000L, CurrencyCode.KRW),
                    allocations = listOf(
                        AllocationAuthorizationRequest(
                            payerReference = "payer-A",
                            allocationAmount = Money(20_000L, CurrencyCode.KRW),
                            authorizations = listOf(
                                AuthorizationRequest(
                                    instrumentType = InstrumentType.CARD,
                                    requestedAmount = Money(10_000L, CurrencyCode.KRW),
                                    approvedAmount = Money(10_000L, CurrencyCode.KRW),
                                    pgTransactionId = "card-a-003",
                                ),
                            ),
                        ),
                        AllocationAuthorizationRequest(
                            payerReference = "payer-B",
                            allocationAmount = Money(30_000L, CurrencyCode.KRW),
                            authorizations = listOf(
                                AuthorizationRequest(
                                    instrumentType = InstrumentType.CARD,
                                    requestedAmount = Money(30_000L, CurrencyCode.KRW),
                                    approvedAmount = Money(30_000L, CurrencyCode.KRW),
                                    pgTransactionId = "card-b-003",
                                ),
                            ),
                        ),
                    ),
                ),
            )
        }
    }
}
