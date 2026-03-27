package com.firstcomecoupon.repository

import com.firstcomecoupon.domain.CouponIssue
import org.springframework.data.jpa.repository.JpaRepository

interface CouponIssueRepository : JpaRepository<CouponIssue, Long>
