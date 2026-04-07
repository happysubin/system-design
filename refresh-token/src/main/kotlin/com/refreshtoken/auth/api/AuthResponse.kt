package com.refreshtoken.auth.api

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class LoginResponse(
    val accessToken: String,
    val refreshToken: String?,
    val tokenType: String,
    val accessTokenExpiresInSeconds: Long,
    val refreshTokenExpiresInSeconds: Long?,
    val sessionMode: String,
    val degradedAuth: Boolean,
)

data class RefreshTokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String,
    val accessTokenExpiresInSeconds: Long,
    val refreshTokenExpiresInSeconds: Long,
)

data class ErrorResponse(
    val message: String,
)
