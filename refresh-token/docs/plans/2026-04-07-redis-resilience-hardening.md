# Redis Resilience Hardening Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Harden Redis-backed auth behavior with fast failure and visibility by adding Redis timeouts, Actuator health exposure, and minimal Redis operation metrics.

**Architecture:** Keep the current degraded-login behavior intact and improve the system around it. Use Spring Boot Redis timeout properties to cap latency, enable Actuator so Redis health is visible as a dependency signal, and instrument the Redis token store with lightweight Micrometer metrics for latency and failure counts. Defer retry and circuit breaker to a later phase until timeout/health/metrics data exists.

**Tech Stack:** Spring Boot 4, Spring Data Redis, Spring Boot Actuator, Micrometer, Kotlin, MockMvc, Testcontainers.

---

### Task 1: Add failing tests for health and metrics surface

**Files:**
- Modify: `src/test/kotlin/com/refreshtoken/auth/api/AuthIntegrationTest.kt`
- Create: `src/test/kotlin/com/refreshtoken/auth/infrastructure/RedisMetricsTest.kt`

**Step 1: Write the failing test**

Add one integration test that expects `/actuator/health` to expose Redis dependency state when Redis is available. Add one unit/integration test that expects Redis store operations to publish Micrometer metrics.

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.refreshtoken.auth.infrastructure.RedisMetricsTest"`

**Step 3: Write minimal implementation**

No production code yet.

**Step 4: Run test to verify it passes**

Run after implementation is complete.

### Task 2: Add timeout and actuator dependencies/configuration

**Files:**
- Modify: `build.gradle.kts`
- Modify: `src/main/resources/application.yaml`
- Modify: `src/test/resources/application-test.yaml`

**Step 1: Write the failing test**

Covered by Task 1 health endpoint expectations.

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.refreshtoken.auth.api.AuthIntegrationTest"`

**Step 3: Write minimal implementation**

Add Spring Boot Actuator dependency. Configure Redis connect/command timeouts and expose health endpoints with Redis details appropriate for local/test.

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.refreshtoken.auth.api.AuthIntegrationTest"`

### Task 3: Instrument Redis store metrics

**Files:**
- Modify: `src/main/kotlin/com/refreshtoken/auth/infrastructure/RedisRefreshTokenStore.kt`
- Test: `src/test/kotlin/com/refreshtoken/auth/infrastructure/RedisMetricsTest.kt`

**Step 1: Write the failing test**

Covered by Task 1 metrics test.

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.refreshtoken.auth.infrastructure.RedisMetricsTest"`

**Step 3: Write minimal implementation**

Inject `MeterRegistry` and record Redis operation timers/counters for success/failure using stable metric names and low-cardinality tags.

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.refreshtoken.auth.infrastructure.RedisMetricsTest"`

### Task 4: Final verification and docs

**Files:**
- Modify: `README.md`

**Step 1: Run full test suite**

Run: `./gradlew test`

**Step 2: Run full build**

Run: `./gradlew build`

**Step 3: Update docs**

Document timeout settings, actuator health exposure, and Redis metrics. Explicitly note that retry/circuit breaker are deferred to a later phase.
