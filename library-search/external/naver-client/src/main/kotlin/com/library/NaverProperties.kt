package com.library

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "spring.external.naver")
class NaverProperties(
    val url: String,
    val headers: Headers
) {
    class Headers(
        val clientId: String,
        val clientSecret: String,
    )
}