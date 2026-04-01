package com.library

import com.library.feign.NaverFeignClient
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.openfeign.EnableFeignClients
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("test")
@SpringBootTest(classes = [NaverFeignClientJunitTest.TestConfig::class])
class NaverFeignClientJunitTest {

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