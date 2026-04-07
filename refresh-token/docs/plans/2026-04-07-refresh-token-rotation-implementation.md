# Refresh Token Rotation Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Change the refresh endpoint so it rotates refresh tokens: issue a new access token and a new refresh token, store the new refresh token, and invalidate the previous refresh token immediately.

**Architecture:** Keep the current Redis-backed model and extend it minimally. The refresh service flow should validate the incoming refresh token, delete the old Redis entry, generate a new token pair, save the new refresh token, and return both tokens. JWT generation needs a per-token unique identifier so rotated refresh tokens are distinct even when created close together.

**Tech Stack:** Spring Boot Web MVC, Spring Data Redis, Kotlin, JJWT, MockMvc, Testcontainers.

---

### Task 1: Add failing tests for rotation behavior

**Files:**
- Modify: `src/test/kotlin/com/refreshtoken/auth/domain/JwtTokenProviderTest.kt`
- Modify: `src/test/kotlin/com/refreshtoken/auth/application/AuthServiceTest.kt`
- Modify: `src/test/kotlin/com/refreshtoken/auth/api/AuthControllerTest.kt`
- Modify: `src/test/kotlin/com/refreshtoken/auth/api/AuthIntegrationTest.kt`

**Step 1: Write the failing test**

Add tests that require two refresh tokens minted at the same time to still differ, require the refresh endpoint to return a new refresh token, and require the previous refresh token to stop working after rotation.

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.refreshtoken.auth.application.AuthServiceTest"`

**Step 3: Write minimal implementation**

No production code yet.

**Step 4: Run test to verify it passes**

Run after implementation is complete.

### Task 2: Implement token uniqueness and rotation service flow

**Files:**
- Modify: `src/main/kotlin/com/refreshtoken/auth/domain/JwtTokenProvider.kt`
- Modify: `src/main/kotlin/com/refreshtoken/auth/application/AuthService.kt`

**Step 1: Write the failing test**

Covered in Task 1.

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.refreshtoken.auth.application.AuthServiceTest"`

**Step 3: Write minimal implementation**

Add a unique JWT ID claim for newly created tokens. Change refresh logic to delete the old refresh token, create a new access token and refresh token, persist the new refresh token, and return both.

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.refreshtoken.auth.application.AuthServiceTest"`

### Task 3: Update HTTP response contract for refresh

**Files:**
- Modify: `src/main/kotlin/com/refreshtoken/auth/api/AuthResponse.kt`
- Modify: `src/main/kotlin/com/refreshtoken/auth/api/AuthController.kt`

**Step 1: Write the failing test**

Covered in Task 1 controller tests.

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.refreshtoken.auth.api.AuthControllerTest"`

**Step 3: Write minimal implementation**

Make refresh responses include the new refresh token and its expiry, matching the service result.

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.refreshtoken.auth.api.AuthControllerTest"`

### Task 4: Verify end-to-end Redis rotation behavior

**Files:**
- Modify: `src/test/kotlin/com/refreshtoken/auth/api/AuthIntegrationTest.kt`

**Step 1: Write the failing test**

Require login → refresh to create a new Redis key, delete the old one, reject the old refresh token, and allow logout of the new one.

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.refreshtoken.auth.api.AuthIntegrationTest"`

**Step 3: Write minimal implementation**

Only adjust production code as needed to satisfy the integration behavior.

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.refreshtoken.auth.api.AuthIntegrationTest"`

### Task 5: Final verification and documentation

**Files:**
- Modify: `README.md`

**Step 1: Run full test suite**

Run: `./gradlew test`

**Step 2: Run full build**

Run: `./gradlew build`

**Step 3: Update docs**

Document that refresh currently rotates and invalidates the old refresh token, but reuse detection still is not implemented.
