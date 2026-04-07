# Redis Degraded Login Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Keep login usable during Redis outages by issuing an access token without a refresh token, while making refresh fail cleanly and prompt re-login.

**Architecture:** Preserve the current healthy-path behavior. In degraded mode, login should catch refresh-store failures and return an access-token-only success response with explicit degraded-mode metadata; refresh should translate refresh-store failures into a clear auth failure instead of an internal server error. Logout can remain best-effort or unchanged for now unless the response contract is explicitly widened.

**Tech Stack:** Spring Boot Web MVC, Spring Data Redis, Kotlin, JJWT, MockMvc, Testcontainers.

---

### Task 1: Add failing tests for degraded login and refresh failure

**Files:**
- Modify: `src/test/kotlin/com/refreshtoken/auth/application/AuthServiceTest.kt`
- Modify: `src/test/kotlin/com/refreshtoken/auth/api/AuthControllerTest.kt`

**Step 1: Write the failing test**

Add one test where the refresh-token store throws on login and assert that login still succeeds with access-token-only semantics. Add another test where the store throws during refresh and assert that a dedicated refresh-unavailable failure is raised and mapped cleanly at the API layer.

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.refreshtoken.auth.application.AuthServiceTest"`

**Step 3: Write minimal implementation**

No production code yet.

**Step 4: Run test to verify it passes**

Run after implementation is complete.

### Task 2: Implement degraded login result and refresh-unavailable exception

**Files:**
- Modify: `src/main/kotlin/com/refreshtoken/auth/application/AuthService.kt`

**Step 1: Write the failing test**

Covered in Task 1.

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.refreshtoken.auth.application.AuthServiceTest"`

**Step 3: Write minimal implementation**

Make `LoginResult` capable of returning access-token-only responses. Catch refresh-store save failures during login and return degraded metadata instead of failing. Catch refresh-store failures during refresh and raise a dedicated exception distinct from invalid token errors.

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.refreshtoken.auth.application.AuthServiceTest"`

### Task 3: Update HTTP response contract and exception mapping

**Files:**
- Modify: `src/main/kotlin/com/refreshtoken/auth/api/AuthResponse.kt`
- Modify: `src/main/kotlin/com/refreshtoken/auth/api/AuthController.kt`
- Modify: `src/main/kotlin/com/refreshtoken/auth/api/AuthExceptionHandler.kt`
- Modify: `src/test/kotlin/com/refreshtoken/auth/api/AuthControllerTest.kt`

**Step 1: Write the failing test**

Covered in Task 1 controller tests.

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.refreshtoken.auth.api.AuthControllerTest"`

**Step 3: Write minimal implementation**

Expose nullable refresh token fields plus a degraded-mode indicator/message on login responses. Map refresh-store unavailability to a clear status code and error message for refresh requests.

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.refreshtoken.auth.api.AuthControllerTest"`

### Task 4: Add integration coverage for degraded mode (without real Redis)

**Files:**
- Create or modify: `src/test/kotlin/com/refreshtoken/auth/api/AuthDegradedModeIntegrationTest.kt`

**Step 1: Write the failing test**

Boot the app with a failing `RefreshTokenStore` bean override and verify login returns access-token-only degraded output while refresh returns the expected error.

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.refreshtoken.auth.api.AuthDegradedModeIntegrationTest"`

**Step 3: Write minimal implementation**

Only adjust production code if the integration test reveals missing wiring.

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.refreshtoken.auth.api.AuthDegradedModeIntegrationTest"`

### Task 5: Final verification and docs

**Files:**
- Modify: `README.md`

**Step 1: Run full test suite**

Run: `./gradlew test`

**Step 2: Run full build**

Run: `./gradlew build`

**Step 3: Update docs**

Document the degraded-mode policy: login may succeed with access-token-only during Redis outage, refresh fails and requires re-login.
