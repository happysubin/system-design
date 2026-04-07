package com.refreshtoken.auth.domain

import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("app.auth")
data class TokenProperties(
    val secret: String,
    val issuer: String,
    val accessTokenTtl: Duration,
    val refreshTokenTtl: Duration,
)
