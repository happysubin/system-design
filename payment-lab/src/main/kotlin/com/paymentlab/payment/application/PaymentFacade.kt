package com.paymentlab.payment.application

import com.paymentlab.inventory.application.InventoryHoldApplicationService
import com.paymentlab.payment.api.dto.ApprovePaymentAttemptResponse
import com.paymentlab.payment.api.dto.CreatePaymentAttemptRequest
import com.paymentlab.payment.api.dto.ReconcilePaymentAttemptResponse
import com.paymentlab.payment.infrastructure.pg.PgClient
import org.springframework.stereotype.Service

@Service
/**
 * 결제 시작 흐름의 오케스트레이션을 담당하는 facade다.
 *
 * 외부 API 입장에서는 `POST /payments` 한 번만 호출하면 되지만,
 * 내부적으로는
 * 1) 결제 시도 생성
 * 2) PG 승인 요청
 * 3) 승인 결과 반영
 * 순서로 작업이 이어진다.
 *
 * 이 클래스는 그 순서만 책임지고,
 * 실제 저장/상태 전이/재확정 로직은 아래 application service들에 위임한다.
 */
class PaymentFacade(
    private val paymentApplicationService: PaymentApplicationService,
    private val inventoryHoldApplicationService: InventoryHoldApplicationService,
    private val pgClient: PgClient,
    ) {
    fun startPayment(request: CreatePaymentAttemptRequest): ApprovePaymentAttemptResponse {
        // PG 승인 전에 hold를 먼저 확보하는 이유는,
        // 결제 성공 가능성이 있는 요청만 재고를 선점하고 이후 성공/실패에 따라 한 경로로 정산하기 위해서다.
        val inventoryHold = inventoryHoldApplicationService.reserveOrReuse(request.orderId)
        val paymentAttempt = paymentApplicationService.createPaymentAttempt(request, inventoryHold.id)
        return try {
            val approveResult = pgClient.approve(
                paymentAttempt.paymentAttemptId,
                request.merchantOrderId,
                request.amount,
            )
            paymentApplicationService.applyApproveResult(paymentAttempt.paymentAttemptId, approveResult)
        } catch (exception: RuntimeException) {
            // 동기 승인 호출이 실패해도 PG 쪽 결과가 완전히 불명확할 수 있으므로,
            // 즉시 FAILED로 닫지 않고 PENDING으로 남겨 webhook/reconcile이 정리하게 한다.
            paymentApplicationService.markPendingForUnknownApproveResult(paymentAttempt.paymentAttemptId)
            throw exception
        }
    }

    fun reconcilePaymentAttempt(paymentAttemptId: Long): ReconcilePaymentAttemptResponse {
        val target = paymentApplicationService.loadPendingPaymentAttempt(paymentAttemptId)
        val result = target.pgTransactionId?.let { pgClient.query(it) }
            ?: pgClient.queryByMerchantOrderId(target.merchantOrderId)
        return paymentApplicationService.applyReconcileResult(target.id, result)
    }
}
