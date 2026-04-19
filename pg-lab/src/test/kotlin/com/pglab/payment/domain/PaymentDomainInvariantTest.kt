package com.pglab.payment.domain

import com.pglab.payment.allocation.PaymentAllocation
import com.pglab.payment.authorization.Authorization
import com.pglab.payment.authorization.AuthorizationLinePortion
import com.pglab.payment.authorization.AuthorizationLinePortionRequest
import com.pglab.payment.authorization.AuthorizationRequest
import com.pglab.payment.authorization.AuthorizePaymentCommand
import com.pglab.payment.authorization.AuthorizePaymentService
import com.pglab.payment.authorization.AuthorizePaymentWriter
import com.pglab.payment.authorization.InstrumentType
import com.pglab.payment.authorization.PaymentOrderLineRequest
import com.pglab.payment.authorization.AllocationAuthorizationRequest
import com.pglab.payment.authorization.AuthorizePaymentResult
import com.pglab.payment.order.PaymentOrder
import com.pglab.payment.order.PaymentOrderLine
import com.pglab.payment.shared.CurrencyCode
import com.pglab.payment.shared.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PaymentDomainInvariantTest {
    @Test
    fun `부분 취소는 남은 취소 가능 금액을 감소시킨다`() {
        val authorization = Authorization(
            instrumentType = InstrumentType.CARD,
            requestedAmount = Money(30_000L, CurrencyCode.KRW),
            approvedAmount = Money(30_000L, CurrencyCode.KRW),
            pgTransactionId = "pg-tx-001",
        )

        authorization.cancel(Money(10_000L, CurrencyCode.KRW))

        assertEquals(20_000L, authorization.remainingCancelableAmount.amount)
    }

    @Test
    fun `취소 금액은 남은 취소 가능 금액을 초과할 수 없다`() {
        val authorization = Authorization(
            instrumentType = InstrumentType.CARD,
            requestedAmount = Money(30_000L, CurrencyCode.KRW),
            approvedAmount = Money(30_000L, CurrencyCode.KRW),
            pgTransactionId = "pg-tx-002",
        )

        assertFailsWith<IllegalArgumentException> {
            authorization.cancel(Money(40_000L, CurrencyCode.KRW))
        }
    }

    @Test
    fun `승인 라인 분배 합은 승인 금액과 같아야 한다`() {
        val order = PaymentOrder(
            merchantId = "merchant-1",
            merchantOrderId = "order-domain-001",
            totalAmount = Money(50_000L, CurrencyCode.KRW),
        )
        val line = PaymentOrderLine(
            lineReference = "line-1",
            payeeId = "seller-A",
            lineAmount = Money(50_000L, CurrencyCode.KRW),
            quantity = 1,
        )
        order.addLine(line)
        val authorization = Authorization(
            paymentAllocation = PaymentAllocation(
                paymentOrder = order,
                payerReference = "payer-A",
                allocationAmount = Money(50_000L, CurrencyCode.KRW),
                sequence = 1,
            ),
            instrumentType = InstrumentType.CARD,
            requestedAmount = Money(50_000L, CurrencyCode.KRW),
            approvedAmount = Money(50_000L, CurrencyCode.KRW),
            pgTransactionId = "pg-tx-003",
        )

        authorization.addLinePortion(
            AuthorizationLinePortion(
                paymentOrderLine = line,
                payeeId = "seller-A",
                amount = Money(30_000L, CurrencyCode.KRW),
                sequence = 1,
            ),
        )

        assertFailsWith<IllegalArgumentException> {
            authorization.addLinePortion(
                AuthorizationLinePortion(
                    paymentOrderLine = line,
                    payeeId = "seller-A",
                    amount = Money(25_000L, CurrencyCode.KRW),
                    sequence = 2,
                ),
            )
        }
    }

    @Test
    fun `승인 요청의 라인 분배 합이 승인 금액과 다르면 실패한다`() {
        val service = AuthorizePaymentService(NoopAuthorizePaymentWriter())

        val exception = assertFailsWith<IllegalArgumentException> {
            service.authorize(
                AuthorizePaymentCommand(
                    merchantId = "merchant-1",
                    merchantOrderId = "order-domain-002",
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
                                    pgTransactionId = "pg-tx-004",
                                    linePortions = listOf(
                                        AuthorizationLinePortionRequest(
                                            lineReference = "line-1",
                                            amount = Money(40_000L, CurrencyCode.KRW),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            )
        }

        assertEquals("authorization line portion sum must match approved amount", exception.message)
    }

    private class NoopAuthorizePaymentWriter : AuthorizePaymentWriter {
        override fun save(result: AuthorizePaymentResult): AuthorizePaymentResult = result
    }
}
