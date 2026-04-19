package com.pglab.payment.authorization

import com.pglab.payment.order.PaymentOrderLine
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
@Table(name = "authorization_line_portions")
class AuthorizationLinePortion(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_order_line_id", nullable = false)
    val paymentOrderLine: PaymentOrderLine? = null,
    @Column(nullable = false)
    val payeeId: String = "",
    @Embedded
    @AttributeOverrides(
        AttributeOverride(name = "amount", column = Column(name = "amount", nullable = false)),
        AttributeOverride(name = "currency", column = Column(name = "currency", nullable = false, length = 3)),
    )
    val amount: Money = Money(0L, CurrencyCode.KRW),
    @Column(nullable = false)
    val sequence: Int = 0,
) {
    init {
        paymentOrderLine?.let { line ->
            require(payeeId == line.payeeId) {
                "authorization line portion payee must match payment order line payee"
            }
            require(amount.currency == line.lineAmount.currency) {
                "authorization line portion currency must match payment order line currency"
            }
        }
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "authorization_id", nullable = false)
    private var authorizationInternal: Authorization? = null

    val authorization: Authorization?
        get() = authorizationInternal

    internal fun attachTo(authorization: Authorization) {
        authorizationInternal = authorization
    }

    internal fun detach() {
        authorizationInternal = null
    }
}
