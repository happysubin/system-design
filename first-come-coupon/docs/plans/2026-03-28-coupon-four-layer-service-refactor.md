# Coupon Four-Layer Service Refactor Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Refactor the coupon feature into a simple 4-layer architecture using Service naming in the application layer.

**Architecture:** The target package structure is `coupon.api`, `coupon.application`, `coupon.domain`, and `coupon.infrastructure`. Controllers and API DTOs live in `api`, orchestration services live in `application`, business rules/results/exceptions/entities live in `domain`, and JPA/Redis/scheduling adapters live in `infrastructure`.

**Tech Stack:** Spring Boot 4, Kotlin, Spring MVC, Spring Data JPA, Spring Data Redis, PostgreSQL, H2, Testcontainers.

---

### Task 1: Define the target package structure in a failing test

**Files:**
- Create: `src/test/kotlin/com/firstcomecoupon/coupon/CouponLayerStructureTest.kt`

**Step 1: Write the failing structure test**
- Expect the following classes to exist in the target packages:
  - `com.firstcomecoupon.coupon.api.CouponController`
  - `com.firstcomecoupon.coupon.application.CouponApplicationService`
  - `com.firstcomecoupon.coupon.application.CouponClaimApplicationService`
  - `com.firstcomecoupon.coupon.domain.Coupon`
  - `com.firstcomecoupon.coupon.domain.CouponClaimResult`
  - `com.firstcomecoupon.coupon.infrastructure.redis.CouponClaimRedisGate`
  - `com.firstcomecoupon.coupon.infrastructure.persistence.CouponRepository`

**Step 2: Run test to verify it fails**
- `./gradlew test --tests com.firstcomecoupon.coupon.CouponLayerStructureTest`

### Task 2: Move coupon code into 4 layers

**Files:**
- Move/modify all coupon-related files from current `controller`, `domain`, `repository`, `serivce` packages

**Step 1: Move API layer**
- `CouponController`
- all coupon request/response DTOs

**Step 2: Move application layer**
- `CouponService` -> `CouponApplicationService`
- `CouponClaimService` -> `CouponClaimApplicationService`
- `CouponStatisticsQueryService`
- `CouponClaimCompensationHandler`

**Step 3: Move domain layer**
- `Coupon`, `CouponIssue`, `Member`
- `CouponClaimResult`
- `CouponSoldOutException`
- `CouponClaimEligibilityChecker`
- `CouponClaimFinalizer`

**Step 4: Move infrastructure layer**
- `CouponRepository`, `CouponIssueRepository`, `MemberRepository`
- `CouponClaimRedisGate`
- `CouponStockReconciliationService`
- `CouponStockReconciliationScheduler`

### Task 3: Update tests and imports

**Files:**
- Update all test packages/imports under `src/test/kotlin`

**Step 1: Make existing tests compile against moved packages**

**Step 2: Re-run focused structure test**
- `./gradlew test --tests com.firstcomecoupon.coupon.CouponLayerStructureTest`

**Step 3: Re-run feature regression tests**
- registration tests
- claim tests
- real Postgres/Redis API tests

### Task 4: Final verification

**Step 1: Run full build**
- `./gradlew build`
