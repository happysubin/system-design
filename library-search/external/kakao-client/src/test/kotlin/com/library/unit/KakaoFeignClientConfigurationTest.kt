package com.library.unit

import com.library.feign.KakaoFeignClientConfiguration
import com.library.feign.KakaoProperties
import feign.RequestTemplate
import io.kotest.core.spec.style.StringSpec
import org.assertj.core.api.Assertions

class KakaoFeignClientConfigurationTest: StringSpec({

    "kakaoRequestInterceptor의 header에 key값들이 적용된다." {

        // given
        val requestTemplate = RequestTemplate()
        val client = KakaoFeignClientConfiguration()
        val properties = KakaoProperties(
            headers = KakaoProperties.Headers(
                restApiKey = "restApiKey"
            )
        )

        //when
        val interceptor = client.kakaoRequestInterceptor(
            properties
        )

        interceptor.apply(requestTemplate)

        // then
        Assertions.assertThat(requestTemplate.headers()["Authorization"]).contains("KakaoAK ${properties.headers.restApiKey}")
    }
})