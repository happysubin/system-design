package com.refreshtoken.auth.infrastructure

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("app.auth.redis-key")
data class RedisKeyProperties(
    val environment: String,
    val appName: String,
)
