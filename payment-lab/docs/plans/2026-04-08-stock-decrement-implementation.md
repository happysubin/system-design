# Stock Decrement with Reserved Inventory Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add SKU-level stock reservation and stock decrement so payment success consumes real inventory while payment failure or expiration only releases reservations.

**Architecture:** Keep payment responsible for payment attempts and final outcomes, and move all quantity mutation into inventory-owned services. Introduce `SkuStock`, `OrderItem`, and `InventoryHoldItem`, reserve stock when payment starts, and confirm/release/expire the exact linked hold through guarded inventory operations.

**Tech Stack:** Kotlin, Spring Boot, Spring Data JPA, Spring Scheduling, JUnit 5, Mockito.

---

### Task 1: Add order item persistence model

**Files:**
- Create: `src/main/kotlin/com/paymentlab/order/domain/OrderItem.kt`
- Create: `src/main/kotlin/com/paymentlab/order/infrastructure/persistence/OrderItemRepository.kt`
- Modify: `src/main/kotlin/com/paymentlab/payment/domain/Order.kt`
- Modify: `src/main/kotlin/com/paymentlab/payment/application/OrderApplicationService.kt`
- Modify: `src/main/kotlin/com/paymentlab/payment/api/dto/CreateOrderRequest.kt`
- Modify: `src/main/kotlin/com/paymentlab/payment/api/dto/CreateOrderResponse.kt`
- Test: `src/test/kotlin/com/paymentlab/order/infrastructure/persistence/OrderItemPersistenceTest.kt`

**Step 1: Write the failing test**

Add a persistence test proving an order can save and reload multiple order items with `skuId`, `quantity`, and `unitPrice`.

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "*OrderItemPersistenceTest"`

Expected: FAIL because order item persistence does not exist.

**Step 3: Write minimal implementation**

Add `OrderItem` and repository, then extend the current local test-order flow so an order can include item lines.

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "*OrderItemPersistenceTest"`

Expected: PASS.

**Step 5: Commit**

```bash
git add src/main/kotlin/com/paymentlab/order/domain/OrderItem.kt src/main/kotlin/com/paymentlab/order/infrastructure/persistence/OrderItemRepository.kt src/main/kotlin/com/paymentlab/payment/domain/Order.kt src/main/kotlin/com/paymentlab/payment/application/OrderApplicationService.kt src/main/kotlin/com/paymentlab/payment/api/dto/CreateOrderRequest.kt src/main/kotlin/com/paymentlab/payment/api/dto/CreateOrderResponse.kt src/test/kotlin/com/paymentlab/order/infrastructure/persistence/OrderItemPersistenceTest.kt
git commit -m "feat: add order item persistence"
```

### Task 2: Add SKU stock model

**Files:**
- Create: `src/main/kotlin/com/paymentlab/inventory/domain/SkuStock.kt`
- Create: `src/main/kotlin/com/paymentlab/inventory/infrastructure/persistence/SkuStockRepository.kt`
- Test: `src/test/kotlin/com/paymentlab/inventory/infrastructure/persistence/SkuStockPersistenceTest.kt`

**Step 1: Write the failing test**

Add a persistence test proving `skuId`, `onHand`, and `reserved` persist and reload correctly.

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "*SkuStockPersistenceTest"`

Expected: FAIL because SKU stock persistence does not exist.

**Step 3: Write minimal implementation**

Create `SkuStock` and repository. Keep `available` as a derived concept in service logic, not as a persisted column.

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "*SkuStockPersistenceTest"`

Expected: PASS.

**Step 5: Commit**

```bash
git add src/main/kotlin/com/paymentlab/inventory/domain/SkuStock.kt src/main/kotlin/com/paymentlab/inventory/infrastructure/persistence/SkuStockRepository.kt src/test/kotlin/com/paymentlab/inventory/infrastructure/persistence/SkuStockPersistenceTest.kt
git commit -m "feat: add sku stock model"
```

### Task 3: Add inventory hold item model

**Files:**
- Create: `src/main/kotlin/com/paymentlab/inventory/domain/InventoryHoldItem.kt`
- Create: `src/main/kotlin/com/paymentlab/inventory/infrastructure/persistence/InventoryHoldItemRepository.kt`
- Test: `src/test/kotlin/com/paymentlab/inventory/infrastructure/persistence/InventoryHoldItemPersistenceTest.kt`

**Step 1: Write the failing test**

Add a persistence test proving a hold can save and reload multiple hold items with `skuId` and `quantity`.

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "*InventoryHoldItemPersistenceTest"`

Expected: FAIL because hold item persistence does not exist.

**Step 3: Write minimal implementation**

Create `InventoryHoldItem` plus repository methods for loading all items by `holdId`.

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "*InventoryHoldItemPersistenceTest"`

Expected: PASS.

**Step 5: Commit**

