package com.firstcomecoupon.coupon.infrastructure.redis

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.data.redis.core.StringRedisTemplate

@ExtendWith(MockitoExtension::class)
class CouponClaimRedisGateTest {

    @Mock
    lateinit var stringRedisTemplate: StringRedisTemplate

    @Test
    fun `rollback executes atomically in redis script instead of separate increment and delete calls`() {
        val gate = CouponClaimRedisGate(stringRedisTemplate)

        gate.rollback(1L, 2L)

        verify(stringRedisTemplate).execute(
            org.mockito.ArgumentMatchers.any<RedisScript<Long>>(),
            eq(listOf("coupon:stock:1", "coupon:claim:1:2")),
        )
        verify(stringRedisTemplate, never()).opsForValue()
        verify(stringRedisTemplate, never()).delete("coupon:claim:1:2")
    }
}
