package com.paymentlab.payment

import com.paymentlab.payment.domain.PaymentStatus
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class PaymentLayerStructureTest {

    @Test
    fun `결제 기능이 4계층 패키지 구조로 분리되어 있다`() {
        assertNotNull(Class.forName("com.paymentlab.payment.api.PaymentController"))
        assertNotNull(Class.forName("com.paymentlab.payment.api.WebhookController"))
        assertNotNull(Class.forName("com.paymentlab.payment.application.PaymentApplicationService"))
        assertNotNull(Class.forName("com.paymentlab.payment.application.PaymentWebhookApplicationService"))
        assertNotNull(Class.forName("com.paymentlab.order.domain.Order"))
        assertNotNull(Class.forName("com.paymentlab.payment.domain.PaymentAttempt"))
        assertNotNull(Class.forName("com.paymentlab.payment.domain.PaymentStatus"))
        assertNotNull(Class.forName("com.paymentlab.order.infrastructure.persistence.OrderRepository"))
        assertNotNull(Class.forName("com.paymentlab.payment.infrastructure.persistence.PaymentAttemptRepository"))
        assertNotNull(Class.forName("com.paymentlab.payment.infrastructure.pg.PgClient"))
    }

    @Test
    fun `PaymentStatus는 결제 플로우에서 실제 사용되는 상태만 포함한다`() {
        val statusNames = PaymentStatus.entries.map { it.name }.toSet()
        val usedStatuses = setOf("READY", "PENDING", "DECLINED", "DONE", "FAILED", "CANCELLED")

        assertTrue(
            statusNames == usedStatuses,
            "PaymentStatus should only contain statuses actually used by payment flow. Expected $usedStatuses but got $statusNames"
        )
    }
}
