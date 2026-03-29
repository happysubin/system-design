package com.firstcomecoupon.support

import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

@Testcontainers
abstract class AbstractPostgresApiTest {

    companion object {
        private val postgresContainer: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:17")
            .withDatabaseName("first_come_coupon_test")
            .withUsername("test")
            .withPassword("test")

        private val redisContainer: GenericContainer<*> = GenericContainer(DockerImageName.parse("redis:7.4"))
            .withExposedPorts(6379)

        init {
            postgresContainer.start()
            redisContainer.start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgresContainer.jdbcUrl }
            registry.add("spring.datasource.username") { postgresContainer.username }
            registry.add("spring.datasource.password") { postgresContainer.password }
            registry.add("spring.datasource.driver-class-name") { postgresContainer.driverClassName }
            registry.add("spring.data.redis.host") { redisContainer.host }
            registry.add("spring.data.redis.port") { redisContainer.getMappedPort(6379) }
        }
    }
}
