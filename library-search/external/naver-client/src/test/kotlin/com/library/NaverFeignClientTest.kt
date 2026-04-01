package com.library


import com.library.feign.NaverFeignClient
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
@SpringBootTest(classes = [NaverFeignClientTest.TestConfig::class])
class NaverFeignClientTest: StringSpec() {
    @Autowired
    lateinit var client: NaverFeignClient

    @EnableAutoConfiguration
    @ConfigurationPropertiesScan
    @EnableFeignClients
    class TestConfig

    init {
        "naverFeignClient 호출 성공" {
            val result = client.search("HTTP", 1, 1, "sim")
            result.total shouldBeGreaterThan  1
        }
    }
}