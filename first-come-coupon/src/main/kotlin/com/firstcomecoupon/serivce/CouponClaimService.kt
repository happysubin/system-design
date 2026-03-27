package com.firstcomecoupon.serivce

import com.firstcomecoupon.controller.dto.IssueCouponRequest
import com.firstcomecoupon.repository.CouponRepository
import com.firstcomecoupon.repository.MemberRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import java.time.LocalDateTime

sealed interface CouponClaimResult {
    data class Issued(
        val issueId: Long,
        val couponId: Long,
        val memberId: Long,
        val issuedAt: LocalDateTime,
    ) : CouponClaimResult

    data object AlreadyClaimed : CouponClaimResult
    data object SoldOut : CouponClaimResult
    data object CouponNotFound : CouponClaimResult
    data object MemberNotFound : CouponClaimResult
    data object NotInIssueWindow : CouponClaimResult
    data object InternalFailure : CouponClaimResult
}

@Service
class CouponClaimService(
    private val couponRepository: CouponRepository,
    private val memberRepository: MemberRepository,
    private val couponClaimRedisGate: CouponClaimRedisGate,
    private val couponClaimFinalizer: CouponClaimFinalizer,
) {

    fun claimCoupon(couponId: Long, request: IssueCouponRequest): CouponClaimResult {
        val coupon = couponRepository.findById(couponId).orElse(null) ?: return CouponClaimResult.CouponNotFound
        val member = memberRepository.findById(request.memberId).orElse(null) ?: return CouponClaimResult.MemberNotFound
        val now = LocalDateTime.now()

        if (now.isBefore(coupon.issueStartAt) || now.isAfter(coupon.issueEndAt)) {
            return CouponClaimResult.NotInIssueWindow
        }

        return when (couponClaimRedisGate.tryClaim(couponId, member.id)) {
            CouponClaimGateResult.ALREADY_CLAIMED -> CouponClaimResult.AlreadyClaimed
            CouponClaimGateResult.SOLD_OUT -> CouponClaimResult.SoldOut
            CouponClaimGateResult.NOT_INITIALIZED -> CouponClaimResult.InternalFailure
            CouponClaimGateResult.PASSED -> finalizeClaim(couponId, member.id)
        }
    }

    private fun finalizeClaim(couponId: Long, memberId: Long): CouponClaimResult {
        return try {
            val issue = couponClaimFinalizer.finalizeClaim(couponId, memberId)
            CouponClaimResult.Issued(
                issueId = issue.id,
                couponId = couponId,
                memberId = memberId,
                issuedAt = issue.issuedAt,
            )
        } catch (_: DataIntegrityViolationException) {
            couponClaimRedisGate.rollback(couponId, memberId)
            CouponClaimResult.AlreadyClaimed
        } catch (exception: RuntimeException) {
            couponClaimRedisGate.rollback(couponId, memberId)
            throw exception
        }
    }
}
