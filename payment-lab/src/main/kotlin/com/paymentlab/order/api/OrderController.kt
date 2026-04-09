package com.paymentlab.order.api

import com.paymentlab.order.api.dto.CreateOrderRequest
import com.paymentlab.order.api.dto.CreateOrderResponse
import com.paymentlab.order.application.OrderApplicationService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/orders")
/**
 * 로컬 테스트나 예제 흐름에서 주문을 빠르게 만들 때 사용하는 보조 API다.
 * 실제 권장 구조에서는 외부 주문 시스템이 이 역할을 담당한다.
 */
class OrderController(
    private val orderApplicationService: OrderApplicationService,
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    /**
     * 테스트용 주문을 생성할 때 호출한다.
     * 주문서/외부 주문 정보가 이미 있다고 가정하는 실무형 메인 흐름에서는 보조 용도다.
     */
    fun createOrder(@RequestBody request: CreateOrderRequest): CreateOrderResponse {
        return orderApplicationService.createOrder(request)
    }
}
