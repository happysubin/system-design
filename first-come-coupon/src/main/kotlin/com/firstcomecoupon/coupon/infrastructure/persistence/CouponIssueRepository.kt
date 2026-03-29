package com.firstcomecoupon.coupon.infrastructure.persistence

import com.firstcomecoupon.coupon.domain.CouponIssue
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface CouponIssueRepository : JpaRepository<CouponIssue, Long> {

    fun countByCouponId(couponId: Long): Long

    @Query(
        """
        select ci.coupon.id as couponId, count(ci) as issuedCount
        from CouponIssue ci
        where ci.coupon.id in :couponIds
        group by ci.coupon.id
        """,
    )
    fun findIssuedCountsByCouponIds(@Param("couponIds") couponIds: Collection<Long>): List<CouponIssueCountProjection>
}

interface CouponIssueCountProjection {
    val couponId: Long
    val issuedCount: Long
}
