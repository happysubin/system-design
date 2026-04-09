package com.paymentlab.payment.api

import com.paymentlab.order.api.OrderController
import com.paymentlab.order.api.dto.CreateOrderItemRequest
import com.paymentlab.order.api.dto.CreateOrderRequest
import com.paymentlab.order.api.dto.CreateOrderResponse
import com.paymentlab.order.api.dto.CreateOrderResponseItem
import com.paymentlab.order.application.OrderApplicationService
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

        given(
            orderApplicationService.createOrder(
                CreateOrderRequest(
                    amount = 15000,
                    items = listOf(
                        CreateOrderItemRequest(
                            skuId = 101L,
                            quantity = 2,
                            unitPrice = 7500,
                        ),
                    ),
                ),
            ),
        ).willReturn(
            CreateOrderResponse(
                orderId = 1,
                merchantOrderId = "generated-order-key",
                checkoutKey = "checkout-1",
                amount = 15000,
                items = listOf(
                    CreateOrderResponseItem(
                        skuId = 101L,
                        quantity = 2,
                        unitPrice = 7500,
                    ),
                ),
            ),
        )

        mockMvc.post("/api/v1/orders") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "amount": 15000,
                  "items": [
                    {
                      "skuId": 101,
                      "quantity": 2,
                      "unitPrice": 7500
                    }
                  ]
                }
            """.trimIndent()
        }
            .andExpect {
                status { isCreated() }
                jsonPath("$.orderId") { value(1) }
                jsonPath("$.merchantOrderId") { value("generated-order-key") }
                jsonPath("$.checkoutKey") { value("checkout-1") }
                jsonPath("$.amount") { value(15000) }
                jsonPath("$.items[0].skuId") { value(101) }
                jsonPath("$.items[0].quantity") { value(2) }
                jsonPath("$.items[0].unitPrice") { value(7500) }
            }
    }
}
