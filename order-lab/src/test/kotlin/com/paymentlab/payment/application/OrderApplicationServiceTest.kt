package com.paymentlab.payment.application

import com.paymentlab.order.api.dto.CreateOrderItemRequest
import com.paymentlab.order.api.dto.CreateOrderRequest
import com.paymentlab.order.domain.Order
import com.paymentlab.order.application.OrderApplicationService
import com.paymentlab.order.infrastructure.persistence.OrderRepository
import com.paymentlab.payment.infrastructure.redis.CheckoutKeyStore
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq

@ExtendWith(MockitoExtension::class)
class OrderApplicationServiceTest {

    @Mock
    lateinit var orderRepository: OrderRepository

    @Mock
    lateinit var checkoutKeyStore: CheckoutKeyStore

    @Test
    fun `주문 생성 요청이 들어오면 주문을 저장하고 응답을 반환한다`() {
        val service = OrderApplicationService(orderRepository, checkoutKeyStore)

        doAnswer { invocation ->
            val saved = invocation.getArgument(0, Order::class.java)
            saved.id = 1
            saved
        }.`when`(orderRepository).save(org.mockito.ArgumentMatchers.any(Order::class.java))
        given(checkoutKeyStore.issue(eq(1L), anyString(), eq(15000L))).willReturn("checkout-1")

        val result = service.createOrder(
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
        )

        val captor = ArgumentCaptor.forClass(Order::class.java)
        verify(orderRepository).save(captor.capture())
        assertNotNull(captor.value.merchantOrderId)
        assertTrue(captor.value.merchantOrderId.isNotBlank())
        assertEquals(1, captor.value.items.size)
        assertEquals(101L, captor.value.items[0].skuId)
        assertEquals(2, captor.value.items[0].quantity)
        assertEquals(7500, captor.value.items[0].unitPrice)
        assertEquals(1, result.orderId)
        assertEquals(captor.value.merchantOrderId, result.merchantOrderId)
        assertEquals("checkout-1", result.checkoutKey)
        assertEquals(15000, result.amount)
        assertEquals(1, result.items.size)
        assertEquals(101L, result.items[0].skuId)
        assertEquals(2, result.items[0].quantity)
        assertEquals(7500, result.items[0].unitPrice)
    }
}
