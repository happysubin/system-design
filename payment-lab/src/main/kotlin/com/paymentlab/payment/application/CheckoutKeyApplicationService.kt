package com.paymentlab.payment.application

import com.paymentlab.payment.api.dto.IssueCheckoutKeyRequest
import com.paymentlab.payment.api.dto.IssueCheckoutKeyResponse
import com.paymentlab.payment.infrastructure.redis.CheckoutKeyStore
import org.springframework.stereotype.Service

@Service
class CheckoutKeyApplicationService(
    private val checkoutKeyStore: CheckoutKeyStore,
) {
    fun issueCheckoutKey(request: IssueCheckoutKeyRequest): IssueCheckoutKeyResponse {
        val checkoutKey = checkoutKeyStore.issue(request.orderId, request.merchantOrderId, request.amount)
        return IssueCheckoutKeyResponse(checkoutKey = checkoutKey)
    }
}
