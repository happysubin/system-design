package com.paymentlab.payment.application

import com.paymentlab.payment.api.dto.CreatePaymentAttemptRequest
import com.paymentlab.payment.api.dto.CreatePaymentAttemptResponse
import com.paymentlab.payment.api.dto.ApprovePaymentAttemptResponse
import com.paymentlab.payment.api.dto.ReconcilePaymentAttemptResponse
import com.paymentlab.payment.domain.PaymentAttempt
import com.paymentlab.payment.domain.PaymentStatus
import com.paymentlab.payment.infrastructure.persistence.PaymentAttemptRepository
import com.paymentlab.payment.infrastructure.pg.PgApproveResult
import com.paymentlab.payment.infrastructure.pg.PgApproveOutcome
import com.paymentlab.payment.infrastructure.pg.PgClient
import com.paymentlab.payment.infrastructure.redis.CheckoutKeyStore
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
/**
 * 결제 시도 생성, 승인 결과 반영, 재확정 같은
 * 결제 도메인의 핵심 상태 전이 로직을 담당하는 application service다.
 *
 * 이 클래스는 외부 API 흐름 전체를 조합하기보다는,
 * "한 단계의 상태 전이"와 "트랜잭션 경계가 필요한 저장 작업"에 집중한다.
 *
 * 현재 구조에서는
 * - checkoutKey 검증 후 결제 시도 생성
 * - PG 승인 결과를 `READY -> PENDING/DECLINED`로 반영
 * - `PENDING -> DONE/FAILED` 재확정
 * 을 맡는다.
 */
