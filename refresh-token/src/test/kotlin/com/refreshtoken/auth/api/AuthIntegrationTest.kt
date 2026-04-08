package com.refreshtoken.auth.api

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import tools.jackson.databind.ObjectMapper
import com.refreshtoken.auth.infrastructure.RedisKeyNamespace

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class AuthIntegrationTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired private val redisTemplate: StringRedisTemplate,
    @Autowired private val redisKeyNamespace: RedisKeyNamespace,
) {

    @Test
    fun `actuator health exposes redis dependency status`() {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.components.redis.status").value("UP"))
    }

    @Test
    fun `actuator metrics exposes redis auth metric after login`() {
        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(LoginRequest("demo", "password"))),
        )
            .andExpect(status().isOk)

        mockMvc.perform(get("/actuator/metrics"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.names[?(@ == 'auth.redis.operations')]").exists())
    }

    @Test
    fun `login refresh logout and refresh failure flow works with redis`() {
        val loginResponse = mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(LoginRequest("demo", "password"))),
        )
            .andExpect(status().isOk)
            .andReturn()
            .response
            .contentAsString

        val refreshToken = objectMapper.readTree(loginResponse).get("refreshToken").asText()

        val originalRedisKey = redisKeyNamespace.refreshTokenKey(refreshToken)
        kotlin.test.assertEquals(true, redisTemplate.hasKey(originalRedisKey))

        val refreshResponse = mockMvc.perform(
            post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(RefreshTokenRequest(refreshToken))),
        )
            .andExpect(status().isOk)
            .andReturn()
            .response
            .contentAsString

        val rotatedRefreshToken = objectMapper.readTree(refreshResponse).get("refreshToken").asText()
        val rotatedRedisKey = redisKeyNamespace.refreshTokenKey(rotatedRefreshToken)

        kotlin.test.assertNotEquals(refreshToken, rotatedRefreshToken)
        kotlin.test.assertEquals(false, redisTemplate.hasKey(originalRedisKey))
        kotlin.test.assertEquals(true, redisTemplate.hasKey(rotatedRedisKey))

        mockMvc.perform(
            post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(RefreshTokenRequest(refreshToken))),
        )
            .andExpect(status().isUnauthorized)

        mockMvc.perform(
            post("/api/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(LogoutRequest(rotatedRefreshToken))),
        )
            .andExpect(status().isNoContent)

        kotlin.test.assertEquals(false, redisTemplate.hasKey(rotatedRedisKey))

        mockMvc.perform(
            post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(RefreshTokenRequest(rotatedRefreshToken))),
        )
            .andExpect(status().isUnauthorized)
    }

    companion object {
        @Container
        @JvmStatic
        private val redis = GenericContainer<Nothing>("redis:7.2-alpine").apply {
            withExposedPorts(6379)
        }

        @DynamicPropertySource
        @JvmStatic
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.data.redis.host") { redis.host }
            registry.add("spring.data.redis.port") { redis.getMappedPort(6379) }
        }
    }
}
