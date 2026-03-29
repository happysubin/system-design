package com.library

import com.library.rest.NaverRestClient
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("test")
@SpringBootTest(classes = [NaverRestClientJunitTest.TestConfig::class])
class NaverRestClientJunitTest {

    @Autowired
    lateinit var naverClient: NaverRestClient

    @ConfigurationPropertiesScan
    @ComponentScan
    class TestConfig

    @Test
    fun naverClientTest() {
        val result = naverClient.search("subin")
        print(result)
    }
}