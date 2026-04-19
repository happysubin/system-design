package com.pglab.payment.authorization

import com.pglab.payment.allocation.PaymentAllocation
import com.pglab.payment.order.PaymentOrder
import com.pglab.payment.order.PaymentOrderLine
import com.pglab.payment.shared.CurrencyCode
import com.pglab.payment.shared.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AuthorizationTest {
    @Test
    fun `승인 결과는 승인 금액으로 취소 가능 잔액을 초기화한다`() {
        val authorization = Authorization(
            instrumentType = InstrumentType.CARD,
            requestedAmount = Money(30_000L, CurrencyCode.KRW),
            approvedAmount = Money(30_000L, CurrencyCode.KRW),
            pgTransactionId = "pg-tx-001",
        )

        assertEquals(30_000L, authorization.remainingCancelableAmount.amount)
        assertEquals(AuthorizationStatus.APPROVED, authorization.status)
    }

    @Test
    fun `승인 결과는 라인 귀속분을 추가하면 연관관계를 함께 맞춘다`() {
        val authorization = authorization()
        val authorizationOrder = authorization.paymentAllocation!!.paymentOrder!!
        val firstPortion = AuthorizationLinePortion(
            paymentOrderLine = paymentOrderLine(
                order = authorizationOrder,
                lineReference = "line-1",
                payeeId = "seller-A",
                amount = 30_000L,
            ),
            payeeId = "seller-A",
            amount = Money(30_000L, CurrencyCode.KRW),
            sequence = 1,
        )
        val secondPortion = AuthorizationLinePortion(
            paymentOrderLine = paymentOrderLine(
                order = authorizationOrder,
                lineReference = "line-2",
                payeeId = "seller-B",
                amount = 20_000L,
            ),
            payeeId = "seller-B",
            amount = Money(20_000L, CurrencyCode.KRW),
            sequence = 2,
        )

        authorization.addLinePortion(firstPortion)
        authorization.addLinePortion(secondPortion)

        assertEquals(2, authorization.linePortions.size)
        assertEquals(listOf("seller-A", "seller-B"), authorization.linePortions.map { it.payeeId })
        assertEquals(authorization, firstPortion.authorization)
        assertEquals(authorization, secondPortion.authorization)
    }

    @Test
    fun `승인 결과는 라인 귀속분을 제거하면 연관관계를 함께 해제한다`() {
        val authorization = authorization()
        val authorizationOrder = authorization.paymentAllocation!!.paymentOrder!!
        val portion = AuthorizationLinePortion(
            paymentOrderLine = paymentOrderLine(
                order = authorizationOrder,
                lineReference = "line-1",
                payeeId = "seller-A",
                amount = 30_000L,
            ),
            payeeId = "seller-A",
            amount = Money(30_000L, CurrencyCode.KRW),
            sequence = 1,
        )

        authorization.addLinePortion(portion)
        authorization.removeLinePortion(portion)

        assertEquals(emptyList(), authorization.linePortions)
        assertEquals(null, portion.authorization)
    }

    @Test
    fun `이미 다른 승인 결과에 속한 라인 귀속분은 추가할 수 없다`() {
        val firstAuthorization = authorization(pgTransactionId = "pg-tx-002")
        val secondAuthorization = authorization(pgTransactionId = "pg-tx-003")
        val firstAuthorizationOrder = firstAuthorization.paymentAllocation!!.paymentOrder!!
        val portion = AuthorizationLinePortion(
            paymentOrderLine = paymentOrderLine(
                order = firstAuthorizationOrder,
                lineReference = "line-1",
                payeeId = "seller-A",
                amount = 30_000L,
            ),
            payeeId = "seller-A",
            amount = Money(30_000L, CurrencyCode.KRW),
            sequence = 1,
        )

        firstAuthorization.addLinePortion(portion)

        assertFailsWith<IllegalArgumentException> {
            secondAuthorization.addLinePortion(portion)
        }
    }

    @Test
    fun `승인 결과는 다른 주문에 속한 라인 귀속분을 추가할 수 없다`() {
        val authorization = authorization(pgTransactionId = "pg-tx-004")
        val anotherOrder = PaymentOrder(
            merchantId = "platform-merchant",
            merchantOrderId = "other-order",
            totalAmount = Money(30_000L, CurrencyCode.KRW),
        )
        val foreignLine = PaymentOrderLine(
            lineReference = "line-foreign",
            payeeId = "seller-A",
            lineAmount = Money(30_000L, CurrencyCode.KRW),
            quantity = 1,
        )
        anotherOrder.addLine(foreignLine)

        val portion = AuthorizationLinePortion(
            paymentOrderLine = foreignLine,
            payeeId = "seller-A",
            amount = Money(30_000L, CurrencyCode.KRW),
            sequence = 1,
        )

        assertFailsWith<IllegalArgumentException> {
            authorization.addLinePortion(portion)
        }
    }

    @Test
    fun `승인 결과는 승인 금액 합계를 초과하는 라인 귀속분을 추가할 수 없다`() {
        val authorization = authorization(pgTransactionId = "pg-tx-005")
        val authorizationOrder = authorization.paymentAllocation!!.paymentOrder!!
        val firstPortion = AuthorizationLinePortion(
            paymentOrderLine = paymentOrderLine(
                order = authorizationOrder,
                lineReference = "line-1",
                payeeId = "seller-A",
                amount = 30_000L,
            ),
            payeeId = "seller-A",
            amount = Money(30_000L, CurrencyCode.KRW),
            sequence = 1,
        )
        val secondPortion = AuthorizationLinePortion(
            paymentOrderLine = paymentOrderLine(
                order = authorizationOrder,
                lineReference = "line-2",
                payeeId = "seller-B",
                amount = 20_001L,
            ),
            payeeId = "seller-B",
            amount = Money(20_001L, CurrencyCode.KRW),
            sequence = 2,
        )

        authorization.addLinePortion(firstPortion)

        assertFailsWith<IllegalArgumentException> {
            authorization.addLinePortion(secondPortion)
        }
    }

    @Test
    fun `승인 결과는 같은 주문 라인에 대한 중복 귀속분을 하나의 승인 안에 둘 수 없다`() {
        val authorization = authorization(pgTransactionId = "pg-tx-006")
        val authorizationOrder = authorization.paymentAllocation!!.paymentOrder!!
        val orderLine = paymentOrderLine(
            order = authorizationOrder,
            lineReference = "line-1",
            payeeId = "seller-A",
            amount = 50_000L,
        )
        val firstPortion = AuthorizationLinePortion(
            paymentOrderLine = orderLine,
            payeeId = "seller-A",
            amount = Money(30_000L, CurrencyCode.KRW),
            sequence = 1,
        )
        val secondPortion = AuthorizationLinePortion(
            paymentOrderLine = orderLine,
            payeeId = "seller-A",
            amount = Money(20_000L, CurrencyCode.KRW),
            sequence = 2,
        )

        authorization.addLinePortion(firstPortion)

        assertFailsWith<IllegalArgumentException> {
            authorization.addLinePortion(secondPortion)
        }
    }

    private fun authorization(pgTransactionId: String = "pg-tx-001"): Authorization {
        val order = PaymentOrder(
            merchantId = "platform-merchant",
            merchantOrderId = "order-$pgTransactionId",
            totalAmount = Money(50_000L, CurrencyCode.KRW),
        )
        val allocation = PaymentAllocation(
            paymentOrder = order,
            payerReference = "payer-A",
            allocationAmount = Money(50_000L, CurrencyCode.KRW),
            sequence = 1,
        )
        return Authorization(
            paymentAllocation = allocation,
            instrumentType = InstrumentType.CARD,
            requestedAmount = Money(50_000L, CurrencyCode.KRW),
            approvedAmount = Money(50_000L, CurrencyCode.KRW),
            pgTransactionId = pgTransactionId,
        )
    }

    private fun paymentOrderLine(order: PaymentOrder, lineReference: String, payeeId: String, amount: Long): PaymentOrderLine {
        return PaymentOrderLine(
            lineReference = lineReference,
            payeeId = payeeId,
            lineAmount = Money(amount, CurrencyCode.KRW),
            quantity = 1,
        ).also(order::addLine)
    }
}
