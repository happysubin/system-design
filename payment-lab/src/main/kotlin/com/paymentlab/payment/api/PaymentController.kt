package com.paymentlab.payment.api

import com.paymentlab.payment.api.dto.CreatePaymentAttemptRequest
import com.paymentlab.payment.api.dto.CreatePaymentAttemptResponse
import com.paymentlab.payment.api.dto.ApprovePaymentAttemptResponse
import com.paymentlab.payment.api.dto.ReconcilePaymentAttemptResponse
import com.paymentlab.payment.application.PaymentApplicationService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/payments")
class PaymentController(
    private val paymentApplicationService: PaymentApplicationService,
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createPaymentAttempt(@RequestBody request: CreatePaymentAttemptRequest): CreatePaymentAttemptResponse {
        return paymentApplicationService.createPaymentAttempt(request)
    }

    @PostMapping("/{paymentAttemptId}/approve")
    fun approvePaymentAttempt(@PathVariable paymentAttemptId: Long): ApprovePaymentAttemptResponse {
        return paymentApplicationService.approvePaymentAttempt(paymentAttemptId)
    }

    @PostMapping("/{paymentAttemptId}/reconcile")
    fun reconcilePaymentAttempt(@PathVariable paymentAttemptId: Long): ReconcilePaymentAttemptResponse {
        return paymentApplicationService.reconcilePaymentAttempt(paymentAttemptId)
    }
}
