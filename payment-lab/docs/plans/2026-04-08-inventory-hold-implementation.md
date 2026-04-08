# Inventory Hold on Payment Start Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Reserve inventory when payment starts, reuse the same active hold for retries on the same order, and finalize the hold when payment is confirmed or failed.

**Architecture:** Add a separate inventory-hold lifecycle inside the monolith under a sibling package, keep payment state in `com.paymentlab.payment`, and let start-payment orchestration obtain or reuse a hold before PG approval begins. Final payment outcomes from webhook/reconcile should flow through one shared finalization step that confirms or releases the hold.

**Tech Stack:** Kotlin, Spring Boot, Spring Data JPA, Spring Scheduling, JUnit 5, Mockito.

---

### Task 1: Remove unused payment enums safely in code

**Files:**
- Modify: `src/main/kotlin/com/paymentlab/payment/domain/PaymentStatus.kt`
- Test: `src/test/kotlin/com/paymentlab/payment/PaymentLayerStructureTest.kt`

**Step 1: Write the failing test**

Add or extend a structure-level test asserting the payment status enum only contains the statuses actually used by the payment flow.

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "*PaymentLayerStructureTest"`

Expected: FAIL because `AUTH_REQUESTED` and `EXPIRED` are still present.

**Step 3: Write minimal implementation**

Remove `AUTH_REQUESTED` and `EXPIRED` from `PaymentStatus`.

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "*PaymentLayerStructureTest"`

Expected: PASS.

**Step 5: Commit**

```bash
git add src/main/kotlin/com/paymentlab/payment/domain/PaymentStatus.kt src/test/kotlin/com/paymentlab/payment/PaymentLayerStructureTest.kt
git commit -m "refactor: remove unused payment statuses"
```

### Task 2: Introduce inventory hold domain model

**Files:**
- Create: `src/main/kotlin/com/paymentlab/inventory/domain/InventoryHold.kt`
- Create: `src/main/kotlin/com/paymentlab/inventory/domain/InventoryHoldStatus.kt`
- Create: `src/main/kotlin/com/paymentlab/inventory/infrastructure/persistence/InventoryHoldRepository.kt`
- Test: `src/test/kotlin/com/paymentlab/inventory/infrastructure/persistence/InventoryHoldPersistenceTest.kt`

**Step 1: Write the failing test**

Create a persistence test that saves an `InventoryHold`, reloads it, and verifies `orderId`, `status`, and `expiresAt` persist.

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "*InventoryHoldPersistenceTest"`

Expected: FAIL because the entity/repository do not exist.

**Step 3: Write minimal implementation**

Create the new entity and enum with at least these fields:

- `id`
- `orderId`
- `status`
- `expiresAt`
- `createdAt`

Repository should support:

- active-hold lookup by `orderId`
- find expired held rows
- conditional status updates for finalization/expiration

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "*InventoryHoldPersistenceTest"`

Expected: PASS.

**Step 5: Commit**

```bash
git add src/main/kotlin/com/paymentlab/inventory/domain/InventoryHold.kt src/main/kotlin/com/paymentlab/inventory/domain/InventoryHoldStatus.kt src/main/kotlin/com/paymentlab/inventory/infrastructure/persistence/InventoryHoldRepository.kt src/test/kotlin/com/paymentlab/inventory/infrastructure/persistence/InventoryHoldPersistenceTest.kt
git commit -m "feat: add inventory hold persistence model"
```

### Task 3: Add inventory hold application service with reuse semantics

**Files:**
- Create: `src/main/kotlin/com/paymentlab/inventory/application/InventoryHoldApplicationService.kt`
- Test: `src/test/kotlin/com/paymentlab/inventory/application/InventoryHoldApplicationServiceTest.kt`

**Step 1: Write the failing test**

Add tests for:

