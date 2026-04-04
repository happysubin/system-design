package com.paymentlab.payment.api

import com.paymentlab.payment.api.dto.ReconcilePaymentAttemptResponse
import com.paymentlab.payment.application.PaymentApplicationService
import com.paymentlab.payment.domain.PaymentStatus
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class PaymentReconciliationApiTest {

    @Test
    fun `재확정 요청을 받아 최종 상태를 반환한다`() {
        val paymentApplicationService = mock(PaymentApplicationService::class.java)
        val mockMvc = MockMvcBuilders.standaloneSetup(PaymentController(paymentApplicationService)).build()

        given(paymentApplicationService.reconcilePaymentAttempt(10)).willReturn(
            ReconcilePaymentAttemptResponse(
                paymentAttemptId = 10,
                status = PaymentStatus.DONE,
            ),
        )

        mockMvc.post("/api/v1/payments/10/reconcile")
            .andExpect {
                status { isOk() }
                jsonPath("$.paymentAttemptId") { value(10) }
                jsonPath("$.status") { value("DONE") }
            }
    }
}
