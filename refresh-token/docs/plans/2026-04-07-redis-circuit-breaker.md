# Redis Circuit Breaker Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a fast-fail circuit breaker around Redis refresh-token store operations so repeated Redis failures open the circuit and stop repeated slow downstream calls.

**Architecture:** Keep the existing timeout/degraded-mode behavior and insert a single programmatic circuit breaker at the `RedisRefreshTokenStore` boundary. The store will treat both downstream Redis exceptions and open-circuit rejections as `RefreshTokenStoreUnavailableException`, preserving the existing degraded login and refresh-unavailable behavior while reducing repeated Redis pressure during outage windows.

**Tech Stack:** Spring Boot 4, Spring Data Redis, Micrometer, Resilience4j CircuitBreaker, Kotlin, Mockito, Testcontainers.

---

### Task 1: Add failing circuit-breaker tests

**Files:**
- Create: `src/test/kotlin/com/refreshtoken/auth/infrastructure/RedisCircuitBreakerTest.kt`

**Step 1: Write the failing test**

Add a unit test that forces Redis write failures until the breaker opens, then verifies the next store call fails fast without invoking Redis again.

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.refreshtoken.auth.infrastructure.RedisCircuitBreakerTest"`

**Step 3: Write minimal implementation**

No production code yet.

**Step 4: Run test to verify it passes**

Run after implementation is complete.

### Task 2: Add Resilience4j and store-level breaker wiring

**Files:**
- Modify: `build.gradle.kts`
- Modify: `src/main/kotlin/com/refreshtoken/auth/infrastructure/RedisRefreshTokenStore.kt`
- Create or modify: `src/main/kotlin/com/refreshtoken/auth/config/AuthPropertiesConfig.kt`

**Step 1: Write the failing test**

Covered by Task 1.

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.refreshtoken.auth.infrastructure.RedisCircuitBreakerTest"`

**Step 3: Write minimal implementation**

Add Resilience4j circuit-breaker dependency, create a named breaker bean with a small explicit config, and wrap Redis store operations so open-circuit calls fail immediately.

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.refreshtoken.auth.infrastructure.RedisCircuitBreakerTest"`

### Task 3: Verify degraded auth still works with breaker in place

**Files:**
- Modify: `src/test/kotlin/com/refreshtoken/auth/api/AuthDegradedModeIntegrationTest.kt` (if needed)

**Step 1: Write the failing test**

Only if breaker integration changes degraded behavior unexpectedly.

**Step 2: Run targeted verification**

Run: `./gradlew test --tests "com.refreshtoken.auth.api.AuthDegradedModeIntegrationTest"`

**Step 3: Write minimal implementation**

Adjust only if circuit breaker integration affects existing degraded-mode semantics.

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.refreshtoken.auth.api.AuthDegradedModeIntegrationTest"`

### Task 4: Final verification and docs

**Files:**
- Modify: `README.md`

**Step 1: Run full test suite**

Run: `./gradlew test`

**Step 2: Run full build**

Run: `./gradlew build`

**Step 3: Update docs**

Document that circuit breaker is now enabled for Redis operations, and that retry is still intentionally deferred.
