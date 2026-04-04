package com.paymentlab.payment.api

import com.paymentlab.payment.api.dto.CreateOrderRequest
import com.paymentlab.payment.api.dto.CreateOrderResponse
import com.paymentlab.payment.application.OrderApplicationService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/orders")
class OrderController(
    private val orderApplicationService: OrderApplicationService,
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createOrder(@RequestBody request: CreateOrderRequest): CreateOrderResponse {
        return orderApplicationService.createOrder(request)
    }
}
