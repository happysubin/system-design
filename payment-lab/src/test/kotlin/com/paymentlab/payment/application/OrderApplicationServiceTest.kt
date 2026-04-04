package com.paymentlab.payment.application

import com.paymentlab.payment.api.dto.CreateOrderRequest
import com.paymentlab.payment.infrastructure.persistence.OrderRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.any
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import kotlin.test.assertEquals
import com.paymentlab.payment.domain.Order

@ExtendWith(MockitoExtension::class)
class OrderApplicationServiceTest {

    @Mock
    lateinit var orderRepository: OrderRepository

    @Test
    fun `주문 생성 요청이 들어오면 주문을 저장하고 응답을 반환한다`() {
        val service = OrderApplicationService(orderRepository)

        given(orderRepository.save(any(Order::class.java))).willAnswer { invocation ->
            val saved = invocation.getArgument(0, Order::class.java)
            saved.id = 1
            saved
        }

        val result = service.createOrder(
            CreateOrderRequest(
                merchantOrderId = "order-1",
                amount = 15000,
            ),
        )

        verify(orderRepository).save(any(Order::class.java))
        assertEquals(1, result.orderId)
        assertEquals("order-1", result.merchantOrderId)
        assertEquals(15000, result.amount)
    }
}
