package com.firstcomecoupon.coupon.application

import com.firstcomecoupon.coupon.domain.CouponClaimResult
import com.firstcomecoupon.coupon.domain.CouponSoldOutException
import com.firstcomecoupon.coupon.infrastructure.redis.CouponClaimRedisGate
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component

@Component
class CouponClaimCompensationHandler(
    private val couponClaimFinalizer: CouponClaimFinalizer,
    private val couponClaimRedisGate: CouponClaimRedisGate,
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
            CouponClaimResult.SoldOut
        } catch (_: DataIntegrityViolationException) {
            // 최종 중복 방어선은 DB unique constraint다.
            // 같은 회원이 이미 저장돼 있으면 Redis 상태를 되돌리고 ALREADY_CLAIMED로 응답한다.
            couponClaimRedisGate.rollback(couponId, memberId)
            CouponClaimResult.AlreadyClaimed
        } catch (exception: RuntimeException) {
            // 알 수 없는 예외도 Redis는 먼저 원복해야 한다.
            // 그렇지 않으면 실패한 요청 때문에 재고만 줄어든 상태가 남을 수 있다.
            couponClaimRedisGate.rollback(couponId, memberId)
            throw exception
        }
    }
}
