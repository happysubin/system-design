package com.refreshtoken.auth.infrastructure

import com.refreshtoken.auth.domain.RefreshTokenStore
import com.refreshtoken.auth.domain.RefreshTokenStoreUnavailableException
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import java.time.Duration
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component

@Primary
@Component
class ResilientRefreshTokenStore(
    private val redisRefreshTokenStore: RedisRefreshTokenStore,
    private val meterRegistry: MeterRegistry,
) : RefreshTokenStore {
    @CircuitBreaker(name = "redisRefreshTokenStore", fallbackMethod = "saveFallback")
    override fun save(refreshToken: String, subject: String, ttl: Duration) {
        record("save") {
            redisRefreshTokenStore.save(refreshToken, subject, ttl)
        }
    }

    @CircuitBreaker(name = "redisRefreshTokenStore", fallbackMethod = "existsFallback")
    override fun exists(refreshToken: String): Boolean =
        record("exists") {
            redisRefreshTokenStore.exists(refreshToken)
        }

    @CircuitBreaker(name = "redisRefreshTokenStore", fallbackMethod = "deleteFallback")
    override fun delete(refreshToken: String) {
        record("delete") {
            redisRefreshTokenStore.delete(refreshToken)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun saveFallback(refreshToken: String, subject: String, ttl: Duration, throwable: Throwable) {
        handleFallback("save", throwable)
    }

    @Suppress("UNUSED_PARAMETER")
    fun existsFallback(refreshToken: String, throwable: Throwable): Boolean =
        handleFallback("exists", throwable)

    @Suppress("UNUSED_PARAMETER")
    fun deleteFallback(refreshToken: String, throwable: Throwable) {
        handleFallback("delete", throwable)
    }

    private fun handleFallback(operation: String, throwable: Throwable): Nothing {
        if (throwable is CallNotPermittedException) {
            Counter.builder("auth.redis.failures")
                .tag("operation", operation)
                .register(meterRegistry)
                .increment()
            Timer.builder("auth.redis.operations")
                .tag("operation", operation)
                .tag("outcome", "short_circuited")
                .register(meterRegistry)
                .record(Duration.ZERO)
        }
        throw RefreshTokenStoreUnavailableException()
    }

    private fun <T> record(operation: String, action: () -> T): T {
        val sample = Timer.start(meterRegistry)

        return try {
            val result = action()
            sample.stop(
                Timer.builder("auth.redis.operations")
                    .tag("operation", operation)
                    .tag("outcome", "success")
                    .register(meterRegistry),
            )
            result
        } catch (_: RuntimeException) {
            Counter.builder("auth.redis.failures")
                .tag("operation", operation)
                .register(meterRegistry)
                .increment()
            sample.stop(
                Timer.builder("auth.redis.operations")
                    .tag("operation", operation)
                    .tag("outcome", "failure")
                    .register(meterRegistry),
            )
            throw RefreshTokenStoreUnavailableException()
        }
    }
}
