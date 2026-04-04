package com.paymentlab.payment.api

import com.paymentlab.payment.api.dto.ApprovePaymentAttemptResponse
import com.paymentlab.payment.application.PaymentApplicationService
import com.paymentlab.payment.domain.PaymentStatus
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class PaymentApprovalApiTest {

    @Test
    fun `결제 승인 요청을 받아 pending 응답을 반환한다`() {
        val paymentApplicationService = mock(PaymentApplicationService::class.java)
        val mockMvc = MockMvcBuilders.standaloneSetup(PaymentController(paymentApplicationService)).build()

        given(paymentApplicationService.approvePaymentAttempt(10)).willReturn(
            ApprovePaymentAttemptResponse(
                paymentAttemptId = 10,
                status = PaymentStatus.PENDING,
                pgTransactionId = "pg-tx-1",
            ),
        )

        mockMvc.post("/api/v1/payments/10/approve")
            .andExpect {
                status { isOk() }
                jsonPath("$.paymentAttemptId") { value(10) }
                jsonPath("$.status") { value("PENDING") }
                jsonPath("$.pgTransactionId") { value("pg-tx-1") }
            }
    }
}
