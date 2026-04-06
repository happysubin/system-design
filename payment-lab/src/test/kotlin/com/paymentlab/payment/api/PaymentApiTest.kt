package com.paymentlab.payment.api

import com.paymentlab.payment.api.dto.CreatePaymentAttemptRequest
import com.paymentlab.payment.api.dto.CreatePaymentAttemptResponse
import com.paymentlab.payment.application.PaymentApplicationService
import com.paymentlab.payment.domain.PaymentStatus
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock

class PaymentApiTest {

    @Test
    fun `결제 시도 생성 요청을 받아 201 응답을 반환한다`() {
        val paymentApplicationService = mock(PaymentApplicationService::class.java)
        val mockMvc = MockMvcBuilders.standaloneSetup(PaymentController(paymentApplicationService)).build()

        given(paymentApplicationService.createPaymentAttempt(CreatePaymentAttemptRequest(orderId = 1, merchantOrderId = "order-1", amount = 15000, idempotencyKey = "idem-1"))).willReturn(
            CreatePaymentAttemptResponse(
                paymentAttemptId = 10,
                orderId = 1,
                status = PaymentStatus.READY,
            ),
        )

        mockMvc.post("/api/v1/payments") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "orderId": 1,
                  "merchantOrderId": "order-1",
                  "amount": 15000,
                  "idempotencyKey": "idem-1"
                }
            """.trimIndent()
        }
            .andExpect {
                status { isCreated() }
                jsonPath("$.paymentAttemptId") { value(10) }
                jsonPath("$.orderId") { value(1) }
                jsonPath("$.status") { value("READY") }
            }
    }
}
