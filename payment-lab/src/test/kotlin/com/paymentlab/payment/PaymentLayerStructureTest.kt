package com.paymentlab.payment

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class PaymentLayerStructureTest {

    @Test
    fun `결제 기능이 4계층 패키지 구조로 분리되어 있다`() {
        assertNotNull(Class.forName("com.paymentlab.payment.api.PaymentController"))
        assertNotNull(Class.forName("com.paymentlab.payment.api.WebhookController"))
        assertNotNull(Class.forName("com.paymentlab.payment.application.PaymentApplicationService"))
        assertNotNull(Class.forName("com.paymentlab.payment.application.PaymentWebhookApplicationService"))
        assertNotNull(Class.forName("com.paymentlab.payment.domain.Order"))
        assertNotNull(Class.forName("com.paymentlab.payment.domain.PaymentAttempt"))
        assertNotNull(Class.forName("com.paymentlab.payment.domain.PaymentStatus"))
        assertNotNull(Class.forName("com.paymentlab.payment.infrastructure.persistence.OrderRepository"))
        assertNotNull(Class.forName("com.paymentlab.payment.infrastructure.persistence.PaymentAttemptRepository"))
        assertNotNull(Class.forName("com.paymentlab.payment.infrastructure.pg.PgClient"))
    }
}
