package com.paymentlab.payment.api

import com.paymentlab.payment.api.dto.CreateOrderRequest
import com.paymentlab.payment.api.dto.CreateOrderResponse
import com.paymentlab.payment.application.OrderApplicationService
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class OrderApiTest {

    @Test
    fun `주문 생성 요청을 받아 201 응답을 반환한다`() {
        val orderApplicationService = mock(OrderApplicationService::class.java)
        val mockMvc = MockMvcBuilders.standaloneSetup(OrderController(orderApplicationService)).build()

        given(orderApplicationService.createOrder(CreateOrderRequest(amount = 15000))).willReturn(
            CreateOrderResponse(
                orderId = 1,
                merchantOrderId = "generated-order-key",
                amount = 15000,
            ),
        )

        mockMvc.post("/api/v1/orders") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "amount": 15000
                }
            """.trimIndent()
        }
            .andExpect {
                status { isCreated() }
                jsonPath("$.orderId") { value(1) }
                jsonPath("$.merchantOrderId") { value("generated-order-key") }
                jsonPath("$.amount") { value(15000) }
            }
    }
}
