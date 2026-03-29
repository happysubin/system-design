package com.firstcomecoupon.coupon.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDateTime

@Entity
@Table(
    name = "coupon_issues",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_coupon_issue_coupon_member",
            columnNames = ["coupon_id", "member_id"],
        ),
    ],
)
class CouponIssue(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coupon_id", nullable = false)
    var coupon: Coupon? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    var member: Member? = null,

    @Column(name = "issued_at", nullable = false)
    var issuedAt: LocalDateTime = LocalDateTime.now(),
)
