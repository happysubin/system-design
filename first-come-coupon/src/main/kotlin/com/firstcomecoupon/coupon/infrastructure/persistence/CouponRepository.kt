package com.firstcomecoupon.coupon.infrastructure.persistence

import com.firstcomecoupon.coupon.domain.Coupon
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface CouponRepository : JpaRepository<Coupon, Long> {

    @Query(
        """
        select c
        from Coupon c
        where c.issueStartAt <= :referenceTime
          and c.issueEndAt >= :referenceTime
        """,
    )
    fun findActiveCoupons(@Param("referenceTime") referenceTime: LocalDateTime): List<Coupon>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Coupon c where c.id = :couponId")
    fun findByIdForUpdate(@Param("couponId") couponId: Long): Coupon?
}
