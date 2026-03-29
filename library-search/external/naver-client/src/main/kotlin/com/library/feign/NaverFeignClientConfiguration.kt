package com.library.feign

import com.library.NaverProperties
import feign.RequestInterceptor
import feign.codec.ErrorDecoder
import org.springframework.context.annotation.Bean
import tools.jackson.databind.json.JsonMapper

class NaverFeignClientConfiguration {

    @Bean
    fun requestInterceptor(
        naverProperties: NaverProperties
    ): RequestInterceptor {
        return RequestInterceptor {
            it
                .header("X-Naver-Client-Id", naverProperties.headers.clientId)
                .header("X-Naver-Client-Secret", naverProperties.headers.clientSecret)
        }
    }

    @Bean
    fun naverErrorDecoder(
        jsonMapper: JsonMapper,
    ): ErrorDecoder {
        return NaverErrorDecoder(jsonMapper)
    }
}