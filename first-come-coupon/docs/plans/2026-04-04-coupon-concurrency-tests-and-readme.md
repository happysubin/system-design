# Coupon Concurrency Tests and README Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add high-contention API integration tests for coupon claims and document the current failure-handling behavior in README.

**Architecture:** Reuse the existing PostgreSQL + Redis Testcontainers setup to drive concurrent requests through the real claim API. Prove the production invariants that matter most under contention: no oversell, no duplicate issuance for the same member, and convergence of final persisted state. Update README with a concise failure-handling section that matches the implemented rollback and reconciliation behavior.

**Tech Stack:** Spring Boot 4, Kotlin, Spring MVC, MockMvc, Spring Data JPA, Spring Data Redis, PostgreSQL, Redis, Testcontainers, JUnit 5.

---

### Task 1: Define concurrent oversell protection in a failing integration test

**Files:**
- Create: `src/test/kotlin/com/firstcomecoupon/coupon/api/CouponClaimConcurrencyTest.kt`
- Reference: `src/test/kotlin/com/firstcomecoupon/coupon/api/CouponClaimApiPostgresTest.kt`
- Reference: `src/test/kotlin/com/firstcomecoupon/support/AbstractPostgresApiTest.kt`

**Step 1: Write the failing test**
- Create a PostgreSQL/Redis integration test that sets `totalQuantity=50`, creates 100 distinct members, initializes Redis stock, then fires 100 concurrent `POST /api/v1/coupons/{couponId}/claim` requests.
- Assert exactly 50 issued rows remain in SQL and that Redis stock converges to `0` after all requests complete.

**Step 2: Run test to verify it fails**
- `./gradlew test --tests com.firstcomecoupon.coupon.api.CouponClaimConcurrencyTest`

**Step 3: Write minimal implementation**
- Add only the test harness needed for deterministic concurrent start, result collection, and final-state verification.

**Step 4: Run test to verify it passes**
- `./gradlew test --tests com.firstcomecoupon.coupon.api.CouponClaimConcurrencyTest`

### Task 2: Define duplicate prevention for concurrent same-member claims

**Files:**
- Modify: `src/test/kotlin/com/firstcomecoupon/coupon/api/CouponClaimConcurrencyTest.kt`

**Step 1: Write the failing test**
- Add a second concurrent scenario where one member submits many simultaneous requests for the same coupon.
- Assert exactly one `CouponIssue` row exists for that member/coupon pair and that all other responses are non-success (`ALREADY_CLAIMED` or equivalent failure path consistent with current implementation).

**Step 2: Run test to verify it fails**
- `./gradlew test --tests com.firstcomecoupon.coupon.api.CouponClaimConcurrencyTest`

**Step 3: Write minimal implementation**
- Extend the test harness only as needed to collect result types and verify duplicate-prevention invariants.

**Step 4: Run test to verify it passes**
- `./gradlew test --tests com.firstcomecoupon.coupon.api.CouponClaimConcurrencyTest`

### Task 3: Document failure handling in README

**Files:**
- Modify: `README.md`

**Step 1: Add concise failure-handling section**
- Document the runtime claim flow for failure cases:
  - Redis gate pass → SQL finalization failure
  - atomic Redis rollback
  - coupon-level reconciliation to SQL truth where applicable
  - stable API failure responses

**Step 2: Keep the documentation factual and minimal**
- Do not claim retry/alerting/stale-marker cleanup if not implemented.

### Task 4: Final verification

**Files:**
- No new code changes expected

**Step 1: Run focused concurrency tests**
- `./gradlew test --tests com.firstcomecoupon.coupon.api.CouponClaimConcurrencyTest`

**Step 2: Run full test suite**
- `./gradlew test`

**Step 3: Run full build**
- `./gradlew build`
