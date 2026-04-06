package com.paymentlab.payment.api

import com.paymentlab.payment.api.dto.IssueCheckoutKeyRequest
import com.paymentlab.payment.api.dto.IssueCheckoutKeyResponse
import com.paymentlab.payment.application.CheckoutKeyApplicationService
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class CheckoutKeyApiTest {

    @Test
    fun `체크아웃 키 발급 요청을 받아 201 응답을 반환한다`() {
        val checkoutKeyApplicationService = mock(CheckoutKeyApplicationService::class.java)
        val mockMvc = MockMvcBuilders.standaloneSetup(CheckoutKeyController(checkoutKeyApplicationService)).build()

        given(checkoutKeyApplicationService.issueCheckoutKey(IssueCheckoutKeyRequest(orderId = 1, merchantOrderId = "order-1", amount = 15000))).willReturn(
            IssueCheckoutKeyResponse(checkoutKey = "checkout-1"),
        )

        mockMvc.post("/api/v1/checkout-keys") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "orderId": 1,
                  "merchantOrderId": "order-1",
                  "amount": 15000
                }
            """.trimIndent()
        }
            .andExpect {
                status { isCreated() }
                jsonPath("$.checkoutKey") { value("checkout-1") }
            }
    }
}
