package com.paymentlab.payment.infrastructure.redis

import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.UUID

@Component
class RedisCheckoutKeyStore(
    private val stringRedisTemplate: StringRedisTemplate,
    @Value("\${payment.checkout-key.ttl-seconds:300}") private val ttlSeconds: Long,
) : CheckoutKeyStore {

    private val consumeScript = DefaultRedisScript(
        """
        local value = redis.call('GET', KEYS[1])
        if value == ARGV[1] then
            redis.call('DEL', KEYS[1])
            return 1
        end
        return 0
        """.trimIndent(),
        Long::class.java,
    )

    override fun issue(orderId: Long, merchantOrderId: String, amount: Long): String {
        val checkoutKey = UUID.randomUUID().toString()
        val payload = payload(orderId, merchantOrderId, amount)
        stringRedisTemplate.opsForValue().set(redisKey(checkoutKey), payload, Duration.ofSeconds(ttlSeconds))
        return checkoutKey
    }

    override fun consumeIfValid(checkoutKey: String, orderId: Long, merchantOrderId: String, amount: Long): Boolean {
        val expectedPayload = payload(orderId, merchantOrderId, amount)
        val key = redisKey(checkoutKey)
        val result = stringRedisTemplate.execute(consumeScript, listOf(key), expectedPayload)
        return result == 1L
    }

    private fun redisKey(checkoutKey: String): String = "checkout:$checkoutKey"

    private fun payload(orderId: Long, merchantOrderId: String, amount: Long): String = "$orderId|$merchantOrderId|$amount"
}
