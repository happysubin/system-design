package com.paymentlab.payment.api

import com.paymentlab.payment.api.dto.PaymentWebhookRequest
import com.paymentlab.payment.api.dto.PaymentWebhookResponse
import com.paymentlab.payment.application.PaymentWebhookApplicationService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/payment-webhooks")
class WebhookController(
    private val paymentWebhookApplicationService: PaymentWebhookApplicationService,
) {

    @PostMapping
    fun handleWebhook(@RequestBody request: PaymentWebhookRequest): PaymentWebhookResponse {
        return paymentWebhookApplicationService.handleWebhook(request)
    }
}
