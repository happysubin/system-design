package com.refreshtoken.auth.infrastructure

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class RedisKeyNamespaceTest {

    @Test
    fun `refresh token key includes env app domain version and hashed token`() {
        val namespace = RedisKeyNamespace(
            properties = RedisKeyProperties(
                environment = "prod",
                appName = "refresh-token",
            ),
        )

        val key = namespace.refreshTokenKey("refresh-token-value")

        assertEquals(
            "prod:refresh-token:auth:refresh-token:v1:e65009f6e0ae9fc204adcb73a208f63c582b7839bde7fe836da36a3df6ae7a85",
            key,
        )
    }
}
