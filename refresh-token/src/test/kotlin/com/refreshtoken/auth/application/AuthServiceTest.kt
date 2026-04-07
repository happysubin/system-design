package com.refreshtoken.auth.application

import com.refreshtoken.auth.domain.DemoUserProperties
import com.refreshtoken.auth.domain.JwtTokenProvider
import com.refreshtoken.auth.domain.RefreshTokenStore
import com.refreshtoken.auth.domain.RefreshTokenStoreUnavailableException
import com.refreshtoken.auth.domain.TokenProperties
import com.refreshtoken.auth.domain.TokenType
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import org.junit.jupiter.api.Test

class AuthServiceTest {

    private val fixedNow = Instant.parse("2026-04-07T00:00:00Z")
    private val clock = Clock.fixed(fixedNow, ZoneOffset.UTC)
    private val tokenProperties = TokenProperties(
        secret = "01234567890123456789012345678901",
        issuer = "refresh-token-app",
        accessTokenTtl = Duration.ofMinutes(15),
        refreshTokenTtl = Duration.ofDays(14),
    )
    private val demoUserProperties = DemoUserProperties(
        username = "demo",
        password = "password",
    )

    @Test
    fun `login issues tokens and saves refresh token`() {
        val refreshTokenStore = InMemoryRefreshTokenStore()
        val service = AuthService(
            demoUserProperties = demoUserProperties,
            jwtTokenProvider = JwtTokenProvider(tokenProperties, clock),
            refreshTokenStore = refreshTokenStore,
            tokenProperties = tokenProperties,
        )

        val result = service.login("demo", "password")

        val refreshToken = assertNotNull(result.refreshToken)
        val refreshClaims = JwtTokenProvider(tokenProperties, clock).parse(refreshToken)
        assertEquals(TokenType.REFRESH, refreshClaims.tokenType)
        assertEquals(true, refreshTokenStore.exists(refreshToken))
        assertEquals(900, result.accessTokenExpiresInSeconds)
        assertEquals(Duration.ofDays(14).seconds, result.refreshTokenExpiresInSeconds)
    }

    @Test
    fun `login fails for wrong credentials`() {
        val service = AuthService(
            demoUserProperties = demoUserProperties,
            jwtTokenProvider = JwtTokenProvider(tokenProperties, clock),
            refreshTokenStore = InMemoryRefreshTokenStore(),
            tokenProperties = tokenProperties,
        )

        assertFailsWith<InvalidCredentialsException> {
            service.login("demo", "wrong")
        }
    }

    @Test
    fun `login degrades to access only when refresh store is unavailable`() {
        val service = AuthService(
            demoUserProperties = demoUserProperties,
            jwtTokenProvider = JwtTokenProvider(tokenProperties, clock),
            refreshTokenStore = UnavailableRefreshTokenStore(),
            tokenProperties = tokenProperties,
        )

        val result = service.login("demo", "password")

        assertEquals("access_only", result.sessionMode)
        assertEquals(true, result.degradedAuth)
        assertEquals(null, result.refreshToken)
        assertEquals(null, result.refreshTokenExpiresInSeconds)
        assertEquals(900, result.accessTokenExpiresInSeconds)
    }

    @Test
    fun `refresh rotates refresh token and returns a new token pair`() {
        val refreshTokenStore = InMemoryRefreshTokenStore()
        val provider = JwtTokenProvider(tokenProperties, clock)
        val service = AuthService(
            demoUserProperties = demoUserProperties,
            jwtTokenProvider = provider,
            refreshTokenStore = refreshTokenStore,
            tokenProperties = tokenProperties,
        )
        val refreshToken = provider.createRefreshToken("demo")
        refreshTokenStore.save(refreshToken, "demo", tokenProperties.refreshTokenTtl)

        val result = service.refresh(refreshToken)

        val claims = provider.parse(result.accessToken)
        assertEquals("demo", claims.subject)
        assertEquals(TokenType.ACCESS, claims.tokenType)
        assertEquals(900, result.accessTokenExpiresInSeconds)
        assertEquals(Duration.ofDays(14).seconds, result.refreshTokenExpiresInSeconds)
        kotlin.test.assertNotEquals(refreshToken, result.refreshToken)
        assertEquals(false, refreshTokenStore.exists(refreshToken))
        assertEquals(true, refreshTokenStore.exists(result.refreshToken))
    }

    @Test
    fun `refresh fails when refresh token is not stored`() {
        val service = AuthService(
            demoUserProperties = demoUserProperties,
            jwtTokenProvider = JwtTokenProvider(tokenProperties, clock),
            refreshTokenStore = InMemoryRefreshTokenStore(),
            tokenProperties = tokenProperties,
        )

        val refreshToken = JwtTokenProvider(tokenProperties, clock).createRefreshToken("demo")

        assertFailsWith<InvalidRefreshTokenException> {
            service.refresh(refreshToken)
        }
    }

    @Test
    fun `refresh fails with dedicated exception when refresh store is unavailable`() {
        val service = AuthService(
            demoUserProperties = demoUserProperties,
            jwtTokenProvider = JwtTokenProvider(tokenProperties, clock),
            refreshTokenStore = UnavailableRefreshTokenStore(),
            tokenProperties = tokenProperties,
        )
        val refreshToken = JwtTokenProvider(tokenProperties, clock).createRefreshToken("demo")

        assertFailsWith<RefreshUnavailableException> {
            service.refresh(refreshToken)
        }
    }

    @Test
    fun `logout removes refresh token from store`() {
        val refreshTokenStore = InMemoryRefreshTokenStore()
        val provider = JwtTokenProvider(tokenProperties, clock)
        val service = AuthService(
            demoUserProperties = demoUserProperties,
            jwtTokenProvider = provider,
            refreshTokenStore = refreshTokenStore,
            tokenProperties = tokenProperties,
        )
        val refreshToken = provider.createRefreshToken("demo")
        refreshTokenStore.save(refreshToken, "demo", tokenProperties.refreshTokenTtl)

        service.logout(refreshToken)

        assertEquals(false, refreshTokenStore.exists(refreshToken))
    }
}

private class InMemoryRefreshTokenStore : RefreshTokenStore {
    private val tokens = ConcurrentHashMap<String, String>()

    override fun save(refreshToken: String, subject: String, ttl: Duration) {
        tokens[refreshToken] = subject
    }

    override fun exists(refreshToken: String): Boolean = tokens.containsKey(refreshToken)

    override fun delete(refreshToken: String) {
        tokens.remove(refreshToken)
    }
}

private class UnavailableRefreshTokenStore : RefreshTokenStore {
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
