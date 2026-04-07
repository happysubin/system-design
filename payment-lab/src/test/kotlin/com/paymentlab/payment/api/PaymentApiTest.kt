package com.paymentlab.payment.api

import com.paymentlab.payment.api.dto.CreatePaymentAttemptRequest
import com.paymentlab.payment.api.dto.ApprovePaymentAttemptResponse
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
    fun `결제 시작 요청을 받아 바로 승인 요청까지 진행하고 201 응답을 반환한다`() {
        val paymentApplicationService = mock(PaymentApplicationService::class.java)
        val mockMvc = MockMvcBuilders.standaloneSetup(PaymentController(paymentApplicationService))
            .setControllerAdvice(PaymentErrorHandler())
            .build()

        given(paymentApplicationService.startPayment(CreatePaymentAttemptRequest(orderId = 1, merchantOrderId = "order-1", amount = 15000, checkoutKey = "checkout-1"))).willReturn(
            ApprovePaymentAttemptResponse(
                paymentAttemptId = 10,
                status = PaymentStatus.PENDING,
                pgTransactionId = "pg-tx-1",
            ),
        )

        mockMvc.post("/api/v1/payments") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "orderId": 1,
                  "merchantOrderId": "order-1",
                  "amount": 15000,
                  "checkoutKey": "checkout-1"
                }
            """.trimIndent()
        }
            .andExpect {
                status { isCreated() }
                jsonPath("$.paymentAttemptId") { value(10) }
                jsonPath("$.status") { value("PENDING") }
                jsonPath("$.pgTransactionId") { value("pg-tx-1") }
            }
    }

    @Test
    fun `유효하지 않은 checkout key면 400 응답을 반환한다`() {
        val paymentApplicationService = mock(PaymentApplicationService::class.java)
        val mockMvc = MockMvcBuilders.standaloneSetup(PaymentController(paymentApplicationService))
            .setControllerAdvice(PaymentErrorHandler())
            .build()

        given(paymentApplicationService.startPayment(CreatePaymentAttemptRequest(orderId = 1, merchantOrderId = "order-1", amount = 15000, checkoutKey = "invalid-checkout")))
            .willThrow(IllegalArgumentException("invalid checkout key: invalid-checkout"))

        mockMvc.post("/api/v1/payments") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "orderId": 1,
                  "merchantOrderId": "order-1",
                  "amount": 15000,
                  "checkoutKey": "invalid-checkout"
                }
            """.trimIndent()
        }
            .andExpect {
                status { isBadRequest() }
            }
    }
}
