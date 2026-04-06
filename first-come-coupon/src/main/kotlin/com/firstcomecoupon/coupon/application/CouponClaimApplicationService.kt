package com.firstcomecoupon.coupon.application

import com.firstcomecoupon.coupon.api.dto.IssueCouponRequest
import com.firstcomecoupon.coupon.domain.CouponClaimResult
import com.firstcomecoupon.coupon.infrastructure.redis.CouponClaimGateResult
import com.firstcomecoupon.coupon.infrastructure.redis.RedisClaimGateGuard
import com.firstcomecoupon.coupon.infrastructure.redis.RedisGateUnavailableException
import org.springframework.stereotype.Service

@Service
class CouponClaimApplicationService(
    private val couponClaimEligibilityChecker: CouponClaimEligibilityChecker,
    private val redisClaimGateGuard: RedisClaimGateGuard,
    private val couponClaimCompensationHandler: CouponClaimCompensationHandler,
    private val couponClaimSqlFallbackService: CouponClaimSqlFallbackService,
    private val couponClaimFallbackRateLimiter: CouponClaimFallbackRateLimiter,
    private val couponFallbackProperties: CouponFallbackProperties,
) {

    fun claimCoupon(couponId: Long, request: IssueCouponRequest): CouponClaimResult {
        val eligibility = couponClaimEligibilityChecker.check(couponId, request.memberId)

        if (eligibility is CouponClaimEligibilityResult.Ineligible) {
            return eligibility.result
        }

        val eligible = eligibility as CouponClaimEligibilityResult.Eligible

        return try {
            when (redisClaimGateGuard.tryClaim(couponId, eligible.member.id)) {
                CouponClaimGateResult.ALREADY_CLAIMED -> CouponClaimResult.AlreadyClaimed
                CouponClaimGateResult.SOLD_OUT -> CouponClaimResult.SoldOut
                CouponClaimGateResult.NOT_INITIALIZED -> CouponClaimResult.InternalFailure("coupon stock is not initialized")
                CouponClaimGateResult.PASSED -> couponClaimCompensationHandler.finalizeClaim(couponId, eligible.member.id)
            }
        } catch (_: RedisGateUnavailableException) {
            handleRedisUnavailable(couponId, eligible.member.id)
        } catch (_: RuntimeException) {
            CouponClaimResult.InternalFailure("unexpected coupon claim failure")
        }
    }

    private fun handleRedisUnavailable(couponId: Long, memberId: Long): CouponClaimResult {
        if (!couponFallbackProperties.sqlOnlyEnabled) {
            return CouponClaimResult.InternalFailure("redis service unavailable")
        }

        if (!couponClaimFallbackRateLimiter.tryAcquire()) {
            return CouponClaimResult.InternalFailure("redis fallback rate limit exceeded")
        }

        return try {
            couponClaimSqlFallbackService.claimWithoutRedis(couponId, memberId)
        } finally {
            couponClaimFallbackRateLimiter.release()
        }
    }
}
