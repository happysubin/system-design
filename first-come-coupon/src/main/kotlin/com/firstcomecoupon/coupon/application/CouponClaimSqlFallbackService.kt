package com.firstcomecoupon.coupon.application

import com.firstcomecoupon.coupon.domain.CouponClaimResult
import com.firstcomecoupon.coupon.domain.CouponSoldOutException
import org.hibernate.exception.ConstraintViolationException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service

@Service
class CouponClaimSqlFallbackService(
    private val couponClaimFinalizer: CouponClaimFinalizer,
) {
    fun claimWithoutRedis(couponId: Long, memberId: Long): CouponClaimResult =
        try {
            val issue = couponClaimFinalizer.finalizeClaim(couponId, memberId)
            CouponClaimResult.Issued(
                issueId = issue.id,
                couponId = couponId,
                memberId = memberId,
                issuedAt = issue.issuedAt,
            )
        } catch (_: CouponSoldOutException) {
            CouponClaimResult.SoldOut
        } catch (exception: DataIntegrityViolationException) {
            if (isDuplicateCouponClaim(exception)) {
                CouponClaimResult.AlreadyClaimed
            } else {
                throw exception
            }
        }

    private fun isDuplicateCouponClaim(exception: DataIntegrityViolationException): Boolean {
        var current: Throwable? = exception
        while (current != null) {
            if (current is ConstraintViolationException && current.constraintName == COUPON_MEMBER_UNIQUE_CONSTRAINT) {
                return true
            }

            val message = current.message.orEmpty()
            if (message.contains(COUPON_MEMBER_UNIQUE_CONSTRAINT, ignoreCase = true)) {
                return true
            }

            if (current is java.sql.SQLException && current.sqlState == UNIQUE_VIOLATION_SQL_STATE) {
                return true
            }

            current = current.cause
        }

        return false
    }

    companion object {
        private const val COUPON_MEMBER_UNIQUE_CONSTRAINT = "uk_coupon_issue_coupon_member"
        private const val UNIQUE_VIOLATION_SQL_STATE = "23505"
    }
}
