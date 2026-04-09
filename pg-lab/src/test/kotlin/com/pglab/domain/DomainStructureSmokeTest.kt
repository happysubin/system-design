package com.pglab.domain

import kotlin.test.Test

class DomainStructureSmokeTest {
    @Test
    fun `결제 도메인 클래스 골격이 존재한다`() {
        Class.forName("com.pglab.payment.order.PaymentOrder")
        Class.forName("com.pglab.payment.allocation.PaymentAllocation")
        Class.forName("com.pglab.payment.authorization.Authorization")
        Class.forName("com.pglab.payment.ledger.LedgerEntry")
        Class.forName("com.pglab.payment.settlement.Settlement")
    }
}
