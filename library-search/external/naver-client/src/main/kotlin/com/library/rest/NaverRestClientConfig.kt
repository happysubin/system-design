package com.library.rest

import com.library.NaverProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient


@Configuration
class NaverRestClientConfig(
    val naverProperties: NaverProperties
) {

    @Bean
    fun restClient(): RestClient {
        val restClient = RestClient
            .builder()
            .baseUrl(naverProperties.url)
            .defaultHeader("X-Naver-Client-Id", naverProperties.headers.clientId)
            .defaultHeader("X-Naver-Client-Secret", naverProperties.headers.clientSecret)
            .build()
        return restClient
    }
}