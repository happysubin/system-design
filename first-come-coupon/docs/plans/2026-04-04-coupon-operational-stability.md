# Coupon Operational Stability Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Harden the coupon claim flow for production-like failure scenarios without changing the core Redis gate + SQL truth architecture.

**Architecture:** Keep the current synchronous claim flow. Improve the operational weak points by making Redis compensation atomic and idempotent, narrowing database-exception classification, adding stable API error mapping for unexpected failures, and extending recovery logic and tests around drift scenarios.

**Tech Stack:** Spring Boot 4, Kotlin, Spring MVC, Spring Data JPA, Spring Data Redis, PostgreSQL, H2, Testcontainers, MockMvc, Mockito.

---

### Task 1: Define the desired failure-handling behavior in failing tests

**Files:**
- Modify: `src/test/kotlin/com/firstcomecoupon/coupon/application/CouponClaimCompensationHandlerTest.kt`
- Modify: `src/test/kotlin/com/firstcomecoupon/coupon/api/CouponClaimApiTest.kt`
- Modify: `src/test/kotlin/com/firstcomecoupon/coupon/api/CouponClaimApiPostgresTest.kt`
- Create or modify: `src/test/kotlin/com/firstcomecoupon/coupon/infrastructure/redis/CouponClaimRedisGateTest.kt`

**Step 1: Write the failing compensation tests**
- Add a test proving duplicate classification happens only for the coupon/member unique constraint.
- Add a test proving unknown integrity failures are treated as internal failures, not `ALREADY_CLAIMED`.

**Step 2: Write the failing rollback tests**
- Add a test proving Redis rollback is executed atomically and idempotently.
- Add a test proving duplicate rollback does not inflate stock on repeated execution.

**Step 3: Write the failing API tests**
- Add a test proving unexpected runtime failures return a stable internal-failure response contract rather than default framework output.
- Add a test proving the `NOT_INITIALIZED` path still returns the existing internal-failure response.

**Step 4: Run focused tests to verify they fail for the expected reasons**
- `./gradlew test --tests com.firstcomecoupon.coupon.application.CouponClaimCompensationHandlerTest`
- `./gradlew test --tests com.firstcomecoupon.coupon.api.CouponClaimApiTest`
- `./gradlew test --tests com.firstcomecoupon.coupon.api.CouponClaimApiPostgresTest`

### Task 2: Make Redis compensation atomic and idempotent

**Files:**
- Modify: `src/main/kotlin/com/firstcomecoupon/coupon/infrastructure/redis/CouponClaimRedisGate.kt`
- Test: `src/test/kotlin/com/firstcomecoupon/coupon/infrastructure/redis/CouponClaimRedisGateTest.kt`

**Step 1: Replace two-call rollback with a Lua script**
- Roll back in one atomic Redis script.
- Only restore stock when the claim marker exists.
- Delete the marker in the same script.

**Step 2: Keep the public API minimal**
- Preserve `rollback(couponId, memberId)` unless a richer return type is necessary for observability.

**Step 3: Run focused tests**
- `./gradlew test --tests com.firstcomecoupon.coupon.infrastructure.redis.CouponClaimRedisGateTest`
- `./gradlew test --tests com.firstcomecoupon.coupon.api.CouponClaimApiPostgresTest`

### Task 3: Narrow exception handling and stabilize API failure responses

**Files:**
- Modify: `src/main/kotlin/com/firstcomecoupon/coupon/application/CouponClaimCompensationHandler.kt`
- Modify: `src/main/kotlin/com/firstcomecoupon/coupon/domain/CouponClaimResult.kt` (only if a new explicit failure result is needed)
- Modify: `src/main/kotlin/com/firstcomecoupon/coupon/api/CouponClaimResponseMapper.kt`
- Create: `src/main/kotlin/com/firstcomecoupon/coupon/api/CouponApiExceptionHandler.kt`
- Test: `src/test/kotlin/com/firstcomecoupon/coupon/application/CouponClaimCompensationHandlerTest.kt`
- Test: `src/test/kotlin/com/firstcomecoupon/coupon/api/CouponClaimApiTest.kt`

**Step 1: Narrow duplicate classification**
- Inspect the `DataIntegrityViolationException` cause chain.
- Map to `AlreadyClaimed` only when the violated constraint is `uk_coupon_issue_coupon_member`.

**Step 2: Handle non-duplicate integrity failures safely**
- Roll back Redis.
- Surface an internal-failure result or throw a typed exception that the API layer converts consistently.

**Step 3: Add stable API error mapping**
- Add `@RestControllerAdvice` for unexpected runtime exceptions in coupon APIs.
- Return the same response shape used by `CouponClaimResponseMapper`.

**Step 4: Run focused tests**
- `./gradlew test --tests com.firstcomecoupon.coupon.application.CouponClaimCompensationHandlerTest`
- `./gradlew test --tests com.firstcomecoupon.coupon.api.CouponClaimApiTest`

### Task 4: Strengthen recovery and reconciliation behavior

**Files:**
- Modify: `src/main/kotlin/com/firstcomecoupon/coupon/infrastructure/reconciliation/CouponStockReconciliationService.kt`
- Modify: `src/main/kotlin/com/firstcomecoupon/coupon/application/CouponClaimCompensationHandler.kt`
- Modify or create: `src/test/kotlin/com/firstcomecoupon/coupon/infrastructure/reconciliation/CouponStockReconciliationServiceTest.kt`
- Modify: `src/test/kotlin/com/firstcomecoupon/coupon/api/CouponClaimApiPostgresTest.kt`

**Step 1: Use truth-based recovery where blind increment is unsafe**
- For sold-out and internal-failure branches, prefer reconciling from SQL truth over naive stock increment when appropriate.

**Step 2: Add a recovery test**
- Prove reconciliation restores correct stock after drift.
- If claim-marker cleanup is implemented, prove stale marker scenarios are handled explicitly.

**Step 3: Run focused tests**
- `./gradlew test --tests com.firstcomecoupon.coupon.infrastructure.reconciliation.CouponStockReconciliationServiceTest`
- `./gradlew test --tests com.firstcomecoupon.coupon.api.CouponClaimApiPostgresTest`

### Task 5: Final verification

**Files:**
- No new code changes expected

**Step 1: Run all claim-related tests**
- `./gradlew test --tests com.firstcomecoupon.coupon.application.CouponClaimApplicationServiceTest`
- `./gradlew test --tests com.firstcomecoupon.coupon.application.CouponClaimFinalizerTest`
- `./gradlew test --tests com.firstcomecoupon.coupon.application.CouponClaimCompensationHandlerTest`
- `./gradlew test --tests com.firstcomecoupon.coupon.api.CouponClaimApiTest`
- `./gradlew test --tests com.firstcomecoupon.coupon.api.CouponClaimApiPostgresTest`
- `./gradlew test --tests com.firstcomecoupon.coupon.infrastructure.reconciliation.CouponStockReconciliationServiceTest`

**Step 2: Run the full test suite**
- `./gradlew test`

**Step 3: Run the full build**
- `./gradlew build`
