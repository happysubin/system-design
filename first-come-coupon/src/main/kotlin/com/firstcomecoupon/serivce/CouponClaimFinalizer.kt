package com.firstcomecoupon.serivce

import com.firstcomecoupon.domain.CouponIssue
import com.firstcomecoupon.repository.CouponIssueRepository
import com.firstcomecoupon.repository.CouponRepository
import com.firstcomecoupon.repository.MemberRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CouponClaimFinalizer(
    private val couponIssueRepository: CouponIssueRepository,
    private val couponRepository: CouponRepository,
    private val memberRepository: MemberRepository,
) {

    @Transactional
    fun finalizeClaim(couponId: Long, memberId: Long): CouponIssue {
        val savedIssue = couponIssueRepository.saveAndFlush(
            CouponIssue(
                coupon = couponRepository.getReferenceById(couponId),
                member = memberRepository.getReferenceById(memberId),
            ),
        )

        return savedIssue
    }
}
