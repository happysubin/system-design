package com.firstcomecoupon.coupon.application

import com.firstcomecoupon.coupon.domain.CouponIssue
import com.firstcomecoupon.coupon.domain.CouponSoldOutException
import com.firstcomecoupon.coupon.infrastructure.persistence.CouponIssueRepository
import com.firstcomecoupon.coupon.infrastructure.persistence.CouponRepository
import com.firstcomecoupon.coupon.infrastructure.persistence.CouponStockRepository
import com.firstcomecoupon.coupon.infrastructure.persistence.MemberRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CouponClaimFinalizer(
    private val couponIssueRepository: CouponIssueRepository,
    private val couponRepository: CouponRepository,
    private val memberRepository: MemberRepository,
    private val couponStockRepository: CouponStockRepository,
) {

    @Transactional
    fun finalizeClaim(couponId: Long, memberId: Long): CouponIssue {
        if (couponStockRepository.decrementIfAvailable(couponId) == 0) {
            if (couponStockRepository.findByCouponId(couponId) == null) {
                throw NoSuchElementException("coupon stock not found")
            }
            throw CouponSoldOutException()
        }

        val coupon = couponRepository.findById(couponId).orElseThrow()

        val savedIssue = couponIssueRepository.saveAndFlush(
            CouponIssue(
                coupon = coupon,
                member = memberRepository.getReferenceById(memberId),
            ),
        )

        return savedIssue
    }
}