```bash
git add src/main/kotlin/com/paymentlab/inventory/domain/InventoryHoldItem.kt src/main/kotlin/com/paymentlab/inventory/infrastructure/persistence/InventoryHoldItemRepository.kt src/test/kotlin/com/paymentlab/inventory/infrastructure/persistence/InventoryHoldItemPersistenceTest.kt
git commit -m "feat: add inventory hold item model"
```

### Task 4: Reserve stock from order items when creating a new hold

**Files:**
- Modify: `src/main/kotlin/com/paymentlab/inventory/application/InventoryHoldApplicationService.kt`
- Modify: `src/main/kotlin/com/paymentlab/inventory/infrastructure/persistence/InventoryHoldRepository.kt`
- Modify: `src/main/kotlin/com/paymentlab/inventory/infrastructure/persistence/SkuStockRepository.kt`
- Modify: `src/main/kotlin/com/paymentlab/inventory/infrastructure/persistence/InventoryHoldItemRepository.kt`
- Modify: `src/main/kotlin/com/paymentlab/order/infrastructure/persistence/OrderItemRepository.kt`
- Test: `src/test/kotlin/com/paymentlab/inventory/application/InventoryHoldReservationServiceTest.kt`

**Step 1: Write the failing test**

Add tests for:

1. new hold reserves stock from order items
2. existing active hold is reused without reserving twice
3. insufficient available stock fails reservation

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "*InventoryHoldReservationServiceTest"`

Expected: FAIL because stock-backed reservation does not exist.

**Step 3: Write minimal implementation**

On new hold creation:

- load order items
- conditionally increment `reserved` on each `SkuStock`
- create hold row
- create `InventoryHoldItem` rows

On reuse:

- return existing active hold
- do not touch `SkuStock.reserved` again

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "*InventoryHoldReservationServiceTest"`

Expected: PASS.

**Step 5: Commit**

```bash
git add src/main/kotlin/com/paymentlab/inventory/application/InventoryHoldApplicationService.kt src/main/kotlin/com/paymentlab/inventory/infrastructure/persistence/InventoryHoldRepository.kt src/main/kotlin/com/paymentlab/inventory/infrastructure/persistence/SkuStockRepository.kt src/main/kotlin/com/paymentlab/inventory/infrastructure/persistence/InventoryHoldItemRepository.kt src/main/kotlin/com/paymentlab/order/infrastructure/persistence/OrderItemRepository.kt src/test/kotlin/com/paymentlab/inventory/application/InventoryHoldReservationServiceTest.kt
git commit -m "feat: reserve sku stock for inventory holds"
```

### Task 5: Confirm linked hold by decrementing onHand

**Files:**
- Modify: `src/main/kotlin/com/paymentlab/payment/application/PaymentFinalizationService.kt`
- Modify: `src/main/kotlin/com/paymentlab/inventory/application/InventoryHoldApplicationService.kt`
- Modify: `src/main/kotlin/com/paymentlab/inventory/infrastructure/persistence/SkuStockRepository.kt`
- Modify: `src/main/kotlin/com/paymentlab/inventory/infrastructure/persistence/InventoryHoldItemRepository.kt`
- Test: `src/test/kotlin/com/paymentlab/payment/application/PaymentFinalizationServiceStockDecrementTest.kt`

**Step 1: Write the failing test**

Add a finalization integration test proving payment success:

- confirms the linked hold
- decrements `reserved`
- decrements `onHand`
- leaves unrelated SKU rows unchanged

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "*PaymentFinalizationServiceStockDecrementTest"`

Expected: FAIL because confirm does not mutate real stock yet.

**Step 3: Write minimal implementation**

Make hold confirmation load `InventoryHoldItem`s and apply guarded stock mutation per SKU:

- `reserved -= quantity`
- `onHand -= quantity`

Then move hold status to `CONFIRMED`.

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "*PaymentFinalizationServiceStockDecrementTest"`

Expected: PASS.

**Step 5: Commit**

```bash
git add src/main/kotlin/com/paymentlab/payment/application/PaymentFinalizationService.kt src/main/kotlin/com/paymentlab/inventory/application/InventoryHoldApplicationService.kt src/main/kotlin/com/paymentlab/inventory/infrastructure/persistence/SkuStockRepository.kt src/main/kotlin/com/paymentlab/inventory/infrastructure/persistence/InventoryHoldItemRepository.kt src/test/kotlin/com/paymentlab/payment/application/PaymentFinalizationServiceStockDecrementTest.kt
git commit -m "feat: confirm holds by decrementing stock"
```

### Task 6: Release and expire linked hold by restoring reserved quantity

**Files:**
- Modify: `src/main/kotlin/com/paymentlab/payment/application/PaymentFinalizationService.kt`
- Modify: `src/main/kotlin/com/paymentlab/inventory/application/InventoryHoldApplicationService.kt`
- Modify: `src/main/kotlin/com/paymentlab/inventory/application/PendingInventoryHoldExpirationScheduler.kt`
- Modify: `src/main/kotlin/com/paymentlab/inventory/infrastructure/persistence/SkuStockRepository.kt`
- Test: `src/test/kotlin/com/paymentlab/payment/application/PaymentFinalizationServiceReleaseStockTest.kt`
- Test: `src/test/kotlin/com/paymentlab/inventory/application/PendingInventoryHoldExpirationSchedulerStockTest.kt`

