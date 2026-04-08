package com.refreshtoken.auth.api

import com.refreshtoken.auth.domain.JwtTokenProvider
import com.refreshtoken.auth.domain.RefreshTokenStoreUnavailableException
import java.time.Duration
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.doAnswer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
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

    @MockitoBean
    private lateinit var redisTemplate: StringRedisTemplate

    @Test
    fun `login succeeds with access token only when refresh store is unavailable`() {
        val valueOperations = org.mockito.Mockito.mock(ValueOperations::class.java) as ValueOperations<String, String>
        given(redisTemplate.opsForValue()).willReturn(valueOperations)
        doAnswer { throw RuntimeException("redis down") }
            .`when`(valueOperations)
            .set(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(Duration.ofDays(14)))

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
        given(redisTemplate.hasKey(org.mockito.ArgumentMatchers.anyString())).willAnswer {
            throw RuntimeException("redis down")
        }

        val refreshToken = jwtTokenProvider.createRefreshToken("demo")

        mockMvc.perform(
            post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(RefreshTokenRequest(refreshToken))),
        )
            .andExpect(status().isServiceUnavailable)
            .andExpect(jsonPath("$.message").value("Session renewal unavailable. Please sign in again."))
    }
}
