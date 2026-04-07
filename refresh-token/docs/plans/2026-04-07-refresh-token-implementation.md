# Refresh Token Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build login, token refresh, and logout APIs with 15-minute access tokens, 14-day refresh tokens, stateless access-token validation, and Redis-backed refresh-token state.

**Architecture:** Keep the app intentionally small. Use a config-backed demo user instead of introducing a member database, generate JWTs in a dedicated provider, and persist only refresh-token state in Redis with TTL. Expose three MVC endpoints and verify behavior with unit, controller, and Redis-backed integration tests.

**Tech Stack:** Spring Boot Web MVC, Spring Data Redis, Kotlin, JJWT, MockMvc, Testcontainers.

---

### Task 1: Add dependencies and runtime configuration

**Files:**
- Modify: `build.gradle.kts`
- Create: `src/main/resources/application.yaml`
- Create: `src/test/resources/application-test.yaml`

**Step 1: Write the failing test**

Use a context-load test that expects auth properties to bind once configuration exists.

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.refreshtoken.RefreshTokenApplicationTests"`

**Step 3: Write minimal implementation**

Add JJWT and testcontainer dependencies, then add application properties for JWT secret, token TTLs, demo credentials, and Redis connection.

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.refreshtoken.RefreshTokenApplicationTests"`

### Task 2: Implement JWT provider with TDD

**Files:**
- Create: `src/main/kotlin/com/refreshtoken/auth/domain/TokenProperties.kt`
- Create: `src/main/kotlin/com/refreshtoken/auth/domain/JwtTokenProvider.kt`
- Test: `src/test/kotlin/com/refreshtoken/auth/domain/JwtTokenProviderTest.kt`

**Step 1: Write the failing test**

Cover access token issuance, refresh token issuance, claim extraction, and invalid token rejection.

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.refreshtoken.auth.domain.JwtTokenProviderTest"`

**Step 3: Write minimal implementation**

Implement token creation and parsing with subject, token type, and expiration.

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.refreshtoken.auth.domain.JwtTokenProviderTest"`

### Task 3: Implement auth service and refresh-token store with TDD

**Files:**
- Create: `src/main/kotlin/com/refreshtoken/auth/domain/DemoUserProperties.kt`
- Create: `src/main/kotlin/com/refreshtoken/auth/domain/RefreshTokenStore.kt`
- Create: `src/main/kotlin/com/refreshtoken/auth/infrastructure/RedisRefreshTokenStore.kt`
- Create: `src/main/kotlin/com/refreshtoken/auth/application/AuthService.kt`
- Test: `src/test/kotlin/com/refreshtoken/auth/application/AuthServiceTest.kt`

**Step 1: Write the failing test**

Cover login success, login failure, refresh success only when token exists in the store, and logout deletion.

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.refreshtoken.auth.application.AuthServiceTest"`

**Step 3: Write minimal implementation**

Implement config-backed credential validation, token issuance, Redis store save/load/delete, and simple exceptions.

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.refreshtoken.auth.application.AuthServiceTest"`

### Task 4: Add controller endpoints with TDD

**Files:**
- Create: `src/main/kotlin/com/refreshtoken/auth/api/AuthRequest.kt`
- Create: `src/main/kotlin/com/refreshtoken/auth/api/AuthResponse.kt`
- Create: `src/main/kotlin/com/refreshtoken/auth/api/AuthController.kt`
- Create: `src/main/kotlin/com/refreshtoken/auth/api/AuthExceptionHandler.kt`
- Create: `src/main/kotlin/com/refreshtoken/auth/config/AuthPropertiesConfig.kt`
- Test: `src/test/kotlin/com/refreshtoken/auth/api/AuthControllerTest.kt`

**Step 1: Write the failing test**

Use MockMvc to cover `POST /api/auth/login`, `POST /api/auth/refresh`, and `POST /api/auth/logout` response contracts.

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.refreshtoken.auth.api.AuthControllerTest"`

**Step 3: Write minimal implementation**

Add DTOs, controller mappings, and exception translation to HTTP status codes.

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.refreshtoken.auth.api.AuthControllerTest"`

### Task 5: Verify Redis integration end-to-end

**Files:**
- Test: `src/test/kotlin/com/refreshtoken/auth/api/AuthIntegrationTest.kt`

**Step 1: Write the failing test**

Start Redis with Testcontainers and verify login → refresh → logout → refresh fails.

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.refreshtoken.auth.api.AuthIntegrationTest"`

**Step 3: Write minimal implementation**

Adjust configuration or serialization only as needed to pass the integration flow.

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.refreshtoken.auth.api.AuthIntegrationTest"`

### Task 6: Final verification

**Files:**
- Verify all modified files

**Step 1: Run focused diagnostics**

Run language diagnostics on changed Kotlin files.

**Step 2: Run full test suite**

Run: `./gradlew test`

**Step 3: Run full build**

Run: `./gradlew build`

**Step 4: Update documentation if needed**

Reflect any implementation-specific assumptions in `README.md`.
