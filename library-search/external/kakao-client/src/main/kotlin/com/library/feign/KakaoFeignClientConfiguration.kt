package com.library.feign

import feign.RequestInterceptor
import feign.codec.ErrorDecoder
import org.springframework.context.annotation.Bean
import tools.jackson.databind.json.JsonMapper

class KakaoFeignClientConfiguration {


    @Bean
    fun kakaoRequestInterceptor(
        kakaoProperties: KakaoProperties
    ): RequestInterceptor {
        return RequestInterceptor {
            it.header("Authorization", "KakaoAK ${kakaoProperties.headers.restApiKey}")
        }
    }

    @Bean
    fun kakaoErrorDecoder(
        jsonMapper: JsonMapper,
    ): ErrorDecoder {
        return KakaoErrorDecoder(jsonMapper)
    }
}