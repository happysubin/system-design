package com.firstcomecoupon.coupon.application

import com.firstcomecoupon.coupon.domain.CouponIssue
import com.firstcomecoupon.coupon.domain.CouponSoldOutException
import com.firstcomecoupon.coupon.infrastructure.persistence.CouponIssueRepository
import com.firstcomecoupon.coupon.infrastructure.persistence.CouponRepository
import com.firstcomecoupon.coupon.infrastructure.persistence.MemberRepository
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
        // Redis gate는 빠른 admission control일 뿐이라 drift 가능성이 있다.
        // 최종 확정 직전에는 DB에서 coupon row를 잠그고 capacity를 다시 확인한다.
        val coupon = couponRepository.findByIdForUpdate(couponId) ?: throw NoSuchElementException()
        val issuedCount = couponIssueRepository.countByCouponId(couponId)

        if (issuedCount >= coupon.totalQuantity) {
            throw CouponSoldOutException()
        }

        val savedIssue = couponIssueRepository.saveAndFlush(
            CouponIssue(
                coupon = coupon,
                member = memberRepository.getReferenceById(memberId),
            ),
        )

        return savedIssue
    }
}
