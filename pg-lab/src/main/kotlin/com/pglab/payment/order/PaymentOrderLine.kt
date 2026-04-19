package com.pglab.payment.order

import com.pglab.payment.shared.CurrencyCode
import com.pglab.payment.shared.Money
import jakarta.persistence.AttributeOverride
import jakarta.persistence.AttributeOverrides
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "payment_order_lines")
class PaymentOrderLine(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(nullable = false)
    val lineReference: String = "",
    @Column(nullable = false)
    val payeeId: String = "",
    @Embedded
    @AttributeOverrides(
        AttributeOverride(name = "amount", column = Column(name = "line_amount", nullable = false)),
        AttributeOverride(name = "currency", column = Column(name = "line_currency", nullable = false, length = 3)),
    )
    val lineAmount: Money = Money(0L, CurrencyCode.KRW),
    @Column(nullable = false)
    val quantity: Int = 0,
) {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_order_id", nullable = false)
    private var paymentOrderInternal: PaymentOrder? = null

    val paymentOrder: PaymentOrder?
        get() = paymentOrderInternal

    internal fun attachTo(paymentOrder: PaymentOrder) {
        paymentOrderInternal = paymentOrder
    }

    internal fun detach() {
        paymentOrderInternal = null
    }
}
