# Claim Flow Split Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Refactor the coupon claim flow into a thinner orchestration service plus extracted helpers while keeping behavior unchanged.

**Architecture:** `CouponClaimService` remains the entry point for the claim flow, but it should orchestrate rather than own every detail. Eligibility validation and SQL-failure compensation are extracted into dedicated collaborators, and `CouponClaimResult` moves to its own file so the orchestration code reads as a simple sequence.

**Tech Stack:** Spring Boot 4, Kotlin, Spring MVC, Spring Data JPA, Spring Data Redis, PostgreSQL, H2, Mockito.

---

### Task 1: Add failing tests for the extracted helpers

**Files:**
- Create: `src/test/kotlin/com/firstcomecoupon/serivce/CouponClaimEligibilityCheckerTest.kt`
- Create: `src/test/kotlin/com/firstcomecoupon/serivce/CouponClaimCompensationHandlerTest.kt`

**Step 1: Write failing checker tests**
- missing coupon -> `CouponNotFound`
- missing member -> `MemberNotFound`
- outside issue window -> `NotInIssueWindow`
- valid coupon/member/window -> eligible continuation

**Step 2: Run checker test to verify failure**
- `./gradlew test --tests com.firstcomecoupon.serivce.CouponClaimEligibilityCheckerTest`

**Step 3: Write failing compensation tests**
- success -> returns `Issued`
- `DataIntegrityViolationException` -> rollback + `AlreadyClaimed`
- runtime exception -> rollback + rethrow

**Step 4: Run compensation test to verify failure**
- `./gradlew test --tests com.firstcomecoupon.serivce.CouponClaimCompensationHandlerTest`

### Task 2: Extract the helper classes and thin the service

**Files:**
- Create: `src/main/kotlin/com/firstcomecoupon/serivce/CouponClaimResult.kt`
- Create: `src/main/kotlin/com/firstcomecoupon/serivce/CouponClaimEligibilityChecker.kt`
- Create: `src/main/kotlin/com/firstcomecoupon/serivce/CouponClaimCompensationHandler.kt`
- Modify: `src/main/kotlin/com/firstcomecoupon/serivce/CouponClaimService.kt`
- Modify: `src/test/kotlin/com/firstcomecoupon/serivce/CouponClaimServiceTest.kt`
- Modify: `src/main/kotlin/com/firstcomecoupon/controller/CouponController.kt`

**Step 1: Move result type**
- Move `CouponClaimResult` to its own file without changing variants.

**Step 2: Extract eligibility checker**
- Move coupon/member lookup and issue-window validation into `CouponClaimEligibilityChecker`.

**Step 3: Extract compensation handler**
- Move finalizer try/catch + Redis rollback policy into `CouponClaimCompensationHandler`.

**Step 4: Thin `CouponClaimService`**
- Keep only the orchestration sequence:
  - eligibility check
  - Redis gate
  - compensation/finalization call

### Task 3: Verification

**Files:**
- No new files expected

**Step 1: Run focused tests**
- `./gradlew test --tests com.firstcomecoupon.serivce.CouponClaimEligibilityCheckerTest`
- `./gradlew test --tests com.firstcomecoupon.serivce.CouponClaimCompensationHandlerTest`
- `./gradlew test --tests com.firstcomecoupon.serivce.CouponClaimServiceTest`

**Step 2: Run regression tests**
- `./gradlew test --tests com.firstcomecoupon.controller.CouponClaimApiTest`
- `./gradlew test --tests com.firstcomecoupon.serivce.CouponClaimFinalizerTest`

**Step 3: Run full build**
- `./gradlew build`
