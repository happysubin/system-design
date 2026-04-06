package com.paymentlab.payment.api

import com.paymentlab.payment.api.dto.IssueCheckoutKeyRequest
import com.paymentlab.payment.api.dto.IssueCheckoutKeyResponse
import com.paymentlab.payment.application.CheckoutKeyApplicationService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/checkout-keys")
class CheckoutKeyController(
    private val checkoutKeyApplicationService: CheckoutKeyApplicationService,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun issueCheckoutKey(@RequestBody request: IssueCheckoutKeyRequest): IssueCheckoutKeyResponse {
        return checkoutKeyApplicationService.issueCheckoutKey(request)
    }
}
