package com.pglab.payment.authorization

import org.springframework.data.jpa.repository.JpaRepository

interface AuthorizationRepository : JpaRepository<Authorization, Long> {
    fun findAllByPaymentAllocationId(paymentAllocationId: Long): List<Authorization>
}
