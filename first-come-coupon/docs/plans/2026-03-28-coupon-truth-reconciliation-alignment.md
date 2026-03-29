# Coupon Truth & Reconciliation Alignment Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Align the coupon system so `CouponIssue` is the sole SQL source of truth, Redis remains only an admission gate/runtime counter, and reconciliation keeps Redis consistent with SQL.

**Architecture:** Remove `issuedQuantity` from synchronous truth and stop exposing it as if it were authoritative. Add aggregate read queries on `CouponIssue` for issued/remaining counts, and introduce a reconciliation service that recomputes Redis stock from SQL on startup and periodically for active coupons. The claim hot path remains `Redis gate -> SQL finalize`, but SQL truth is `CouponIssue` only.

**Tech Stack:** Spring Boot 4, Kotlin, Spring MVC, Spring Data JPA, Spring Data Redis, PostgreSQL, H2, scheduled tasks.

---

### Task 1: Remove `issuedQuantity` from write truth and API surface

**Files:**
- Modify: `src/main/kotlin/com/firstcomecoupon/domain/Coupon.kt`
- Modify: `src/main/kotlin/com/firstcomecoupon/controller/dto/CreateCouponResponse.kt`
- Modify: `src/main/kotlin/com/firstcomecoupon/serivce/CouponService.kt`
- Modify: `src/test/kotlin/com/firstcomecoupon/controller/CouponRegistrationApiTest.kt`
- Modify: `src/test/kotlin/com/firstcomecoupon/serivce/CouponServiceTest.kt`
- Modify any remaining fixture-only tests that still initialize `issuedQuantity`

**Step 1: Write failing tests**
- Update registration/service tests so they no longer expect `issuedQuantity` in the response or entity semantics.

**Step 2: Run focused tests to verify failure**
- `./gradlew test --tests com.firstcomecoupon.controller.CouponRegistrationApiTest`
- `./gradlew test --tests com.firstcomecoupon.serivce.CouponServiceTest`

**Step 3: Remove the field from synchronous truth**
- Delete `issuedQuantity` from `Coupon`
- Remove mapping from registration response/service

### Task 2: Add aggregate read queries based on `CouponIssue`

**Files:**
- Modify: `src/main/kotlin/com/firstcomecoupon/repository/CouponIssueRepository.kt`
- Create (if needed): `src/main/kotlin/com/firstcomecoupon/service/read/CouponStatisticsQueryService.kt`
- Create (if needed): read DTOs for aggregate responses

**Step 1: Add focused failing tests**
- Single-coupon issued count query
- Remaining quantity calculation from `totalQuantity - issuedCount`
- Paged/grouped coupon count query if admin list view is planned

**Step 2: Run focused tests to verify failure**
- `./gradlew test --tests com.firstcomecoupon.repository.CouponIssueRepositoryTest`

**Step 3: Implement aggregate queries**
- `countByCouponId(couponId)` for simple reads
- Consider grouped aggregate query for admin/list views to avoid N+1 counts

### Task 3: Add Redis reconciliation service

**Files:**
- Create: `src/main/kotlin/com/firstcomecoupon/serivce/CouponStockReconciliationService.kt`
- Create (if needed): `src/main/kotlin/com/firstcomecoupon/serivce/CouponStockReconciliationScheduler.kt`
- Modify: `src/main/resources/application-local.yml` if scheduler settings are needed
- Modify: tests or add new reconciliation tests

**Step 1: Write failing tests**
- Startup-style sync computes `remaining = totalQuantity - issuedCount`
- Periodic sync updates only active or recently-claimed coupons

**Step 2: Run focused tests to verify failure**
- `./gradlew test --tests com.firstcomecoupon.serivce.CouponStockReconciliationServiceTest`

**Step 3: Implement reconciliation**
- Startup full sync for active coupons
- Periodic targeted sync for active/recent coupons
- Redis key update uses `coupon:stock:{couponId}`

### Task 4: Add SQL-side capacity verification in finalization

**Files:**
- Modify: `src/main/kotlin/com/firstcomecoupon/serivce/CouponClaimFinalizer.kt`
- Modify: `src/main/kotlin/com/firstcomecoupon/repository/CouponIssueRepository.kt`
- Modify: claim tests to cover oversell protection when Redis drifts

**Step 1: Write failing tests**
- SQL finalize rejects claim when issued count already reached `totalQuantity`
- Redis rollback still happens after SQL-side rejection

**Step 2: Run focused tests to verify failure**
- `./gradlew test --tests com.firstcomecoupon.serivce.CouponClaimFinalizerTest`
- `./gradlew test --tests com.firstcomecoupon.controller.CouponClaimApiTest`

**Step 3: Implement SQL guard**
- Compare issued count vs total quantity inside finalize transaction
- Reject before persisting new `CouponIssue` when capacity is exhausted

### Task 5: Final verification and docs alignment

**Files:**
- Modify: `README.md`
- Modify any prior plan docs that still claim `issuedQuantity` exists or is authoritative

**Step 1: Update docs**
- State clearly that `CouponIssue` is SQL truth
- State Redis is gate/runtime cache only

**Step 2: Run regression commands**
- `./gradlew test`
- `./gradlew build`
