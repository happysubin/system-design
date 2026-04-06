package com.firstcomecoupon.coupon.application

import com.firstcomecoupon.coupon.domain.CouponClaimResult
import com.firstcomecoupon.coupon.domain.CouponSoldOutException
import com.firstcomecoupon.coupon.infrastructure.redis.CouponClaimRedisGate
import com.firstcomecoupon.coupon.infrastructure.reconciliation.CouponStockReconciliationService
import org.hibernate.exception.ConstraintViolationException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component

@Component
class CouponClaimCompensationHandler(
    private val couponClaimFinalizer: CouponClaimFinalizer,
    private val couponClaimRedisGate: CouponClaimRedisGate,
    private val couponStockReconciliationService: CouponStockReconciliationService,
) {

    fun finalizeClaim(couponId: Long, memberId: Long): CouponClaimResult {
        return try {
            val issue = couponClaimFinalizer.finalizeClaim(couponId, memberId)
            CouponClaimResult.Issued(
                issueId = issue.id,
                couponId = couponId,
                memberId = memberId,
                issuedAt = issue.issuedAt,
            )
        } catch (_: CouponSoldOutException) {
            // Redis gate는 통과했지만 SQL 최종 검증에서 수량 초과가 드러난 경우다.
            // 이미 감소시킨 Redis 재고와 claim marker를 원복해야 drift가 커지지 않는다.
            couponClaimRedisGate.rollback(couponId, memberId)
            couponStockReconciliationService.reconcileCouponStock(couponId)
            CouponClaimResult.SoldOut
        } catch (exception: DataIntegrityViolationException) {
            couponClaimRedisGate.rollback(couponId, memberId)

            if (isDuplicateCouponClaim(exception)) {
                CouponClaimResult.AlreadyClaimed
            } else {
                couponStockReconciliationService.reconcileCouponStock(couponId)
                throw exception
            }
        } catch (exception: RuntimeException) {
            // 알 수 없는 예외도 Redis는 먼저 원복해야 한다.
            // 그렇지 않으면 실패한 요청 때문에 재고만 줄어든 상태가 남을 수 있다.
            couponClaimRedisGate.rollback(couponId, memberId)
            couponStockReconciliationService.reconcileCouponStock(couponId)
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
