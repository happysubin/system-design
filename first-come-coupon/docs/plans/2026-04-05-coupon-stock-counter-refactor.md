# Coupon Stock Counter Refactor Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace coupon finalization's lock+count structure with a coupon counter-based finalization path while preserving duplicate protection through `coupon_issue` unique constraints.

**Architecture:** Keep `coupon` as the master/policy entity and add a 1:1 `coupon_stock` counter entity for runtime stock tracking. In the finalizer, replace `coupon row lock + countByCouponId` with atomic stock decrement plus issue insert; duplicate and sold-out handling remain enforced through SQL, and Redis continues to act as the fast gate. Duplicate issue inserts are expected to roll back the surrounding transaction rather than requiring a separate stock-recovery step.

**Tech Stack:** Spring Boot 4, Kotlin, Spring Data JPA, PostgreSQL/H2, Redis, JUnit 5, Mockito, MockMvc, Testcontainers.

---

### Task 1: Define the new finalization behavior in failing tests

**Files:**
- Modify: `src/test/kotlin/com/firstcomecoupon/coupon/application/CouponClaimFinalizerTest.kt`
- Modify: `src/test/kotlin/com/firstcomecoupon/coupon/api/CouponClaimApiPostgresTest.kt`
- Modify: `src/test/kotlin/com/firstcomecoupon/coupon/api/CouponClaimConcurrencyTest.kt`

**Step 1: Write the failing finalizer tests**
- Add tests proving finalization uses stock depletion instead of `countByCouponId` for sold-out checks.
- Add tests proving duplicate inserts are handled by unique constraint and surrounding transaction rollback semantics.

**Step 2: Write the failing integration assertions**
- Update Postgres/API tests so they verify `coupon_stock` state, not just `coupon_issue` counts.

**Step 3: Run tests to verify red**
- `./gradlew test --tests com.firstcomecoupon.coupon.application.CouponClaimFinalizerTest --tests com.firstcomecoupon.coupon.api.CouponClaimApiPostgresTest --tests com.firstcomecoupon.coupon.api.CouponClaimConcurrencyTest`

### Task 2: Add coupon_stock 1:1 persistence model

**Files:**
- Create: `src/main/kotlin/com/firstcomecoupon/coupon/domain/CouponStock.kt`
- Create: `src/main/kotlin/com/firstcomecoupon/coupon/infrastructure/persistence/CouponStockRepository.kt`
- Create or modify: `src/test/kotlin/com/firstcomecoupon/coupon/infrastructure/persistence/CouponStockRepositoryTest.kt`

**Step 1: Add 1:1 counter entity**
- One stock row per coupon with remaining quantity and timestamps.

**Step 2: Add repository methods**
- Conditional decrement, increment, and lookup methods needed by finalization and reconciliation.

**Step 3: Run focused repository tests**
- `./gradlew test --tests com.firstcomecoupon.coupon.infrastructure.persistence.CouponStockRepositoryTest`

### Task 3: Initialize coupon_stock on coupon creation

**Files:**
- Modify: `src/main/kotlin/com/firstcomecoupon/coupon/application/CouponApplicationService.kt`
- Test: `src/test/kotlin/com/firstcomecoupon/coupon/application/CouponApplicationServiceTest.kt`
- Test: `src/test/kotlin/com/firstcomecoupon/coupon/api/CouponRegistrationApiPostgresTest.kt`

**Step 1: Write the failing creation tests**
- Assert coupon creation also persists a matching coupon_stock row.

**Step 2: Implement stock initialization**
- When a coupon is created, initialize the 1:1 stock row with full remaining quantity.

**Step 3: Run focused tests**
- `./gradlew test --tests com.firstcomecoupon.coupon.application.CouponApplicationServiceTest --tests com.firstcomecoupon.coupon.api.CouponRegistrationApiPostgresTest`

### Task 4: Replace finalizer lock+count with counter + unique insert

**Files:**
- Modify: `src/main/kotlin/com/firstcomecoupon/coupon/application/CouponClaimFinalizer.kt`
- Modify: `src/main/kotlin/com/firstcomecoupon/coupon/infrastructure/persistence/CouponIssueRepository.kt`
- Modify: `src/main/kotlin/com/firstcomecoupon/coupon/infrastructure/reconciliation/CouponStockReconciliationService.kt`
- Modify: `src/main/kotlin/com/firstcomecoupon/coupon/application/CouponStatisticsQueryService.kt`
- Test: `src/test/kotlin/com/firstcomecoupon/coupon/application/CouponClaimFinalizerTest.kt`
- Test: `src/test/kotlin/com/firstcomecoupon/coupon/api/CouponClaimApiPostgresTest.kt`
- Test: `src/test/kotlin/com/firstcomecoupon/coupon/api/CouponClaimConcurrencyTest.kt`

**Step 1: Implement conditional stock decrement**
- Use `coupon_stock` as the sold-out decision point instead of `countByCouponId`.

**Step 2: Keep duplicate protection in `coupon_issue`**
- If issue insert fails due to duplicate unique constraint, rely on the surrounding transaction rollback to cancel the stock decrement and preserve the same duplicate semantics.

**Step 3: Update reconciliation/statistics**
- Reconciliation should re-sync Redis from `coupon_stock`, not by recounting issue rows.
- Statistics reads should use `coupon_stock` for remaining quantity and `coupon_issue` for issued truth where appropriate.

**Step 4: Run focused tests**
- `./gradlew test --tests com.firstcomecoupon.coupon.application.CouponClaimFinalizerTest --tests com.firstcomecoupon.coupon.api.CouponClaimApiPostgresTest --tests com.firstcomecoupon.coupon.api.CouponClaimConcurrencyTest`

### Task 5: Final verification

**Files:**
- No new code changes expected

**Step 1: Run full test suite**
- `./gradlew test`

**Step 2: Run full build**
- `./gradlew build`

**Step 3: Create a separate feature commit**
- Commit only the stock-counter refactor files in a new commit, keeping the earlier README checkpoint separate.
