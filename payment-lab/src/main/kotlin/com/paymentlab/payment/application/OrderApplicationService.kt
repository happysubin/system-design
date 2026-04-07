package com.paymentlab.payment.application

import com.paymentlab.payment.api.dto.CreateOrderRequest
import com.paymentlab.payment.api.dto.CreateOrderResponse
import com.paymentlab.payment.domain.Order
import com.paymentlab.payment.infrastructure.persistence.OrderRepository
import com.paymentlab.payment.infrastructure.redis.CheckoutKeyStore
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class OrderApplicationService(
    private val orderRepository: OrderRepository,
    private val checkoutKeyStore: CheckoutKeyStore,
) {

    @Transactional
    fun createOrder(request: CreateOrderRequest): CreateOrderResponse {
        val savedOrder = orderRepository.save(
            Order(
                merchantOrderId = UUID.randomUUID().toString(),
                amount = request.amount,
            ),
        )
        val checkoutKey = checkoutKeyStore.issue(savedOrder.id, savedOrder.merchantOrderId, savedOrder.amount)

        return CreateOrderResponse(
            orderId = savedOrder.id,
            merchantOrderId = savedOrder.merchantOrderId,
            checkoutKey = checkoutKey,
            amount = savedOrder.amount,
        )
    }
}
