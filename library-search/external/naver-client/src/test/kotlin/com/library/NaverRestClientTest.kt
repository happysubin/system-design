package com.library

import com.library.feign.NaverFeignClient
import com.library.feign.NaverFeignClientConfiguration
import com.library.rest.NaverRestClient
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.openfeign.EnableFeignClients
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("test")
@SpringBootTest(classes = [NaverRestClientTest.TestConfig::class])
class NaverRestClientTest {

    @Autowired
    lateinit var naverClient: NaverFeignClient

    @EnableAutoConfiguration
    @ConfigurationPropertiesScan
//    @EnableFeignClients(clients = [NaverFeignClient::class])
    @EnableFeignClients
    class TestConfig

    @Test
    fun naverClientTest() {
        val result = naverClient.search(
            "subin",
            1,
            1,
            "sim"
        )
        print(result)
    }
}