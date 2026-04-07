package com.paymentlab.payment.api

import com.paymentlab.payment.api.dto.PaymentWebhookRequest
import com.paymentlab.payment.api.dto.PaymentWebhookResponse
import com.paymentlab.payment.application.PaymentWebhookApplicationService
import com.paymentlab.payment.domain.PaymentStatus
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class PaymentWebhookApiTest {

    @Test
    fun `웹훅 요청을 받아 최종 상태를 반환한다`() {
        val paymentWebhookApplicationService = mock(PaymentWebhookApplicationService::class.java)
        val mockMvc = MockMvcBuilders.standaloneSetup(WebhookController(paymentWebhookApplicationService)).build()

        given(paymentWebhookApplicationService.handleWebhook(PaymentWebhookRequest(merchantOrderId = "order-1", pgTransactionId = "pg-tx-1", secret = "secret-1", result = "SUCCESS"))).willReturn(
            PaymentWebhookResponse(
                paymentAttemptId = 10,
                status = PaymentStatus.DONE,
            ),
        )

        mockMvc.post("/api/v1/payment-webhooks") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "merchantOrderId": "order-1",
                  "pgTransactionId": "pg-tx-1",
                  "secret": "secret-1",
                  "result": "SUCCESS"
                }
            """.trimIndent()
        }
            .andExpect {
                status { isOk() }
                jsonPath("$.paymentAttemptId") { value(10) }
                jsonPath("$.status") { value("DONE") }
            }
    }

    @Test
    fun `webhook secret 검증 실패면 400 응답을 반환한다`() {
        val paymentWebhookApplicationService = mock(PaymentWebhookApplicationService::class.java)
        val mockMvc = MockMvcBuilders.standaloneSetup(WebhookController(paymentWebhookApplicationService))
            .setControllerAdvice(PaymentErrorHandler())
            .build()

        given(paymentWebhookApplicationService.handleWebhook(PaymentWebhookRequest(merchantOrderId = "order-1", pgTransactionId = "pg-tx-1", secret = "wrong-secret", result = "SUCCESS")))
            .willThrow(IllegalArgumentException("invalid webhook secret"))

        mockMvc.post("/api/v1/payment-webhooks") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "merchantOrderId": "order-1",
                  "pgTransactionId": "pg-tx-1",
                  "secret": "wrong-secret",
                  "result": "SUCCESS"
                }
            """.trimIndent()
        }
            .andExpect {
                status { isBadRequest() }
            }
    }
}
