package com.refreshtoken.auth.application

import com.refreshtoken.auth.domain.DemoUserProperties
import com.refreshtoken.auth.domain.InvalidTokenException
import com.refreshtoken.auth.domain.JwtTokenProvider
import com.refreshtoken.auth.domain.RefreshTokenStore
import com.refreshtoken.auth.domain.RefreshTokenStoreUnavailableException
import com.refreshtoken.auth.domain.TokenProperties
import com.refreshtoken.auth.domain.TokenType
import org.springframework.stereotype.Service

data class LoginResult(
    val accessToken: String,
    val refreshToken: String?,
    val tokenType: String,
    val accessTokenExpiresInSeconds: Long,
    val refreshTokenExpiresInSeconds: Long?,
    val sessionMode: String,
    val degradedAuth: Boolean,
)

data class TokenPairResult(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String,
    val accessTokenExpiresInSeconds: Long,
    val refreshTokenExpiresInSeconds: Long,
)

class InvalidCredentialsException : RuntimeException("Invalid credentials")

class InvalidRefreshTokenException : RuntimeException("Invalid refresh token")

class RefreshUnavailableException : RuntimeException("Session renewal unavailable. Please sign in again.")

@Service
class AuthService(
    private val demoUserProperties: DemoUserProperties,
    private val jwtTokenProvider: JwtTokenProvider,
    private val refreshTokenStore: RefreshTokenStore,
    private val tokenProperties: TokenProperties,
) {
    fun login(username: String, password: String): LoginResult {
        if (username != demoUserProperties.username || password != demoUserProperties.password) {
            throw InvalidCredentialsException()
        }

        val accessToken = jwtTokenProvider.createAccessToken(username)
        val refreshToken = jwtTokenProvider.createRefreshToken(username)

        return try {
            refreshTokenStore.save(refreshToken, username, tokenProperties.refreshTokenTtl)

            LoginResult(
                accessToken = accessToken,
                refreshToken = refreshToken,
                tokenType = "Bearer",
                accessTokenExpiresInSeconds = tokenProperties.accessTokenTtl.seconds,
                refreshTokenExpiresInSeconds = tokenProperties.refreshTokenTtl.seconds,
                sessionMode = "normal",
                degradedAuth = false,
            )
        } catch (_: RefreshTokenStoreUnavailableException) {
            LoginResult(
                accessToken = accessToken,
                refreshToken = null,
                tokenType = "Bearer",
                accessTokenExpiresInSeconds = tokenProperties.accessTokenTtl.seconds,
                refreshTokenExpiresInSeconds = null,
                sessionMode = "access_only",
                degradedAuth = true,
            )
        }
    }

    fun refresh(refreshToken: String): TokenPairResult {
        val claims = try {
            jwtTokenProvider.parse(refreshToken)
        } catch (_: InvalidTokenException) {
            throw InvalidRefreshTokenException()
        }

        val exists = try {
            refreshTokenStore.exists(refreshToken)
        } catch (_: RefreshTokenStoreUnavailableException) {
            throw RefreshUnavailableException()
        }

        if (claims.tokenType != TokenType.REFRESH || !exists) {
            throw InvalidRefreshTokenException()
        }

        try {
            refreshTokenStore.delete(refreshToken)

            val newAccessToken = jwtTokenProvider.createAccessToken(claims.subject)
            val newRefreshToken = jwtTokenProvider.createRefreshToken(claims.subject)
            refreshTokenStore.save(newRefreshToken, claims.subject, tokenProperties.refreshTokenTtl)

            return TokenPairResult(
                accessToken = newAccessToken,
                refreshToken = newRefreshToken,
                tokenType = "Bearer",
                accessTokenExpiresInSeconds = tokenProperties.accessTokenTtl.seconds,
                refreshTokenExpiresInSeconds = tokenProperties.refreshTokenTtl.seconds,
            )
        } catch (_: RefreshTokenStoreUnavailableException) {
            throw RefreshUnavailableException()
        }
    }

    fun logout(refreshToken: String) {
        refreshTokenStore.delete(refreshToken)
    }
}
