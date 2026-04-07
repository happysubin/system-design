package com.refreshtoken.auth.domain

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import org.junit.jupiter.api.Test

class JwtTokenProviderTest {

    private val fixedNow = Instant.parse("2026-04-07T00:00:00Z")
    private val clock = Clock.fixed(fixedNow, ZoneOffset.UTC)
    private val tokenProperties = TokenProperties(
        secret = "01234567890123456789012345678901",
        issuer = "refresh-token-app",
        accessTokenTtl = Duration.ofMinutes(15),
        refreshTokenTtl = Duration.ofDays(14),
    )

    @Test
    fun `create access token includes subject and access expiration`() {
        val provider = JwtTokenProvider(tokenProperties, clock)

        val token = provider.createAccessToken("demo-user")

        val claims = provider.parse(token)

        assertEquals("demo-user", claims.subject)
        assertEquals(TokenType.ACCESS, claims.tokenType)
        assertEquals(fixedNow.plus(Duration.ofMinutes(15)), claims.expiresAt)
    }

    @Test
    fun `create refresh token includes subject and refresh expiration`() {
        val provider = JwtTokenProvider(tokenProperties, clock)

        val token = provider.createRefreshToken("demo-user")

        val claims = provider.parse(token)

        assertEquals("demo-user", claims.subject)
        assertEquals(TokenType.REFRESH, claims.tokenType)
        assertEquals(fixedNow.plus(Duration.ofDays(14)), claims.expiresAt)
    }

    @Test
    fun `create refresh token generates distinct values for rotation`() {
        val provider = JwtTokenProvider(tokenProperties, clock)

        val first = provider.createRefreshToken("demo-user")
        val second = provider.createRefreshToken("demo-user")

        kotlin.test.assertNotEquals(first, second)
    }

    @Test
    fun `parse rejects malformed token`() {
        val provider = JwtTokenProvider(tokenProperties, clock)

        assertFailsWith<InvalidTokenException> {
            provider.parse("not-a-token")
        }
    }
}
