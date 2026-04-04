package com.paymentlab.payment.application

import com.paymentlab.payment.api.dto.CreateOrderRequest
import com.paymentlab.payment.api.dto.CreateOrderResponse
import com.paymentlab.payment.domain.Order
import com.paymentlab.payment.infrastructure.persistence.OrderRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class OrderApplicationService(
    private val orderRepository: OrderRepository,
) {

    @Transactional
    fun createOrder(request: CreateOrderRequest): CreateOrderResponse {
        val savedOrder = orderRepository.save(
            Order(
                merchantOrderId = request.merchantOrderId,
                amount = request.amount,
            ),
        )

        return CreateOrderResponse(
            orderId = savedOrder.id,
            merchantOrderId = savedOrder.merchantOrderId,
            amount = savedOrder.amount,
        )
    }
}
