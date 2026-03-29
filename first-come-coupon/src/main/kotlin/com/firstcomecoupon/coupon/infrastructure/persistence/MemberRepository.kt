package com.firstcomecoupon.coupon.infrastructure.persistence

import com.firstcomecoupon.coupon.domain.Member
import org.springframework.data.jpa.repository.JpaRepository

interface MemberRepository : JpaRepository<Member, Long>
