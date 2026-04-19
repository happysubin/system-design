package com.pglab.payment.jpa

import com.pglab.payment.allocation.PaymentAllocation
import com.pglab.payment.authorization.Authorization
import com.pglab.payment.authorization.AuthorizationLinePortion
import com.pglab.payment.authorization.InstrumentType
import com.pglab.payment.ledger.LedgerEntry
import com.pglab.payment.ledger.LedgerEntryType
import com.pglab.payment.order.PaymentOrder
import com.pglab.payment.order.PaymentOrderLine
import com.pglab.payment.settlement.Payout
import com.pglab.payment.settlement.Settlement
import com.pglab.payment.settlement.SettlementLine
import com.pglab.payment.shared.CurrencyCode
import com.pglab.payment.shared.Money
import org.junit.jupiter.api.assertThrows
import jakarta.persistence.EntityManager
import org.hibernate.PropertyValueException
import org.hibernate.exception.ConstraintViolationException
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import kotlin.test.assertNotNull

@DataJpaTest
class PaymentJpaMappingTest(
    @Autowired private val entityManager: EntityManager,
) {
    @Test
    fun `결제 도메인 엔티티는 JPA로 저장할 수 있다`() {
        val order = PaymentOrder(
            merchantId = "merchant-1",
            merchantOrderId = "order-100",
            totalAmount = Money(50_000L, CurrencyCode.KRW),
        )
        order.addLine(
            PaymentOrderLine(
                lineReference = "line-1",
                payeeId = "seller-A",
                lineAmount = Money(30_000L, CurrencyCode.KRW),
                quantity = 1,
            ),
        )
        order.addLine(
            PaymentOrderLine(
                lineReference = "line-2",
                payeeId = "seller-B",
                lineAmount = Money(20_000L, CurrencyCode.KRW),
                quantity = 2,
            ),
        )
        entityManager.persist(order)

        val allocation = PaymentAllocation(
            paymentOrder = order,
            payerReference = "user-A",
            allocationAmount = Money(20_000L, CurrencyCode.KRW),
            sequence = 1,
        )
        entityManager.persist(allocation)

        val authorization = Authorization(
            paymentAllocation = allocation,
            instrumentType = InstrumentType.CARD,
            requestedAmount = Money(20_000L, CurrencyCode.KRW),
            approvedAmount = Money(20_000L, CurrencyCode.KRW),
            pgTransactionId = "pg-tx-100",
        )
        authorization.addLinePortion(
            AuthorizationLinePortion(
                paymentOrderLine = order.lines[0],
                payeeId = order.lines[0].payeeId,
                amount = Money(10_000L, CurrencyCode.KRW),
                sequence = 1,
            ),
        )
        authorization.addLinePortion(
            AuthorizationLinePortion(
                paymentOrderLine = order.lines[1],
                payeeId = order.lines[1].payeeId,
                amount = Money(10_000L, CurrencyCode.KRW),
                sequence = 2,
            ),
        )
        entityManager.persist(authorization)

        val ledgerEntry = LedgerEntry(
            paymentOrder = order,
            paymentAllocation = allocation,
            authorization = authorization,
            paymentOrderLine = order.lines[0],
            payeeId = order.lines[0].payeeId,
            type = LedgerEntryType.AUTH_CAPTURED,
            amount = Money(10_000L, CurrencyCode.KRW),
            referenceTransactionId = "pg-tx-100",
            description = "initial authorization",
        )
        entityManager.persist(ledgerEntry)

        val settlement = Settlement(
            payeeId = "seller-A",
            grossAmount = Money(50_000L, CurrencyCode.KRW),
            feeAmount = Money(1_000L, CurrencyCode.KRW),
            netAmount = Money(49_000L, CurrencyCode.KRW),
        )

        val settlementLine = SettlementLine(
            ledgerEntry = ledgerEntry,
        )
        settlement.addLine(settlementLine)

        entityManager.persist(settlement)

        val payout = Payout(
            settlement = settlement,
            requestedAmount = Money(49_000L, CurrencyCode.KRW),
            bankCode = "081",
            bankAccountNumber = "110-123-456789",
            accountHolderName = "상점A",
            bankTransferRequestId = "payout-req-001",
        )
        entityManager.persist(payout)

        entityManager.flush()
        entityManager.clear()

        assertNotNull(order.id)
        order.lines.forEach { assertNotNull(it.id) }
        assertNotNull(allocation.id)
        assertNotNull(authorization.id)
        authorization.linePortions.forEach { assertNotNull(it.id) }
        assertNotNull(ledgerEntry.id)
        assertNotNull(settlement.id)
        assertNotNull(payout.id)
        assertNotNull(settlementLine.id)
    }

    @Test
    fun `상위 주문이 없는 주문 라인은 JPA로 저장할 수 없다`() {
        val line = PaymentOrderLine(
            lineReference = "line-without-order",
            payeeId = "seller-A",
            lineAmount = Money(10_000L, CurrencyCode.KRW),
            quantity = 1,
        )

        assertThrows<PropertyValueException> {
            entityManager.persist(line)
            entityManager.flush()
        }
    }

    @Test
    fun `같은 원장 엔트리는 둘 이상의 정산 라인으로 중복 연결될 수 없다`() {
        val order = PaymentOrder(
            merchantId = "merchant-1",
            merchantOrderId = "order-dup-1",
            totalAmount = Money(10_000L, CurrencyCode.KRW),
        )
        entityManager.persist(order)

        val ledgerEntry = LedgerEntry(
            paymentOrder = order,
            payeeId = "payee-1",
            type = LedgerEntryType.AUTH_CAPTURED,
            amount = Money(10_000L, CurrencyCode.KRW),
            referenceTransactionId = "dup-tx-1",
            description = "dup-source",
        )
        entityManager.persist(ledgerEntry)

        val firstSettlement = Settlement(
            payeeId = "payee-1",
            grossAmount = Money(10_000L, CurrencyCode.KRW),
            feeAmount = Money(0L, CurrencyCode.KRW),
            netAmount = Money(10_000L, CurrencyCode.KRW),
        )
        firstSettlement.addLine(SettlementLine(ledgerEntry = ledgerEntry))
        entityManager.persist(firstSettlement)
        entityManager.flush()

        val secondSettlement = Settlement(
            payeeId = "payee-1",
            grossAmount = Money(10_000L, CurrencyCode.KRW),
            feeAmount = Money(0L, CurrencyCode.KRW),
            netAmount = Money(10_000L, CurrencyCode.KRW),
        )
        assertThrows<ConstraintViolationException> {
            secondSettlement.addLine(SettlementLine(ledgerEntry = ledgerEntry))
            entityManager.persist(secondSettlement)
            entityManager.flush()
        }
    }
}
