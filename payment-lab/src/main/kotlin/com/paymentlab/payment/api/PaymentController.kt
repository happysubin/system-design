package com.paymentlab.payment.api

import com.paymentlab.payment.api.dto.CreatePaymentAttemptRequest
import com.paymentlab.payment.api.dto.ApprovePaymentAttemptResponse
import com.paymentlab.payment.api.dto.ReconcilePaymentAttemptResponse
import com.paymentlab.payment.application.PaymentFacade
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
/**
 * 실제 돈 흐름을 시작하고 상태를 진행시키는 결제 API다.
 * 외부 주문 시스템이 준비한 주문 정보와 checkoutKey를 입력으로 받는다.
 */
class PaymentController(
    private val paymentFacade: PaymentFacade,
    private val paymentApplicationService: PaymentApplicationService,
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    /**
     * 사용자가 주문서 화면에서 결제하기를 눌렀을 때 호출한다.
     * 외부 주문 정보와 checkoutKey를 검증한 뒤 결제 시도를 만들고 바로 PG 승인 요청까지 진행한다.
     */
    fun startPayment(@RequestBody request: CreatePaymentAttemptRequest): ApprovePaymentAttemptResponse {
        return paymentFacade.startPayment(request)
    }

    @PostMapping("/{paymentAttemptId}/reconcile")
    /**
     * 웹훅이 늦거나 누락됐을 때 PG를 다시 조회해 최종 상태를 확정할 때 호출한다.
     * `PENDING` 상태의 결제 시도만 `DONE` 또는 `FAILED`로 확정한다.
     */
    fun reconcilePaymentAttempt(@PathVariable paymentAttemptId: Long): ReconcilePaymentAttemptResponse {
        return paymentFacade.reconcilePaymentAttempt(paymentAttemptId)
    }
}
