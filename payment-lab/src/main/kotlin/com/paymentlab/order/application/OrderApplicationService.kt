package com.paymentlab.order.application

import com.paymentlab.order.api.dto.CreateOrderRequest
import com.paymentlab.order.api.dto.CreateOrderResponse
import com.paymentlab.order.api.dto.CreateOrderResponseItem
import com.paymentlab.order.domain.Order
import com.paymentlab.order.domain.OrderItem
import com.paymentlab.order.infrastructure.persistence.OrderRepository
import com.paymentlab.payment.infrastructure.redis.CheckoutKeyStore
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
/**
 * 로컬 테스트와 흐름 검증을 위한 주문 생성용 application service다.
 *
 * 현재 `payment-lab`에서는 외부 주문 시스템을 완전히 구현하지 않았기 때문에,
 * 테스트용으로 주문을 만들고 `merchantOrderId`, `checkoutKey`를 함께 발급하는 역할을 한다.
 *
 * 실무형 구조에서는 이 책임이 외부 주문 시스템으로 이동할 수 있다.
 */
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
                items = request.items.map {
                    OrderItem(
                        skuId = it.skuId,
                        quantity = it.quantity,
                        unitPrice = it.unitPrice,
                    )
                }.toMutableList(),
            ),
        )
        val checkoutKey = checkoutKeyStore.issue(savedOrder.id, savedOrder.merchantOrderId, savedOrder.amount)

        return CreateOrderResponse(
            orderId = savedOrder.id,
            merchantOrderId = savedOrder.merchantOrderId,
            checkoutKey = checkoutKey,
            amount = savedOrder.amount,
            items = savedOrder.items.map {
                CreateOrderResponseItem(
                    skuId = it.skuId,
                    quantity = it.quantity,
                    unitPrice = it.unitPrice,
                )
            },
        )
    }
}
