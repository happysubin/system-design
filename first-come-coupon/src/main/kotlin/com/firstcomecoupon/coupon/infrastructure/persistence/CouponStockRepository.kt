package com.firstcomecoupon.coupon.infrastructure.persistence

import com.firstcomecoupon.coupon.domain.CouponStock
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface CouponStockRepository : JpaRepository<CouponStock, Long> {

    fun findByCouponId(couponId: Long): CouponStock?

    @Modifying
    @Query(
        """
        update CouponStock cs
        set cs.remainingQuantity = cs.remainingQuantity - 1,
            cs.updatedAt = CURRENT_TIMESTAMP
        where cs.couponId = :couponId
          and cs.remainingQuantity > 0
        """,
    )
    fun decrementIfAvailable(@Param("couponId") couponId: Long): Int

    @Modifying
    @Query(
        """
        update CouponStock cs
        set cs.remainingQuantity = cs.remainingQuantity + 1,
            cs.updatedAt = CURRENT_TIMESTAMP
        where cs.couponId = :couponId
        """,
    )
    fun increment(@Param("couponId") couponId: Long): Int
}
