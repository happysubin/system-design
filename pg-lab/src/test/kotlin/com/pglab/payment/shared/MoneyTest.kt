package com.pglab.payment.shared

import kotlin.test.Test
import kotlin.test.assertFailsWith

class MoneyTest {
    @Test
    fun `금액 값 객체는 음수를 허용하지 않는다`() {
        assertFailsWith<IllegalArgumentException> {
            Money(amount = -1L, currency = CurrencyCode.KRW)
        }
    }
}
