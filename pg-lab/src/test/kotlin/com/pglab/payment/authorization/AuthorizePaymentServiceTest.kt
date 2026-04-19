package com.pglab.payment.authorization

import com.pglab.payment.allocation.PaymentAllocationStatus
import com.pglab.payment.ledger.LedgerEntryType
import com.pglab.payment.order.PaymentOrderStatus
import com.pglab.payment.shared.CurrencyCode
import com.pglab.payment.shared.Money
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AuthorizePaymentServiceTest {
    @Test
    fun `일반형 승인 서비스는 여러 allocation과 여러 authorization을 함께 처리한다`() {
        val writer = FakeAuthorizePaymentWriter()
        val service = AuthorizePaymentService(writer)

        val result = service.authorize(
            AuthorizePaymentCommand(
                merchantId = "merchant-1",
                merchantOrderId = "order-001",
                totalAmount = Money(50_000L, CurrencyCode.KRW),
                lines = listOf(
                    PaymentOrderLineRequest(
                        lineReference = "line-1",
                        payeeId = "seller-A",
                        lineAmount = Money(10_000L, CurrencyCode.KRW),
                        quantity = 1,
                    ),
                    PaymentOrderLineRequest(
                        lineReference = "line-2",
                        payeeId = "seller-B",
                        lineAmount = Money(10_000L, CurrencyCode.KRW),
                        quantity = 1,
                    ),
                    PaymentOrderLineRequest(
                        lineReference = "line-3",
                        payeeId = "seller-C",
                        lineAmount = Money(30_000L, CurrencyCode.KRW),
                        quantity = 1,
                    ),
                ),
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
                                linePortions = listOf(
                                    AuthorizationLinePortionRequest(
                                        lineReference = "line-1",
                                        amount = Money(10_000L, CurrencyCode.KRW),
                                    ),
                                ),
                            ),
                            AuthorizationRequest(
                                instrumentType = InstrumentType.BANK_ACCOUNT,
                                requestedAmount = Money(10_000L, CurrencyCode.KRW),
                                approvedAmount = Money(10_000L, CurrencyCode.KRW),
                                pgTransactionId = "bank-a-001",
                                linePortions = listOf(
                                    AuthorizationLinePortionRequest(
                                        lineReference = "line-2",
                                        amount = Money(10_000L, CurrencyCode.KRW),
                                    ),
                                ),
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
                                linePortions = listOf(
                                    AuthorizationLinePortionRequest(
                                        lineReference = "line-3",
                                        amount = Money(30_000L, CurrencyCode.KRW),
                                    ),
                                ),
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
        assertEquals(1, writer.savedResults.size)
    }

    @Test
    fun `일반형 승인 서비스는 주문 라인과 승인 라인 분배를 함께 생성한다`() {
        val writer = FakeAuthorizePaymentWriter()
        val service = AuthorizePaymentService(writer)

        val result = service.authorize(
            AuthorizePaymentCommand(
                merchantId = "merchant-1",
                merchantOrderId = "order-001-lines",
                totalAmount = Money(50_000L, CurrencyCode.KRW),
                lines = listOf(
                    PaymentOrderLineRequest(
                        lineReference = "line-1",
                        payeeId = "seller-A",
                        lineAmount = Money(20_000L, CurrencyCode.KRW),
                        quantity = 1,
                    ),
                    PaymentOrderLineRequest(
                        lineReference = "line-2",
                        payeeId = "seller-B",
                        lineAmount = Money(30_000L, CurrencyCode.KRW),
                        quantity = 1,
                    ),
                ),
                allocations = listOf(
                    AllocationAuthorizationRequest(
                        payerReference = "payer-A",
                        allocationAmount = Money(50_000L, CurrencyCode.KRW),
                        authorizations = listOf(
                            AuthorizationRequest(
                                instrumentType = InstrumentType.CARD,
                                requestedAmount = Money(50_000L, CurrencyCode.KRW),
                                approvedAmount = Money(50_000L, CurrencyCode.KRW),
                                pgTransactionId = "card-a-lines-001",
                                linePortions = listOf(
                                    AuthorizationLinePortionRequest(
                                        lineReference = "line-1",
                                        amount = Money(20_000L, CurrencyCode.KRW),
                                    ),
                                    AuthorizationLinePortionRequest(
                                        lineReference = "line-2",
                                        amount = Money(30_000L, CurrencyCode.KRW),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertEquals(2, result.orderLines.size)
        assertEquals(listOf("line-1", "line-2"), result.orderLines.map { it.lineReference })
        assertEquals(2, result.authorizationLinePortions.size)
        assertEquals(listOf("seller-A", "seller-B"), result.authorizationLinePortions.map { it.payeeId })
        assertEquals(listOf(20_000L, 30_000L), result.authorizations.single().linePortions.map { it.amount.amount })
        assertEquals(2, result.ledgerEntries.size)
        assertEquals(listOf("line-1", "line-2"), result.ledgerEntries.map { it.paymentOrderLine?.lineReference })
        assertEquals(listOf("seller-A", "seller-B"), result.ledgerEntries.map { it.payeeId })
        assertEquals(listOf(20_000L, 30_000L), result.ledgerEntries.map { it.amount.amount })
        assertEquals(result.authorizations.single(), result.ledgerEntries[0].authorization)
        assertEquals(result.authorizations.single(), result.ledgerEntries[1].authorization)
        assertEquals(1, writer.savedResults.size)
    }

    @Test
    fun `일반형 승인 서비스는 approvedAt이 있으면 seller 원장 발생 시각에 그대로 사용한다`() {
        val writer = FakeAuthorizePaymentWriter()
        val service = AuthorizePaymentService(writer)
        val approvedAt = OffsetDateTime.parse("2026-04-18T10:15:30+09:00")

        val result = service.authorize(
            AuthorizePaymentCommand(
                merchantId = "merchant-1",
                merchantOrderId = "order-approved-at",
                totalAmount = Money(50_000L, CurrencyCode.KRW),
                lines = listOf(
                    PaymentOrderLineRequest(
                        lineReference = "line-1",
                        payeeId = "seller-A",
                        lineAmount = Money(20_000L, CurrencyCode.KRW),
                        quantity = 1,
                    ),
                    PaymentOrderLineRequest(
                        lineReference = "line-2",
                        payeeId = "seller-B",
                        lineAmount = Money(30_000L, CurrencyCode.KRW),
                        quantity = 1,
                    ),
                ),
                allocations = listOf(
                    AllocationAuthorizationRequest(
                        payerReference = "payer-A",
                        allocationAmount = Money(50_000L, CurrencyCode.KRW),
                        authorizations = listOf(
                            AuthorizationRequest(
                                instrumentType = InstrumentType.CARD,
                                requestedAmount = Money(50_000L, CurrencyCode.KRW),
                                approvedAmount = Money(50_000L, CurrencyCode.KRW),
                                pgTransactionId = "card-approved-at-001",
                                approvedAt = approvedAt,
                                linePortions = listOf(
                                    AuthorizationLinePortionRequest(
                                        lineReference = "line-1",
                                        amount = Money(20_000L, CurrencyCode.KRW),
                                    ),
                                    AuthorizationLinePortionRequest(
                                        lineReference = "line-2",
                                        amount = Money(30_000L, CurrencyCode.KRW),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertEquals(listOf(approvedAt, approvedAt), result.ledgerEntries.map { it.occurredAt })
    }

    @Test
    fun `일반형 승인 서비스는 approvedAt이 없으면 seller 원장 발생 시각에 같은 fallback 시각을 사용한다`() {
        val writer = FakeAuthorizePaymentWriter()
        val service = AuthorizePaymentService(writer)
        val beforeAuthorize = OffsetDateTime.now().minusSeconds(1)

        val result = service.authorize(
            AuthorizePaymentCommand(
                merchantId = "merchant-1",
                merchantOrderId = "order-fallback-occurred-at",
                totalAmount = Money(50_000L, CurrencyCode.KRW),
                lines = listOf(
                    PaymentOrderLineRequest(
                        lineReference = "line-1",
                        payeeId = "seller-A",
                        lineAmount = Money(20_000L, CurrencyCode.KRW),
                        quantity = 1,
                    ),
                    PaymentOrderLineRequest(
                        lineReference = "line-2",
                        payeeId = "seller-B",
                        lineAmount = Money(30_000L, CurrencyCode.KRW),
                        quantity = 1,
                    ),
                ),
                allocations = listOf(
                    AllocationAuthorizationRequest(
                        payerReference = "payer-A",
                        allocationAmount = Money(50_000L, CurrencyCode.KRW),
                        authorizations = listOf(
                            AuthorizationRequest(
                                instrumentType = InstrumentType.CARD,
                                requestedAmount = Money(50_000L, CurrencyCode.KRW),
                                approvedAmount = Money(50_000L, CurrencyCode.KRW),
                                pgTransactionId = "card-fallback-occurred-at-001",
                                linePortions = listOf(
                                    AuthorizationLinePortionRequest(
                                        lineReference = "line-1",
                                        amount = Money(20_000L, CurrencyCode.KRW),
                                    ),
                                    AuthorizationLinePortionRequest(
                                        lineReference = "line-2",
                                        amount = Money(30_000L, CurrencyCode.KRW),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val afterAuthorize = OffsetDateTime.now().plusSeconds(1)

        val occurredAts = result.ledgerEntries.map { it.occurredAt }
        assertEquals(2, occurredAts.size)
        assertEquals(occurredAts.first(), occurredAts.last())
        assertTrue(occurredAts.first() >= beforeAuthorize)
        assertTrue(occurredAts.first() <= afterAuthorize)
    }

    @Test
    fun `allocation 합이 주문 총액과 다르면 실패한다`() {
        val service = AuthorizePaymentService(FakeAuthorizePaymentWriter())

        assertFailsWith<IllegalArgumentException> {
            service.authorize(
                AuthorizePaymentCommand(
                    merchantId = "merchant-1",
                    merchantOrderId = "order-002",
                    totalAmount = Money(50_000L, CurrencyCode.KRW),
                    lines = listOf(
                        PaymentOrderLineRequest(
                            lineReference = "line-1",
                            payeeId = "seller-A",
                            lineAmount = Money(50_000L, CurrencyCode.KRW),
                            quantity = 1,
                        ),
                    ),
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
                                    linePortions = listOf(
                                        AuthorizationLinePortionRequest(
                                            lineReference = "line-1",
                                            amount = Money(20_000L, CurrencyCode.KRW),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            )
        }
    }

    @Test
    fun `주문 라인 합이 주문 총액과 다르면 실패한다`() {
        val service = AuthorizePaymentService(FakeAuthorizePaymentWriter())

        val exception = assertFailsWith<IllegalArgumentException> {
            service.authorize(
                AuthorizePaymentCommand(
                    merchantId = "merchant-1",
                    merchantOrderId = "order-lines-mismatch",
                    totalAmount = Money(50_000L, CurrencyCode.KRW),
                    lines = listOf(
                        PaymentOrderLineRequest(
                            lineReference = "line-1",
                            payeeId = "seller-A",
                            lineAmount = Money(20_000L, CurrencyCode.KRW),
                            quantity = 1,
                        ),
                        PaymentOrderLineRequest(
                            lineReference = "line-2",
                            payeeId = "seller-B",
                            lineAmount = Money(20_000L, CurrencyCode.KRW),
                            quantity = 1,
                        ),
                    ),
                    allocations = listOf(
                        AllocationAuthorizationRequest(
                            payerReference = "payer-A",
                            allocationAmount = Money(50_000L, CurrencyCode.KRW),
                            authorizations = listOf(
                                AuthorizationRequest(
                                    instrumentType = InstrumentType.CARD,
                                    requestedAmount = Money(50_000L, CurrencyCode.KRW),
                                    approvedAmount = Money(50_000L, CurrencyCode.KRW),
                                    pgTransactionId = "card-lines-mismatch",
                                    linePortions = listOf(
                                        AuthorizationLinePortionRequest(
                                            lineReference = "line-1",
                                            amount = Money(20_000L, CurrencyCode.KRW),
                                        ),
                                        AuthorizationLinePortionRequest(
                                            lineReference = "line-2",
                                            amount = Money(30_000L, CurrencyCode.KRW),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            )
        }

        assertEquals("order line sum must match total amount", exception.message)
    }

    @Test
    fun `중복된 주문 lineReference가 있으면 실패한다`() {
        val service = AuthorizePaymentService(FakeAuthorizePaymentWriter())

        val exception = assertFailsWith<IllegalArgumentException> {
            service.authorize(
                AuthorizePaymentCommand(
                    merchantId = "merchant-1",
                    merchantOrderId = "order-duplicate-line-reference",
                    totalAmount = Money(50_000L, CurrencyCode.KRW),
                    lines = listOf(
                        PaymentOrderLineRequest(
                            lineReference = "line-1",
                            payeeId = "seller-A",
                            lineAmount = Money(20_000L, CurrencyCode.KRW),
                            quantity = 1,
                        ),
                        PaymentOrderLineRequest(
                            lineReference = "line-1",
                            payeeId = "seller-B",
                            lineAmount = Money(30_000L, CurrencyCode.KRW),
                            quantity = 1,
                        ),
                    ),
                    allocations = listOf(
                        AllocationAuthorizationRequest(
                            payerReference = "payer-A",
                            allocationAmount = Money(50_000L, CurrencyCode.KRW),
                            authorizations = listOf(
                                AuthorizationRequest(
                                    instrumentType = InstrumentType.CARD,
                                    requestedAmount = Money(50_000L, CurrencyCode.KRW),
                                    approvedAmount = Money(50_000L, CurrencyCode.KRW),
                                    pgTransactionId = "card-duplicate-line-reference",
                                    linePortions = listOf(
                                        AuthorizationLinePortionRequest(
                                            lineReference = "line-1",
                                            amount = Money(50_000L, CurrencyCode.KRW),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            )
        }

        assertEquals("order line references must be unique", exception.message)
    }

    @Test
    fun `allocation 내부 authorization 합이 allocation 금액과 다르면 실패한다`() {
        val service = AuthorizePaymentService(FakeAuthorizePaymentWriter())

        assertFailsWith<IllegalArgumentException> {
            service.authorize(
                AuthorizePaymentCommand(
                    merchantId = "merchant-1",
                    merchantOrderId = "order-003",
                    totalAmount = Money(50_000L, CurrencyCode.KRW),
                    lines = listOf(
                        PaymentOrderLineRequest(
                            lineReference = "line-1",
                            payeeId = "seller-A",
                            lineAmount = Money(20_000L, CurrencyCode.KRW),
                            quantity = 1,
                        ),
                        PaymentOrderLineRequest(
                            lineReference = "line-2",
                            payeeId = "seller-B",
                            lineAmount = Money(30_000L, CurrencyCode.KRW),
                            quantity = 1,
                        ),
                    ),
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
                                    linePortions = listOf(
                                        AuthorizationLinePortionRequest(
                                            lineReference = "line-1",
                                            amount = Money(10_000L, CurrencyCode.KRW),
                                        ),
                                    ),
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
                                    linePortions = listOf(
                                        AuthorizationLinePortionRequest(
                                            lineReference = "line-2",
                                            amount = Money(30_000L, CurrencyCode.KRW),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            )
        }
    }

    @Test
    fun `승인 라인 분배가 존재하지 않는 주문 라인을 참조하면 실패한다`() {
        val service = AuthorizePaymentService(FakeAuthorizePaymentWriter())

        val exception = assertFailsWith<IllegalArgumentException> {
            service.authorize(
                AuthorizePaymentCommand(
                    merchantId = "merchant-1",
                    merchantOrderId = "order-004",
                    totalAmount = Money(50_000L, CurrencyCode.KRW),
                    lines = listOf(
                        PaymentOrderLineRequest(
                            lineReference = "line-1",
                            payeeId = "seller-A",
                            lineAmount = Money(50_000L, CurrencyCode.KRW),
                            quantity = 1,
                        ),
                    ),
                    allocations = listOf(
                        AllocationAuthorizationRequest(
                            payerReference = "payer-A",
                            allocationAmount = Money(50_000L, CurrencyCode.KRW),
                            authorizations = listOf(
                                AuthorizationRequest(
                                    instrumentType = InstrumentType.CARD,
                                    requestedAmount = Money(50_000L, CurrencyCode.KRW),
                                    approvedAmount = Money(50_000L, CurrencyCode.KRW),
                                    pgTransactionId = "card-a-004",
                                    linePortions = listOf(
                                        AuthorizationLinePortionRequest(
                                            lineReference = "line-unknown",
                                            amount = Money(50_000L, CurrencyCode.KRW),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            )
        }

        assertEquals("unknown order line reference: line-unknown", exception.message)
    }

    @Test
    fun `같은 주문 라인에 대한 승인 라인 분배 누적합이 주문 라인 금액을 초과하면 실패한다`() {
        val service = AuthorizePaymentService(FakeAuthorizePaymentWriter())

        val exception = assertFailsWith<IllegalArgumentException> {
            service.authorize(
                AuthorizePaymentCommand(
                    merchantId = "merchant-1",
                    merchantOrderId = "order-line-over-allocation",
                    totalAmount = Money(50_000L, CurrencyCode.KRW),
                    lines = listOf(
                        PaymentOrderLineRequest(
                            lineReference = "line-1",
                            payeeId = "seller-A",
                            lineAmount = Money(20_000L, CurrencyCode.KRW),
                            quantity = 1,
                        ),
                        PaymentOrderLineRequest(
                            lineReference = "line-2",
                            payeeId = "seller-B",
                            lineAmount = Money(30_000L, CurrencyCode.KRW),
                            quantity = 1,
                        ),
                    ),
                    allocations = listOf(
                        AllocationAuthorizationRequest(
                            payerReference = "payer-A",
                            allocationAmount = Money(25_000L, CurrencyCode.KRW),
                            authorizations = listOf(
                                AuthorizationRequest(
                                    instrumentType = InstrumentType.CARD,
                                    requestedAmount = Money(25_000L, CurrencyCode.KRW),
                                    approvedAmount = Money(25_000L, CurrencyCode.KRW),
                                    pgTransactionId = "card-line-over-allocation-1",
                                    linePortions = listOf(
                                        AuthorizationLinePortionRequest(
                                            lineReference = "line-1",
                                            amount = Money(15_000L, CurrencyCode.KRW),
                                        ),
                                        AuthorizationLinePortionRequest(
                                            lineReference = "line-2",
                                            amount = Money(10_000L, CurrencyCode.KRW),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                        AllocationAuthorizationRequest(
                            payerReference = "payer-B",
                            allocationAmount = Money(25_000L, CurrencyCode.KRW),
                            authorizations = listOf(
                                AuthorizationRequest(
                                    instrumentType = InstrumentType.BANK_ACCOUNT,
                                    requestedAmount = Money(25_000L, CurrencyCode.KRW),
                                    approvedAmount = Money(25_000L, CurrencyCode.KRW),
                                    pgTransactionId = "bank-line-over-allocation-1",
                                    linePortions = listOf(
                                        AuthorizationLinePortionRequest(
                                            lineReference = "line-1",
                                            amount = Money(10_001L, CurrencyCode.KRW),
                                        ),
                                        AuthorizationLinePortionRequest(
                                            lineReference = "line-2",
                                            amount = Money(14_999L, CurrencyCode.KRW),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            )
        }

        assertEquals("authorization line portions for line-1 must not exceed order line amount", exception.message)
    }

    private class FakeAuthorizePaymentWriter : AuthorizePaymentWriter {
        val savedResults = mutableListOf<AuthorizePaymentResult>()

        override fun save(result: AuthorizePaymentResult): AuthorizePaymentResult {
            savedResults.add(result)
            return result
        }
    }
}
