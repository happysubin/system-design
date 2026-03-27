package com.firstcomecoupon.repository

import com.firstcomecoupon.domain.Member
import org.springframework.data.jpa.repository.JpaRepository

interface MemberRepository : JpaRepository<Member, Long>
