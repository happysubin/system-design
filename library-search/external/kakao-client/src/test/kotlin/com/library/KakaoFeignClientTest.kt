package com.library

import com.library.feign.KakaoFeignClient
import io.kotest.core.extensions.ApplyExtension
import io.kotest.core.spec.style.StringSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.comparables.shouldBeGreaterThan
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.openfeign.EnableFeignClients
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("test")
@ApplyExtension(SpringExtension::class)
@SpringBootTest(classes = [KakaoFeignClientTest.TestConfig::class])
class KakaoFeignClientTest: StringSpec() {

    @Autowired
    lateinit var client: KakaoFeignClient

    @EnableAutoConfiguration
    @ConfigurationPropertiesScan
    @EnableFeignClients
    class TestConfig

    init {
        "kakaoFeignClient 호출 성공" {
            val result = client.search("HTTP", null, 1, null, null)
            print(result)
//            result.total shouldBeGreaterThan  1
        }
    }
}