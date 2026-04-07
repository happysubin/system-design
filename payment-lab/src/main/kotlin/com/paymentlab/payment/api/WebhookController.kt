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
/**
 * PG가 비동기적으로 보내는 결제 상태 변경 알림을 받는 API다.
 * 현재는 토스페이먼츠 기준으로 secret 검증 후에만 상태를 반영한다.
 */
class WebhookController(
    private val paymentWebhookApplicationService: PaymentWebhookApplicationService,
) {

    @PostMapping
    /**
     * PG 웹훅이 들어왔을 때 호출된다.
     * secret과 주문 식별자를 확인한 뒤 정상 웹훅이면 최종 상태를 반영한다.
     */
    fun handleWebhook(@RequestBody request: PaymentWebhookRequest): PaymentWebhookResponse {
        return paymentWebhookApplicationService.handleWebhook(request)
    }
}