**Step 1: Write the failing tests**

Add tests proving:

1. payment failure releases reserved stock without changing `onHand`
2. expiration releases reserved stock without changing `onHand`

**Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "*PaymentFinalizationServiceReleaseStockTest" --tests "*PendingInventoryHoldExpirationSchedulerStockTest"`

Expected: FAIL because release/expire do not restore reserved stock yet.

**Step 3: Write minimal implementation**

For release and expire:

- load hold items
- decrement `reserved` only
- keep `onHand` unchanged
- move hold to `RELEASED` or `EXPIRED`

**Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "*PaymentFinalizationServiceReleaseStockTest" --tests "*PendingInventoryHoldExpirationSchedulerStockTest"`

Expected: PASS.

**Step 5: Commit**

```bash
git add src/main/kotlin/com/paymentlab/payment/application/PaymentFinalizationService.kt src/main/kotlin/com/paymentlab/inventory/application/InventoryHoldApplicationService.kt src/main/kotlin/com/paymentlab/inventory/application/PendingInventoryHoldExpirationScheduler.kt src/main/kotlin/com/paymentlab/inventory/infrastructure/persistence/SkuStockRepository.kt src/test/kotlin/com/paymentlab/payment/application/PaymentFinalizationServiceReleaseStockTest.kt src/test/kotlin/com/paymentlab/inventory/application/PendingInventoryHoldExpirationSchedulerStockTest.kt
git commit -m "feat: release reserved stock on hold failure"
```

### Task 7: Add insufficient stock API behavior

**Files:**
- Modify: `src/main/kotlin/com/paymentlab/payment/api/PaymentErrorHandler.kt`
- Modify: `src/main/kotlin/com/paymentlab/payment/api/PaymentController.kt`
- Modify: `src/main/kotlin/com/paymentlab/inventory/application/InventoryHoldApplicationService.kt`
- Test: `src/test/kotlin/com/paymentlab/payment/api/PaymentApiStockFailureTest.kt`

**Step 1: Write the failing test**

Add an API test proving payment start fails with a clear client-facing error when stock reservation cannot be made.

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "*PaymentApiStockFailureTest"`

Expected: FAIL because no explicit stock failure mapping exists.

**Step 3: Write minimal implementation**

Throw a specific inventory reservation exception and map it to a stable API error response.

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "*PaymentApiStockFailureTest"`

Expected: PASS.

**Step 5: Commit**

```bash
git add src/main/kotlin/com/paymentlab/payment/api/PaymentErrorHandler.kt src/main/kotlin/com/paymentlab/payment/api/PaymentController.kt src/main/kotlin/com/paymentlab/inventory/application/InventoryHoldApplicationService.kt src/test/kotlin/com/paymentlab/payment/api/PaymentApiStockFailureTest.kt
git commit -m "feat: expose stock reservation failures"
```

### Task 8: Update documentation

**Files:**
- Modify: `README.md`
- Modify: `docs/plans/2026-04-08-stock-decrement-design.md`

**Step 1: Write doc checklist**

List exact points to update:

1. order items and SKU stock now exist
2. hold creation reserves stock
3. payment success decrements `onHand`
4. release/expire restore `reserved`

**Step 2: Update docs minimally**

Reflect real implementation only. Do not document warehouse-level or refund-level behavior unless implemented.

**Step 3: Run doc sanity check**

Read the updated docs and confirm they match actual code behavior.

**Step 4: Commit**

```bash
git add README.md docs/plans/2026-04-08-stock-decrement-design.md
git commit -m "docs: document stock decrement flow"
```

### Task 9: Full verification

**Files:**
- Verify only

**Step 1: Run targeted tests**

Run:

```bash
./gradlew test --tests "*OrderItemPersistenceTest" \
  --tests "*SkuStockPersistenceTest" \
  --tests "*InventoryHoldItemPersistenceTest" \
  --tests "*InventoryHoldReservationServiceTest" \
  --tests "*PaymentFinalizationServiceStockDecrementTest" \
  --tests "*PaymentFinalizationServiceReleaseStockTest" \
  --tests "*PendingInventoryHoldExpirationSchedulerStockTest" \
  --tests "*PaymentApiStockFailureTest"
```

Expected: PASS.

**Step 2: Run full suite**

Run: `./gradlew test`

Expected: PASS.

**Step 3: Manual sanity path**

Verify this scenario end-to-end:

1. create order with items
2. seed SKU stock
3. start payment → reserved increases
4. payment success → reserved decreases, onHand decreases
5. another order fails payment → reserved decreases, onHand unchanged
6. stale hold expires → reserved decreases, onHand unchanged

**Step 4: Commit final verification checkpoint**

```bash
git status
```
