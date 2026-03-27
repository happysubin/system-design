package com.firstcomecoupon.serivce

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component

enum class CouponClaimGateResult {
    // Redis gate를 통과했고, 이제 SQL 최종 저장 단계로 넘어갈 수 있는 상태
    PASSED,

    // 같은 회원이 이미 이 쿠폰에 대한 claim marker를 가진 상태
    ALREADY_CLAIMED,

    // Redis 재고 키는 존재하지만 남은 수량이 없는 상태
    SOLD_OUT,

    // Redis 재고 키가 아직 초기화되지 않았거나 비정상적으로 유실된 상태
    NOT_INITIALIZED,
}

@Component
class CouponClaimRedisGate(
    private val stringRedisTemplate: StringRedisTemplate,
) {

    fun tryClaim(couponId: Long, memberId: Long): CouponClaimGateResult {
        val result = stringRedisTemplate.execute(
            CLAIM_SCRIPT,
            listOf(stockKey(couponId), claimKey(couponId, memberId)),
        )

        return when (result) {
            1L -> CouponClaimGateResult.PASSED
            0L -> CouponClaimGateResult.SOLD_OUT
            -1L -> CouponClaimGateResult.ALREADY_CLAIMED
            else -> CouponClaimGateResult.NOT_INITIALIZED
        }
    }

    fun rollback(couponId: Long, memberId: Long) {
        stringRedisTemplate.opsForValue().increment(stockKey(couponId))
        stringRedisTemplate.delete(claimKey(couponId, memberId))
    }

    private fun stockKey(couponId: Long): String = "coupon:stock:$couponId"

    private fun claimKey(couponId: Long, memberId: Long): String = "coupon:claim:$couponId:$memberId"

    companion object {
        private val CLAIM_SCRIPT = DefaultRedisScript<Long>().apply {
            setResultType(Long::class.java)
            // 중복 claim 확인과 재고 차감을 한 번에 처리해야
            // 멀티 서버 환경에서 check-then-act race condition을 피할 수 있다.
            setScriptText("""
                if redis.call('EXISTS', KEYS[2]) == 1 then
                    return -1
                end
                local stock = redis.call('GET', KEYS[1])
                if not stock then
                    return -2
                end
                stock = tonumber(stock)
                if stock <= 0 then
                    return 0
                end
                redis.call('DECR', KEYS[1])
                redis.call('SET', KEYS[2], '1')
                return 1
            """.trimIndent())
        }
    }
}
