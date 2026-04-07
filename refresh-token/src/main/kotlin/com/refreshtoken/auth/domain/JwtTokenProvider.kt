package com.refreshtoken.auth.domain

import io.jsonwebtoken.Claims
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Instant
import java.util.UUID
import org.springframework.stereotype.Component

enum class TokenType {
    ACCESS,
    REFRESH,
}

data class TokenClaims(
    val subject: String,
    val tokenType: TokenType,
    val expiresAt: Instant,
)

class InvalidTokenException : RuntimeException("Invalid token")

@Component
class JwtTokenProvider(
    private val tokenProperties: TokenProperties,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val signingKey = Keys.hmacShaKeyFor(tokenProperties.secret.toByteArray(StandardCharsets.UTF_8))

    fun createAccessToken(subject: String): String = createToken(subject, TokenType.ACCESS, tokenProperties.accessTokenTtl)

    fun createRefreshToken(subject: String): String = createToken(subject, TokenType.REFRESH, tokenProperties.refreshTokenTtl)

    fun parse(token: String): TokenClaims {
        val claims = try {
            Jwts.parser()
                .clock { java.util.Date.from(clock.instant()) }
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .payload
        } catch (_: JwtException) {
            throw InvalidTokenException()
        } catch (_: IllegalArgumentException) {
            throw InvalidTokenException()
        }

        return TokenClaims(
            subject = claims.subject,
            tokenType = claims.readTokenType(),
            expiresAt = claims.expiration.toInstant(),
        )
    }

    private fun createToken(subject: String, tokenType: TokenType, ttl: java.time.Duration): String {
        val issuedAt = clock.instant()
        val expiresAt = issuedAt.plus(ttl)

        return Jwts.builder()
            .subject(subject)
            .issuer(tokenProperties.issuer)
            .id(UUID.randomUUID().toString())
            .issuedAt(java.util.Date.from(issuedAt))
            .expiration(java.util.Date.from(expiresAt))
            .claim("tokenType", tokenType.name)
            .signWith(signingKey)
            .compact()
    }

    private fun Claims.readTokenType(): TokenType =
        try {
            TokenType.valueOf(this["tokenType", String::class.java])
        } catch (_: Exception) {
            throw InvalidTokenException()
        }
}
