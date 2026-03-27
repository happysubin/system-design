# Coupon Claim API Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a V1 coupon claim API that gates issuance in Redis and finalizes it in SQL.

**Architecture:** The endpoint will be `POST /api/v1/coupons/{couponId}/claim`. A Redis gate will atomically reject duplicate claims and sold-out stock, then a transactional SQL finalizer will persist `CouponIssue`. On SQL failure after Redis success, the Redis gate will be compensated.

**Tech Stack:** Spring Boot 4, Kotlin, Spring MVC, Spring Data JPA, Spring Data Redis, PostgreSQL, H2, MockMvc, Mockito.

---

### Task 1: Add failing claim tests

**Files:**
- Create: `src/test/kotlin/com/firstcomecoupon/serivce/CouponClaimServiceTest.kt`
- Create: `src/test/kotlin/com/firstcomecoupon/controller/CouponClaimApiTest.kt`

**Step 1: Write failing unit tests**
- Success path: Redis gate allows claim and SQL finalizer returns issued coupon.
- Failure path: Redis gate rejects with `ALREADY_CLAIMED`.
- Failure path: Redis gate rejects with `SOLD_OUT`.

**Step 2: Run unit test to verify failure**
- Run: `./gradlew test --tests com.firstcomecoupon.serivce.CouponClaimServiceTest`

**Step 3: Write failing H2 integration tests**
- Success path returns `201` and persists `CouponIssue`.
- Duplicate path returns `409`.

**Step 4: Run integration test to verify failure**
- Run: `./gradlew test --tests com.firstcomecoupon.controller.CouponClaimApiTest`

### Task 2: Add claim domain/repository/controller/service pieces

**Files:**
- Create: `src/main/kotlin/com/firstcomecoupon/repository/CouponIssueRepository.kt`
- Create: `src/main/kotlin/com/firstcomecoupon/repository/MemberRepository.kt`
- Modify: `src/main/kotlin/com/firstcomecoupon/repository/CouponRepository.kt`
- Create: `src/main/kotlin/com/firstcomecoupon/controller/dto/IssueCouponRequest.kt`
- Create: `src/main/kotlin/com/firstcomecoupon/controller/dto/IssueCouponResponse.kt`
- Create: `src/main/kotlin/com/firstcomecoupon/serivce/CouponClaimRedisGate.kt`
- Create: `src/main/kotlin/com/firstcomecoupon/serivce/CouponClaimFinalizer.kt`
- Create: `src/main/kotlin/com/firstcomecoupon/serivce/CouponClaimService.kt`
- Modify: `src/main/kotlin/com/firstcomecoupon/controller/CouponController.kt`

**Step 1: Implement minimal repository support**
- Add JPA repositories for `CouponIssue` and `Member`.

**Step 2: Implement Redis gate**
- Redis stock key: `coupon:stock:{couponId}`
- Redis claim key: `coupon:claim:{couponId}:{memberId}`
- Use one Lua script for duplicate check + stock decrement.

**Step 3: Implement SQL finalizer**
- Save `CouponIssue`.

**Step 4: Implement orchestrator + controller**
- Add `POST /api/v1/coupons/{couponId}/claim`.
- Map claim result to HTTP 201/409/404/422.

### Task 3: Verify and document

**Files:**
- Modify: `README.md`

**Step 1: Add curl example for claim API**

**Step 2: Run focused verification**
- `./gradlew test --tests com.firstcomecoupon.serivce.CouponClaimServiceTest`
- `./gradlew test --tests com.firstcomecoupon.controller.CouponClaimApiTest`

**Step 3: Run full verification**
- `./gradlew build`
