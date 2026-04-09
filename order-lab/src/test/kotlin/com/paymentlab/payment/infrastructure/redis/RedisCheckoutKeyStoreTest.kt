package com.paymentlab.payment.infrastructure.redis

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.BDDMockito.given
import org.springframework.data.redis.core.StringRedisTemplate
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@ExtendWith(MockitoExtension::class)
class RedisCheckoutKeyStoreTest {

    @Mock
    lateinit var stringRedisTemplate: StringRedisTemplate

    @Test
    fun `payload가 일치하면 redis 스크립트 실행 결과로 key를 원자적으로 소비한다`() {
        val store = RedisCheckoutKeyStore(stringRedisTemplate, 300)

        given(stringRedisTemplate.execute<Long>(any(), eq(listOf("checkout:checkout-1")), eq("1|order-1|15000"))).willReturn(1L)

        val result = store.consumeIfValid("checkout-1", 1, "order-1", 15000)

        assertTrue(result)
        verify(stringRedisTemplate, never()).delete(any<String>())
    }

    @Test
    fun `payload가 일치하지 않으면 redis 스크립트 실행 결과로 소비를 실패한다`() {
        val store = RedisCheckoutKeyStore(stringRedisTemplate, 300)

        given(stringRedisTemplate.execute<Long>(any(), eq(listOf("checkout:checkout-1")), eq("1|order-1|15000"))).willReturn(0L)

        val result = store.consumeIfValid("checkout-1", 1, "order-1", 15000)

        assertFalse(result)
        verify(stringRedisTemplate, never()).delete(any<String>())
    }
}
