package com.firstcomecoupon.coupon.infrastructure.redis

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
        stringRedisTemplate.execute(
            ROLLBACK_SCRIPT,
            listOf(stockKey(couponId), claimKey(couponId, memberId)),
        )
    }

    fun rollbackSoldOut(couponId: Long, memberId: Long) {
        stringRedisTemplate.execute(
            SOLD_OUT_ROLLBACK_SCRIPT,
            listOf(stockKey(couponId), claimKey(couponId, memberId)),
        )
    }

    private fun stockKey(couponId: Long): String = "coupon:stock:$couponId"

    private fun claimKey(couponId: Long, memberId: Long): String = "coupon:claim:$couponId:$memberId"

    companion object {
        private val CLAIM_SCRIPT = DefaultRedisScript<Long>().apply {
            setResultType(Long::class.java)
            // 중복 claim 확인과 재고 차감을 한 번에 처리해야
            // 멀티 서버 환경에서 check-then-act race condition을 피할 수 있다.
            setScriptText("""
                -- 이미 같은 회원이 claim marker를 가지고 있으면 중복 발급 시도다.
                -- 이 경우 재고를 건드리지 않고 즉시 ALREADY_CLAIMED(-1)를 반환한다.
                if redis.call('EXISTS', KEYS[2]) == 1 then
                    return -1
                end

                -- 재고 키가 없으면 아직 초기화되지 않은 비정상 상태로 본다.
                local stock = redis.call('GET', KEYS[1])
                if not stock then
                    return -2
                end

                stock = tonumber(stock)

                -- 남은 수량이 0 이하이면 SOLD_OUT(0)을 반환한다.
                if stock <= 0 then
                    return 0
                end

                -- 여기까지 왔으면 중복도 아니고 재고도 있으므로
                -- 재고 1 차감과 회원 claim marker 기록을 같은 원자 구간에서 처리한다.
                redis.call('DECR', KEYS[1])
                redis.call('SET', KEYS[2], '1')

                -- SQL 최종 저장 단계로 넘어갈 수 있는 상태.
                return 1
            """.trimIndent())
        }

        private val ROLLBACK_SCRIPT = DefaultRedisScript<Long>().apply {
            setResultType(Long::class.java)
            setScriptText(
                """
                if redis.call('EXISTS', KEYS[2]) == 0 then
                    return 0
                end

                if redis.call('EXISTS', KEYS[1]) == 1 then
                    redis.call('INCR', KEYS[1])
                end

                redis.call('DEL', KEYS[2])
                return 1
                """.trimIndent(),
            )
        }

        private val SOLD_OUT_ROLLBACK_SCRIPT = DefaultRedisScript<Long>().apply {
            setResultType(Long::class.java)
            setScriptText(
                """
                if redis.call('EXISTS', KEYS[2]) == 0 then
                    return 0
                end

                redis.call('SET', KEYS[1], '0')
                redis.call('DEL', KEYS[2])
                return 1
                """.trimIndent(),
            )
        }
    }
}