class PaymentApplicationService(
    private val paymentAttemptRepository: PaymentAttemptRepository,
    private val pgClient: PgClient,
    private val checkoutKeyStore: CheckoutKeyStore,
    private val paymentFinalizationService: PaymentFinalizationService,
) {

    /**
     * 결제 시작 직전에 checkoutKey를 검증하고, `READY` 상태의 결제 시도를 저장한다.
     *
     * 이 메서드는 PG 네트워크 호출 전에 실행되는 저장 단계다.
     * 따라서 결제 시작 요청이 실제로 들어왔다는 사실을 DB에 먼저 남기고,
     * 이후 외부 PG 호출이 실패하더라도 결제 시도 레코드는 유지되도록 하는 역할을 한다.
     */
    @Transactional
    fun createPaymentAttempt(request: CreatePaymentAttemptRequest): CreatePaymentAttemptResponse {
        return createPaymentAttempt(request, null)
    }

    @Transactional
    fun createPaymentAttempt(request: CreatePaymentAttemptRequest, inventoryHoldId: Long?): CreatePaymentAttemptResponse {
        val existingAttempt = paymentAttemptRepository.findByCheckoutKey(request.checkoutKey)
        if (existingAttempt != null) {
            if (existingAttempt.inventoryHoldId == null && inventoryHoldId != null) {
                // 과거 시도 레코드가 먼저 생기고 hold 연결이 비어 있을 수 있어서,
                // 중복 시도에서는 새 attempt를 만들지 않고 linked hold만 backfill한다.
                paymentAttemptRepository.updateInventoryHoldIdIfAbsent(existingAttempt.id, inventoryHoldId)
            }

            return CreatePaymentAttemptResponse(
                paymentAttemptId = existingAttempt.id,
                orderId = existingAttempt.orderId,
                status = existingAttempt.status,
            )
        }

        val isValidCheckoutKey = checkoutKeyStore.consumeIfValid(
            request.checkoutKey,
            request.orderId,
            request.merchantOrderId,
            request.amount,
        )
        if (!isValidCheckoutKey) {
            throw IllegalArgumentException("invalid checkout key: ${request.checkoutKey}")
        }

        // checkoutKey를 소비한 뒤 READY attempt를 먼저 남기는 이유는,
        // 이후 외부 PG 호출이 실패하거나 지연돼도 '결제 시작 시도' 자체는 DB에 추적 가능하게 두기 위해서다.
        val savedAttempt = paymentAttemptRepository.save(
            PaymentAttempt(
                orderId = request.orderId,
                merchantOrderId = request.merchantOrderId,
                checkoutKey = request.checkoutKey,
                amount = request.amount,
                inventoryHoldId = inventoryHoldId,
                status = PaymentStatus.READY,
            ),
        )

        return CreatePaymentAttemptResponse(
            paymentAttemptId = savedAttempt.id,
            orderId = savedAttempt.orderId,
            status = savedAttempt.status,
        )
    }

    /**
     * 이미 만들어진 결제 시도에 대해 PG 승인 요청을 보낸다.
     *
     * 이 메서드는 상태를 직접 바꾸지 않고,
     * PG 호출 결과를 받아 `applyApproveResult`에 넘기는 역할만 한다.
     * 즉 네트워크 I/O와 상태 반영을 분리하기 위한 중간 단계다.
     */
    fun approvePaymentAttempt(paymentAttemptId: Long): ApprovePaymentAttemptResponse {
        val paymentAttempt = paymentAttemptRepository.findById(paymentAttemptId)
            .orElseThrow { IllegalArgumentException("payment attempt not found: $paymentAttemptId") }

        if (paymentAttempt.status != PaymentStatus.READY) {
            throw IllegalStateException("payment attempt is not ready: ${paymentAttempt.status}")
        }

        val approveResult = pgClient.approve(
            paymentAttempt.id,
            paymentAttempt.merchantOrderId,
            paymentAttempt.amount,
        )

        return applyApproveResult(paymentAttempt.id, approveResult)
    }

    /**
     * PG 승인 결과를 DB 상태 전이로 반영한다.
     *
     * `READY -> PENDING/DECLINED` 조건부 업데이트를 수행하며,
     * 동시에 `pgTransactionId`, `webhookSecret`도 함께 저장한다.
     * 경쟁 상황에서 이미 다른 요청이 상태를 바꿨다면 예외를 던져 중복 반영을 막는다.
     */
    @Transactional
    fun applyApproveResult(paymentAttemptId: Long, approveResult: PgApproveResult): ApprovePaymentAttemptResponse {
        val nextStatus = when (approveResult.outcome) {
            PgApproveOutcome.PENDING -> PaymentStatus.PENDING
            PgApproveOutcome.DECLINED -> PaymentStatus.DECLINED
        }

        val updated = paymentAttemptRepository.updateStatusAndPgTransactionIdAndWebhookSecretIfCurrentStatus(
            paymentAttemptId,
            PaymentStatus.READY,
            nextStatus,
            approveResult.pgTransactionId,
            approveResult.webhookSecret,
        )
        if (updated == 0) {
            throw IllegalStateException("payment attempt is no longer ready: $paymentAttemptId")
        }

        return ApprovePaymentAttemptResponse(
            paymentAttemptId = paymentAttemptId,
            status = nextStatus,
            pgTransactionId = approveResult.pgTransactionId,
        )
    }

    @Transactional
    fun markPendingForUnknownApproveResult(paymentAttemptId: Long): ApprovePaymentAttemptResponse {
        val updated = paymentAttemptRepository.updateStatusIfCurrentStatusOnly(
            paymentAttemptId,
            PaymentStatus.READY,
            PaymentStatus.PENDING,
        )
        if (updated == 0) {
            throw IllegalStateException("payment attempt is no longer ready: $paymentAttemptId")
        }

        return ApprovePaymentAttemptResponse(
            paymentAttemptId = paymentAttemptId,
            status = PaymentStatus.PENDING,
            pgTransactionId = "",
        )
    }

    /**
     * 재확정(reconcile) 대상 결제를 읽어오는 단계다.
     *
     * `PENDING` 상태인지 확인한 뒤 현재 스냅샷을 반환한다.
     * 이후 실제 PG 조회는 트랜잭션 밖에서 수행되고,
     * 조회 결과 반영은 `applyReconcileResult`에서 다시 트랜잭션으로 처리된다.
     */
    @Transactional(readOnly = true)
    fun loadPendingPaymentAttempt(paymentAttemptId: Long): PaymentAttempt {
        val paymentAttempt = paymentAttemptRepository.findById(paymentAttemptId)
            .orElseThrow { IllegalArgumentException("payment attempt not found: $paymentAttemptId") }

        if (paymentAttempt.status != PaymentStatus.PENDING) {
            throw IllegalStateException("payment attempt is not pending: ${paymentAttempt.status}")
        }

        return paymentAttempt
    }

    /**
     * PG 재조회 결과를 최종 상태로 반영한다.
     *
     * 현재는 조회 결과가 성공이면 `DONE`, 그 외는 `FAILED`로 본다.
     * 상태 반영은 `PENDING -> DONE/FAILED` 조건부 업데이트로 수행해,
     * 다른 경로가 먼저 최종 확정한 경우 중복 반영되지 않도록 한다.
     */
    @Transactional
    fun applyReconcileResult(paymentAttemptId: Long, result: String): ReconcilePaymentAttemptResponse {
        val paymentAttempt = paymentAttemptRepository.findById(paymentAttemptId)
            .orElseThrow { IllegalArgumentException("payment attempt not found: $paymentAttemptId") }

        val nextStatus = if (result == "SUCCESS") {
            PaymentStatus.DONE
        } else {
            PaymentStatus.FAILED
        }

        val updated = paymentAttemptRepository.updateStatusIfCurrentStatus(
            paymentAttemptId,
            PaymentStatus.PENDING,
            nextStatus,
        )
        if (updated == 0) {
            throw IllegalStateException("payment attempt is no longer pending: $paymentAttemptId")
        }

        // 웹훅과 재확정은 같은 결제를 두 경로에서 동시에 정리할 수 있으므로,
        // inventory 후속 정리는 PENDING 상태 전이를 실제로 이긴 경우에만 수행되어야 한다.
        paymentFinalizationService.finalizeInventoryHold(paymentAttempt, nextStatus)

        return ReconcilePaymentAttemptResponse(
            paymentAttemptId = paymentAttemptId,
            status = nextStatus,
        )
    }
}
