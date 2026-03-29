package com.library.feign

import com.library.NaverProperties
import feign.RequestInterceptor
import org.springframework.context.annotation.Bean

class NaverFeignClientConfiguration {

    @Bean fun requestInterceptor(
        naverProperties: NaverProperties
    ): RequestInterceptor {
        return RequestInterceptor {
            it
                .header("X-Naver-Client-Id", naverProperties.headers.clientId)
                .header("X-Naver-Client-Secret", naverProperties.headers.clientSecret)
        }
    }
}