package com.refreshtoken.auth.api

import com.refreshtoken.auth.application.AuthService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService,
) {
    @PostMapping("/login")
    fun login(@RequestBody request: LoginRequest): LoginResponse {
        val result = authService.login(request.username, request.password)

        return LoginResponse(
            accessToken = result.accessToken,
            refreshToken = result.refreshToken,
            tokenType = result.tokenType,
            accessTokenExpiresInSeconds = result.accessTokenExpiresInSeconds,
            refreshTokenExpiresInSeconds = result.refreshTokenExpiresInSeconds,
            sessionMode = result.sessionMode,
            degradedAuth = result.degradedAuth,
        )
    }

    @PostMapping("/refresh")
    fun refresh(@RequestBody request: RefreshTokenRequest): RefreshTokenResponse {
        val result = authService.refresh(request.refreshToken)

        return RefreshTokenResponse(
            accessToken = result.accessToken,
            refreshToken = result.refreshToken,
            tokenType = result.tokenType,
            accessTokenExpiresInSeconds = result.accessTokenExpiresInSeconds,
            refreshTokenExpiresInSeconds = result.refreshTokenExpiresInSeconds,
        )
    }

    @PostMapping("/logout")
    fun logout(@RequestBody request: LogoutRequest): ResponseEntity<Void> {
        authService.logout(request.refreshToken)
        return ResponseEntity.noContent().build()
    }
}
