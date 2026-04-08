# Refresh Token Store Responsibility Refactor Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Split pure Redis persistence from resilience concerns so `AuthService` owns degraded-mode policy, `ResilientRefreshTokenStore` owns circuit breaker/metrics/unavailable conversion, and `RedisRefreshTokenStore` owns only Redis persistence.

**Architecture:** Keep the `RefreshTokenStore` interface as the seam. Make `RedisRefreshTokenStore` a raw persistence adapter that only talks to `StringRedisTemplate`. Add a new `ResilientRefreshTokenStore` wrapper as the injected primary bean; it will delegate to the raw store, record metrics, apply the annotation-based circuit breaker, and convert failures to `RefreshTokenStoreUnavailableException`.

**Tech Stack:** Spring Boot 4, Spring Data Redis, Micrometer, Resilience4j CircuitBreaker, Kotlin, Mockito, Testcontainers.

---

### Task 1: Add failing tests for wrapper-based layering

**Files:**
- Modify: `src/test/kotlin/com/refreshtoken/auth/infrastructure/RedisCircuitBreakerTest.kt`
- Create: `src/test/kotlin/com/refreshtoken/auth/infrastructure/ResilientRefreshTokenStoreTest.kt`

**Step 1: Write the failing test**

Add tests that require the resilient wrapper to short-circuit repeated failures and the raw Redis store to contain no circuit-breaker-specific behavior. One wrapper test should verify fast-fail after breaker open; one raw-store test should verify plain Redis exception propagation.

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.refreshtoken.auth.infrastructure.ResilientRefreshTokenStoreTest"`

**Step 3: Write minimal implementation**

No production code yet.

**Step 4: Run test to verify it passes**

Run after implementation is complete.

### Task 2: Extract pure Redis persistence store

**Files:**
- Modify: `src/main/kotlin/com/refreshtoken/auth/infrastructure/RedisRefreshTokenStore.kt`

**Step 1: Write the failing test**

Covered by Task 1 raw-store expectations.

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.refreshtoken.auth.infrastructure.ResilientRefreshTokenStoreTest"`

**Step 3: Write minimal implementation**

Remove metrics, circuit breaker annotations, and unavailable-store exception mapping from `RedisRefreshTokenStore`. It should become a straightforward `StringRedisTemplate` adapter.

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.refreshtoken.auth.infrastructure.ResilientRefreshTokenStoreTest"`

### Task 3: Add resilient wrapper bean

**Files:**
- Create: `src/main/kotlin/com/refreshtoken/auth/infrastructure/ResilientRefreshTokenStore.kt`

**Step 1: Write the failing test**

Covered by Task 1 wrapper expectations.

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.refreshtoken.auth.infrastructure.RedisCircuitBreakerTest"`

**Step 3: Write minimal implementation**

Create a primary wrapper bean implementing `RefreshTokenStore` that delegates to `RedisRefreshTokenStore`, keeps the existing annotation-based circuit breaker and metrics, and maps failures to `RefreshTokenStoreUnavailableException`.

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.refreshtoken.auth.infrastructure.RedisCircuitBreakerTest"`

### Task 4: Verify auth service behavior is unchanged

**Files:**
- Reuse existing auth tests

**Step 1: Run degraded/auth integration tests**

Run: `./gradlew test --tests "com.refreshtoken.auth.api.AuthDegradedModeIntegrationTest"`
Run: `./gradlew test --tests "com.refreshtoken.auth.api.AuthIntegrationTest"`

**Step 2: Adjust wiring only if needed**

Ensure `AuthService` still receives the resilient wrapper through the `RefreshTokenStore` interface with no policy changes.

### Task 5: Final verification and docs

**Files:**
- Modify: `README.md`

**Step 1: Run full test suite**

Run: `./gradlew test`

**Step 2: Run full build**

Run: `./gradlew build`

**Step 3: Update docs**

Document the responsibility split between pure Redis persistence, resilience wrapper, and service policy.