1. no active hold → creates new `HELD`
2. existing active hold for same `orderId` → reuses existing hold
3. expired/released hold → creates new hold

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "*InventoryHoldApplicationServiceTest"`

Expected: FAIL because service logic does not exist.

**Step 3: Write minimal implementation**

Implement `reserveOrReuse(orderId: Long)` with these rules:

- if there is a non-expired `HELD` row for `orderId`, return it
- otherwise create a new `HELD` row with configured TTL-based `expiresAt`

Do not implement SKU-level stock deduction yet; keep this iteration focused on the hold lifecycle scaffold.

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "*InventoryHoldApplicationServiceTest"`

Expected: PASS.

**Step 5: Commit**

```bash
git add src/main/kotlin/com/paymentlab/inventory/application/InventoryHoldApplicationService.kt src/test/kotlin/com/paymentlab/inventory/application/InventoryHoldApplicationServiceTest.kt
git commit -m "feat: add inventory hold reuse flow"
```

### Task 4: Acquire or reuse hold before payment starts

**Files:**
- Modify: `src/main/kotlin/com/paymentlab/payment/application/PaymentFacade.kt`
- Modify: `src/main/kotlin/com/paymentlab/payment/application/PaymentStartOrchestrator.kt` (create this file if you choose the cleaner orchestration extraction)
- Test: `src/test/kotlin/com/paymentlab/payment/application/PaymentStartTransactionBoundaryTest.kt`
- Test: `src/test/kotlin/com/paymentlab/payment/api/PaymentApiTest.kt`

**Step 1: Write the failing test**

Add tests asserting:

1. payment start obtains a hold before PG approval
2. retry on same order reuses the same active hold
3. hold acquisition failure stops payment start before PG approval

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "*PaymentStartTransactionBoundaryTest" --tests "*PaymentApiTest"`

Expected: FAIL because payment start does not call inventory hold logic.

**Step 3: Write minimal implementation**

Inject `InventoryHoldApplicationService` into the payment-start orchestration seam.

- Preferred: extract a dedicated start-payment orchestrator above `PaymentFacade`
- Acceptable first step: call inventory hold service at the beginning of `PaymentFacade.startPayment()`

The request should still flow through existing payment-attempt creation and approval logic unchanged after hold acquisition succeeds.

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "*PaymentStartTransactionBoundaryTest" --tests "*PaymentApiTest"`

Expected: PASS.

**Step 5: Commit**

```bash
git add src/main/kotlin/com/paymentlab/payment/application/PaymentFacade.kt src/main/kotlin/com/paymentlab/payment/application/PaymentStartOrchestrator.kt src/test/kotlin/com/paymentlab/payment/application/PaymentStartTransactionBoundaryTest.kt src/test/kotlin/com/paymentlab/payment/api/PaymentApiTest.kt
git commit -m "feat: reserve inventory on payment start"
```

### Task 5: Add shared payment finalization hook for inventory updates

**Files:**
- Create: `src/main/kotlin/com/paymentlab/payment/application/PaymentFinalizationService.kt`
- Modify: `src/main/kotlin/com/paymentlab/payment/application/PaymentWebhookApplicationService.kt`
- Modify: `src/main/kotlin/com/paymentlab/payment/application/PaymentApplicationService.kt`
- Test: `src/test/kotlin/com/paymentlab/payment/application/PaymentWebhookApplicationServiceTest.kt`
- Test: `src/test/kotlin/com/paymentlab/payment/application/PaymentReconciliationApplicationServiceTest.kt`

**Step 1: Write the failing test**

Add tests asserting:

1. webhook success confirms inventory hold once
2. reconcile failure releases inventory hold once
3. repeated finalization does not double-confirm or double-release

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "*PaymentWebhookApplicationServiceTest" --tests "*PaymentReconciliationApplicationServiceTest"`

Expected: FAIL because payment finalization does not yet update inventory hold state.

**Step 3: Write minimal implementation**

Create a shared finalization service invoked by both webhook and reconcile flows after guarded payment final state transition succeeds.

Rules:

- payment `DONE` → confirm hold
- payment `FAILED` or `CANCELLED` → release hold
- if payment was already finalized elsewhere, do not re-run inventory finalization

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "*PaymentWebhookApplicationServiceTest" --tests "*PaymentReconciliationApplicationServiceTest"`

