package com.library.unit

import com.library.NaverProperties
import com.library.feign.NaverFeignClientConfiguration
import feign.RequestTemplate
import io.kotest.core.spec.style.StringSpec
import org.assertj.core.api.Assertions

class NaverFeignClientConfigurationTest: StringSpec({

    "naverRequestInterceptor의 header에 key값들이 적용된다." {

        // given
        val requestTemplate = RequestTemplate()
        val client = NaverFeignClientConfiguration()
        val properties = NaverProperties(
            url = "https://openapi.naver.com",
            headers = NaverProperties.Headers(
                clientId = "clientId",
                clientSecret = "clientSecret"
            )
        )

        //when
        val interceptor = client.naverRequestInterceptor(
            properties
        )

        interceptor.apply(requestTemplate)

        // then
        Assertions.assertThat(requestTemplate.headers()["X-Naver-Client-Id"]).contains(properties.headers.clientId)
        Assertions.assertThat(requestTemplate.headers()["X-Naver-Client-Secret"]).contains(properties.headers.clientSecret)
    }
})