package com.refreshtoken.auth.api

import com.refreshtoken.auth.application.RefreshUnavailableException
import com.refreshtoken.auth.domain.JwtTokenProvider
import com.refreshtoken.auth.domain.RefreshTokenStore
import com.refreshtoken.auth.domain.RefreshTokenStoreUnavailableException
import java.time.Duration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthDegradedModeIntegrationTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired private val jwtTokenProvider: JwtTokenProvider,
) {

    @Test
    fun `login succeeds with access token only when refresh store is unavailable`() {
        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(LoginRequest("demo", "password"))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").isString)
            .andExpect(jsonPath("$.refreshToken").doesNotExist())
            .andExpect(jsonPath("$.sessionMode").value("access_only"))
            .andExpect(jsonPath("$.degradedAuth").value(true))
    }

    @Test
    fun `refresh fails with service unavailable when refresh store is unavailable`() {
        val refreshToken = jwtTokenProvider.createRefreshToken("demo")

        mockMvc.perform(
            post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(RefreshTokenRequest(refreshToken))),
        )
            .andExpect(status().isServiceUnavailable)
            .andExpect(jsonPath("$.message").value("Session renewal unavailable. Please sign in again."))
    }

    @TestConfiguration
    class FailingRefreshStoreConfig {
        @Bean
        @Primary
        fun failingRefreshTokenStore(): RefreshTokenStore = object : RefreshTokenStore {
            override fun save(refreshToken: String, subject: String, ttl: Duration) {
                throw RefreshTokenStoreUnavailableException()
            }

            override fun exists(refreshToken: String): Boolean {
                throw RefreshTokenStoreUnavailableException()
            }

            override fun delete(refreshToken: String) {
                throw RefreshTokenStoreUnavailableException()
            }
        }
    }
}
