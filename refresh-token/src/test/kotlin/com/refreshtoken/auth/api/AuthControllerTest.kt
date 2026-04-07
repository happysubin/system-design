package com.refreshtoken.auth.api

import com.refreshtoken.auth.application.AuthService
import com.refreshtoken.auth.application.InvalidCredentialsException
import com.refreshtoken.auth.application.InvalidRefreshTokenException
import com.refreshtoken.auth.application.LoginResult
import com.refreshtoken.auth.application.RefreshUnavailableException
import com.refreshtoken.auth.application.TokenPairResult
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.doNothing
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper

@WebMvcTest(AuthController::class)
@Import(AuthExceptionHandler::class)
class AuthControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val objectMapper: ObjectMapper,
) {

    @MockitoBean
    private lateinit var authService: AuthService

    @Test
    fun `login returns tokens`() {
        given(authService.login("demo", "password")).willReturn(
            LoginResult(
                accessToken = "access-token",
                refreshToken = "refresh-token",
                tokenType = "Bearer",
                accessTokenExpiresInSeconds = 900,
                refreshTokenExpiresInSeconds = 1_209_600,
                sessionMode = "normal",
                degradedAuth = false,
            ),
        )

        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(LoginRequest("demo", "password"))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").value("access-token"))
            .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
            .andExpect(jsonPath("$.sessionMode").value("normal"))
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
    }

    @Test
    fun `login returns access only in degraded mode`() {
        given(authService.login("demo", "password")).willReturn(
            LoginResult(
                accessToken = "access-token",
                refreshToken = null,
                tokenType = "Bearer",
                accessTokenExpiresInSeconds = 900,
                refreshTokenExpiresInSeconds = null,
                sessionMode = "access_only",
                degradedAuth = true,
            ),
        )

        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(LoginRequest("demo", "password"))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").value("access-token"))
            .andExpect(jsonPath("$.refreshToken").doesNotExist())
            .andExpect(jsonPath("$.sessionMode").value("access_only"))
            .andExpect(jsonPath("$.degradedAuth").value(true))
    }

    @Test
    fun `login returns unauthorized for wrong credentials`() {
        given(authService.login("demo", "wrong")).willThrow(InvalidCredentialsException())

        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(LoginRequest("demo", "wrong"))),
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `refresh returns a new token pair`() {
        given(authService.refresh("refresh-token")).willReturn(
            TokenPairResult(
                accessToken = "new-access-token",
                refreshToken = "new-refresh-token",
                tokenType = "Bearer",
                accessTokenExpiresInSeconds = 900,
                refreshTokenExpiresInSeconds = 1_209_600,
            ),
        )

        mockMvc.perform(
            post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(RefreshTokenRequest("refresh-token"))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").value("new-access-token"))
            .andExpect(jsonPath("$.refreshToken").value("new-refresh-token"))
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
    }

    @Test
    fun `refresh returns service unavailable when refresh store is unavailable`() {
        given(authService.refresh("refresh-token")).willThrow(RefreshUnavailableException())

        mockMvc.perform(
            post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(RefreshTokenRequest("refresh-token"))),
        )
            .andExpect(status().isServiceUnavailable)
            .andExpect(jsonPath("$.message").value("Session renewal unavailable. Please sign in again."))
    }

    @Test
    fun `refresh returns unauthorized for invalid token`() {
        given(authService.refresh("invalid-token")).willThrow(InvalidRefreshTokenException())

        mockMvc.perform(
            post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(RefreshTokenRequest("invalid-token"))),
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `logout returns no content`() {
        doNothing().`when`(authService).logout("refresh-token")

        mockMvc.perform(
            post("/api/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(LogoutRequest("refresh-token"))),
        )
            .andExpect(status().isNoContent)
    }
}