Expected: PASS.

**Step 5: Commit**

```bash
git add src/main/kotlin/com/paymentlab/payment/application/PaymentFinalizationService.kt src/main/kotlin/com/paymentlab/payment/application/PaymentWebhookApplicationService.kt src/main/kotlin/com/paymentlab/payment/application/PaymentApplicationService.kt src/test/kotlin/com/paymentlab/payment/application/PaymentWebhookApplicationServiceTest.kt src/test/kotlin/com/paymentlab/payment/application/PaymentReconciliationApplicationServiceTest.kt
git commit -m "feat: finalize inventory holds from payment outcomes"
```

### Task 6: Expire stale holds

**Files:**
- Create: `src/main/kotlin/com/paymentlab/inventory/application/PendingInventoryHoldExpirationScheduler.kt`
- Modify: `src/main/kotlin/com/paymentlab/inventory/application/InventoryHoldApplicationService.kt`
- Test: `src/test/kotlin/com/paymentlab/inventory/application/PendingInventoryHoldExpirationSchedulerTest.kt`

**Step 1: Write the failing test**

Add a scheduler test asserting expired `HELD` rows are transitioned out of active state and can no longer be reused.

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "*PendingInventoryHoldExpirationSchedulerTest"`

Expected: FAIL because no expiration scheduler exists.

**Step 3: Write minimal implementation**

Implement a scheduler that finds expired active holds and marks them `EXPIRED` (or `RELEASED`, if you choose a simpler first version) via guarded update logic.

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "*PendingInventoryHoldExpirationSchedulerTest"`

Expected: PASS.

**Step 5: Commit**

```bash
git add src/main/kotlin/com/paymentlab/inventory/application/PendingInventoryHoldExpirationScheduler.kt src/main/kotlin/com/paymentlab/inventory/application/InventoryHoldApplicationService.kt src/test/kotlin/com/paymentlab/inventory/application/PendingInventoryHoldExpirationSchedulerTest.kt
git commit -m "feat: expire stale inventory holds"
```

### Task 7: Document the new flow

**Files:**
- Modify: `README.md`
- Modify: `docs/plans/2026-04-08-inventory-hold-design.md`

**Step 1: Write the failing doc expectation**

List the exact README points to update before editing:

1. payment start now acquires/reuses inventory hold first
2. finalization confirms/releases hold
3. hold expiration is separate from payment state

**Step 2: Update documentation minimally**

Reflect the new payment-start and finalization flow in the README without overstating item-level stock support if the first iteration only adds hold scaffolding.

**Step 3: Run doc sanity check**

Read both files and ensure they match actual code behavior.

**Step 4: Commit**

```bash
git add README.md docs/plans/2026-04-08-inventory-hold-design.md
git commit -m "docs: add inventory hold payment-start flow"
```

### Task 8: Full verification

**Files:**
- Verify only

**Step 1: Run targeted tests**

Run:

```bash
./gradlew test --tests "*InventoryHoldPersistenceTest" \
  --tests "*InventoryHoldApplicationServiceTest" \
  --tests "*PaymentStartTransactionBoundaryTest" \
  --tests "*PaymentWebhookApplicationServiceTest" \
  --tests "*PaymentReconciliationApplicationServiceTest" \
  --tests "*PendingInventoryHoldExpirationSchedulerTest"
```

Expected: PASS.

**Step 2: Run full suite**

Run: `./gradlew test`

Expected: PASS.

**Step 3: Manual sanity path**

Manually verify this sequence:

1. create order
2. start payment → hold created
3. retry payment on same order → hold reused
4. finalize success → hold confirmed
5. finalize failure/cancel path on another order → hold released

**Step 4: Commit final verification checkpoint**

```bash
git status
```
