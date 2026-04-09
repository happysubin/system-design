package com.paymentlab.payment.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "payment_attempts")
class PaymentAttempt(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    /**
     * 외부 주문서 시스템이 관리하는 내부 주문 식별자다.
     * 결제 서비스 안에서는 이 값을 주문 원본을 참조하는 최소 기준으로만 사용한다.
     */
    @Column(name = "order_id", nullable = false)
    var orderId: Long = 0,

    /**
     * PG와 주고받을 때 사용하는 비즈니스 주문 키다.
     * 내부 PK를 직접 노출하지 않고, 주문 대사와 운영 추적에 사용한다.
     */
    @Column(name = "merchant_order_id", nullable = false, length = 100)
    var merchantOrderId: String = "",

    @Column(name = "checkout_key", nullable = false, length = 120, unique = true)
    var checkoutKey: String = "",

    @Column(name = "pg_transaction_id", length = 120, unique = true)
    var pgTransactionId: String? = null,

    @Column(name = "webhook_secret", length = 120)
    var webhookSecret: String? = null,

    @Column(name = "inventory_hold_id")
    var inventoryHoldId: Long? = null,

    /**
     * 결제 시작 시점에 확정한 금액 스냅샷이다.
     * 이후 원본 주문 금액이 바뀌더라도 이 결제 시도가 어떤 금액으로 시작됐는지 보존한다.
     */
    @Column(nullable = false)
    var amount: Long = 0,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var status: PaymentStatus = PaymentStatus.READY,

    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),
)
