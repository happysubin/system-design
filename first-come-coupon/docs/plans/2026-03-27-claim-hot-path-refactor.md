# Claim Hot Path Refactor Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Reduce successful claim-path SQL work so the request path persists only `CouponIssue`.

**Architecture:** Keep the current Redis gate and SQL final truth model. A successful claim should still insert one `CouponIssue`, but the hot path should stop mutating the shared `Coupon` row on every success. This removes the extra shared-row update while preserving duplicate protection and Redis rollback behavior, and it prepares the system to derive issued counts from `CouponIssue` instead of a coupon-level counter.

**Tech Stack:** Spring Boot 4, Kotlin, Spring MVC, Spring Data JPA, Spring Data Redis, PostgreSQL, H2, MockMvc, Mockito.

---

### Task 1: Change tests to define the new hot-path behavior

**Files:**
- Modify: `src/test/kotlin/com/firstcomecoupon/controller/CouponClaimApiTest.kt`
- Create: `src/test/kotlin/com/firstcomecoupon/serivce/CouponClaimFinalizerTest.kt`

**Step 1: Write the failing integration expectation**
- Update the success-path API test to expect one `CouponIssue` row inserted without any coupon-level counter update.

**Step 2: Run integration test to verify it fails**
- Run: `./gradlew test --tests com.firstcomecoupon.controller.CouponClaimApiTest`

**Step 3: Write the failing unit test for the finalizer**
- Assert `CouponClaimFinalizer.finalizeClaim(...)` saves/flushed `CouponIssue`
- Assert it does **not** call any coupon counter update API

**Step 4: Run unit test to verify it fails**
- Run: `./gradlew test --tests com.firstcomecoupon.serivce.CouponClaimFinalizerTest`

### Task 2: Remove the coupon counter update from the hot path

**Files:**
- Modify: `src/main/kotlin/com/firstcomecoupon/serivce/CouponClaimFinalizer.kt`
- Modify: `src/main/kotlin/com/firstcomecoupon/repository/CouponRepository.kt`

**Step 1: Remove the synchronous coupon counter increment**
- Delete the `couponRepository.incrementIssuedQuantity(couponId)` call from `CouponClaimFinalizer.finalizeClaim(...)`

**Step 2: Remove dead repository code**
- Delete `incrementIssuedQuantity(...)` from `CouponRepository` if no remaining callers exist

**Step 3: Re-run focused tests**
- `./gradlew test --tests com.firstcomecoupon.serivce.CouponClaimFinalizerTest`
- `./gradlew test --tests com.firstcomecoupon.controller.CouponClaimApiTest`

### Task 3: Update the docs to reflect the new claim-path truth

**Files:**
- Modify: `README.md`
- Modify: `docs/plans/2026-03-27-coupon-claim-api.md`

**Step 1: Clarify synchronous success semantics**
- Document that the request path now persists `CouponIssue` only
- Clarify that coupon-level issued counts should be derived from `CouponIssue`

### Task 4: Final verification

**Files:**
- No code changes expected

**Step 1: Run the service-level claim tests**
- `./gradlew test --tests com.firstcomecoupon.serivce.CouponClaimServiceTest`

**Step 2: Run full test suite**
- `./gradlew test`

**Step 3: Run full build**
- `./gradlew build`
