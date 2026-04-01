package com.library.feign

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "spring.external.kakao")
class KakaoProperties(
    val headers: Headers,
) {
    class Headers(
        val restApiKey: String
    )
}