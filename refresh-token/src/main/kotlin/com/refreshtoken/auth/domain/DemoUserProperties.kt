package com.refreshtoken.auth.domain

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("app.auth.demo-user")
data class DemoUserProperties(
    val username: String,
    val password: String,
)
