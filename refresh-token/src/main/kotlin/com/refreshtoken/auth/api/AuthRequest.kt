package com.refreshtoken.auth.api

data class LoginRequest(
    val username: String,
    val password: String,
)

data class RefreshTokenRequest(
    val refreshToken: String,
)

data class LogoutRequest(
    val refreshToken: String,
)
