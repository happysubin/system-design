package com.paymentlab.payment.api

import com.paymentlab.inventory.application.InsufficientInventoryReservationException
import com.paymentlab.payment.api.dto.CreatePaymentAttemptRequest
import com.paymentlab.payment.application.PaymentApplicationService
import com.paymentlab.payment.application.PaymentFacade
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class PaymentApiStockFailureTest {

    @Test
    fun `재고 예약에 실패하면 결제 시작 API가 안정적인 재고 부족 오류를 반환한다`() {
        val paymentFacade = mock(PaymentFacade::class.java)
        val paymentApplicationService = mock(PaymentApplicationService::class.java)
        val mockMvc = MockMvcBuilders.standaloneSetup(PaymentController(paymentFacade, paymentApplicationService))
            .setControllerAdvice(PaymentErrorHandler())
            .build()

        given(paymentFacade.startPayment(CreatePaymentAttemptRequest(orderId = 1, merchantOrderId = "order-1", amount = 15000, checkoutKey = "checkout-1")))
            .willThrow(InsufficientInventoryReservationException("insufficient available stock for skuId: sku-1"))

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
                status { isConflict() }
                jsonPath("$.message") { value("insufficient stock") }
            }
    }
}
