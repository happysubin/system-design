# Order Package Boundary Cleanup Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Move the `Order` aggregate and its supporting repository/service/controller/DTO code out of `payment.*` and into `order.*` without changing behavior.

**Architecture:** Treat this as a package-boundary refactor, not a business-logic rewrite. Move order-owned code together, then update all imports/tests that reference the old `payment` package path, and finally refresh docs to reflect the corrected boundary.

**Tech Stack:** Kotlin, Spring Boot, Spring Data JPA, JUnit 5, MockMvc.

---

### Task 1: Move the Order aggregate and repository

**Files:**
- Create: `src/main/kotlin/com/paymentlab/order/domain/Order.kt`
- Create: `src/main/kotlin/com/paymentlab/order/infrastructure/persistence/OrderRepository.kt`
- Delete/Replace: `src/main/kotlin/com/paymentlab/payment/domain/Order.kt`
- Delete/Replace: `src/main/kotlin/com/paymentlab/payment/infrastructure/persistence/OrderRepository.kt`
- Test: `src/test/kotlin/com/paymentlab/order/infrastructure/persistence/OrderItemPersistenceTest.kt`
- Test: `src/test/kotlin/com/paymentlab/payment/infrastructure/persistence/PersistenceConstraintTest.kt`

**Step 1: Write the failing test**

Add or update a structure/import-level test so the order persistence tests compile only against `com.paymentlab.order.domain.Order` and `com.paymentlab.order.infrastructure.persistence.OrderRepository`.

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "*OrderItemPersistenceTest" --tests "*PersistenceConstraintTest"`

Expected: FAIL due to old package references.

**Step 3: Write minimal implementation**

Move `Order` and `OrderRepository` into the `order` package and update direct imports in the affected tests.

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "*OrderItemPersistenceTest" --tests "*PersistenceConstraintTest"`

Expected: PASS.

**Step 5: Commit**

```bash
git add src/main/kotlin/com/paymentlab/order/domain/Order.kt src/main/kotlin/com/paymentlab/order/infrastructure/persistence/OrderRepository.kt src/test/kotlin/com/paymentlab/order/infrastructure/persistence/OrderItemPersistenceTest.kt src/test/kotlin/com/paymentlab/payment/infrastructure/persistence/PersistenceConstraintTest.kt
git commit -m "refactor: order aggregate를 order 패키지로 이동"
```

### Task 2: Move order service and DTOs

**Files:**
- Create: `src/main/kotlin/com/paymentlab/order/application/OrderApplicationService.kt`
- Create: `src/main/kotlin/com/paymentlab/order/api/dto/CreateOrderRequest.kt`
- Create: `src/main/kotlin/com/paymentlab/order/api/dto/CreateOrderResponse.kt`
- Delete/Replace: `src/main/kotlin/com/paymentlab/payment/application/OrderApplicationService.kt`
- Delete/Replace: `src/main/kotlin/com/paymentlab/payment/api/dto/CreateOrderRequest.kt`
- Delete/Replace: `src/main/kotlin/com/paymentlab/payment/api/dto/CreateOrderResponse.kt`
- Test: `src/test/kotlin/com/paymentlab/payment/application/OrderApplicationServiceTest.kt`
- Test: `src/test/kotlin/com/paymentlab/payment/api/OrderApiTest.kt`

**Step 1: Write the failing test**

Update the order service/API tests to import the new `order.*` service and DTO package paths.

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "*OrderApplicationServiceTest" --tests "*OrderApiTest"`

Expected: FAIL due to old imports or missing moved files.

**Step 3: Write minimal implementation**

Move the service and DTO classes to `order.*` without changing request/response behavior.

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "*OrderApplicationServiceTest" --tests "*OrderApiTest"`

Expected: PASS.

**Step 5: Commit**

```bash
git add src/main/kotlin/com/paymentlab/order/application/OrderApplicationService.kt src/main/kotlin/com/paymentlab/order/api/dto/CreateOrderRequest.kt src/main/kotlin/com/paymentlab/order/api/dto/CreateOrderResponse.kt src/test/kotlin/com/paymentlab/payment/application/OrderApplicationServiceTest.kt src/test/kotlin/com/paymentlab/payment/api/OrderApiTest.kt
git commit -m "refactor: order 서비스와 DTO를 order 패키지로 이동"
```

### Task 3: Move the controller boundary

**Files:**
- Create: `src/main/kotlin/com/paymentlab/order/api/OrderController.kt`
- Delete/Replace: `src/main/kotlin/com/paymentlab/payment/api/OrderController.kt`
- Test: `src/test/kotlin/com/paymentlab/payment/api/OrderApiTest.kt`

**Step 1: Write the failing test**

Update the controller-based order API test to instantiate/import the controller from `order.api`.

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "*OrderApiTest"`

Expected: FAIL due to old controller package reference.

**Step 3: Write minimal implementation**

Move `OrderController` into `com.paymentlab.order.api` and update imports only.

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "*OrderApiTest"`

Expected: PASS.

**Step 5: Commit**

```bash
git add src/main/kotlin/com/paymentlab/order/api/OrderController.kt src/test/kotlin/com/paymentlab/payment/api/OrderApiTest.kt
git commit -m "refactor: order controller를 order 패키지로 이동"
```

### Task 4: Update inventory imports to the new order boundary

**Files:**
- Modify: `src/main/kotlin/com/paymentlab/inventory/application/InventoryHoldApplicationService.kt`
- Modify: `src/test/kotlin/com/paymentlab/inventory/application/InventoryHoldApplicationServiceTest.kt`
- Modify: `src/test/kotlin/com/paymentlab/inventory/application/PendingInventoryHoldExpirationSchedulerTest.kt`

**Step 1: Write the failing test**

Run the inventory tests that currently import `payment.domain.Order` / old repository package.

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "*InventoryHoldApplicationServiceTest" --tests "*PendingInventoryHoldExpirationSchedulerTest"`

Expected: FAIL due to old order package references.

**Step 3: Write minimal implementation**

Update imports to use `order.domain.Order` and `order.infrastructure.persistence.OrderRepository`.

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "*InventoryHoldApplicationServiceTest" --tests "*PendingInventoryHoldExpirationSchedulerTest"`

Expected: PASS.

**Step 5: Commit**

```bash
git add src/main/kotlin/com/paymentlab/inventory/application/InventoryHoldApplicationService.kt src/test/kotlin/com/paymentlab/inventory/application/InventoryHoldApplicationServiceTest.kt src/test/kotlin/com/paymentlab/inventory/application/PendingInventoryHoldExpirationSchedulerTest.kt
git commit -m "refactor: inventory에서 order 경계 참조 정리"
```

### Task 5: Update payment imports to depend on order boundary cleanly

**Files:**
- Modify: any payment-side files/tests still importing moved order types
- Likely test files: `src/test/kotlin/com/paymentlab/payment/PaymentLayerStructureTest.kt` and any payment tests still referencing old order package paths

**Step 1: Write the failing test**

Run payment-side tests that reference old order package names.

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "*PaymentLayerStructureTest" --tests "*PaymentApiTest" --tests "*PaymentWebhookApiTest"`

Expected: Any old package assumptions fail here.

**Step 3: Write minimal implementation**

Update imports and structure assertions so payment depends on order as an external package, not as an internal payment-domain class.

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "*PaymentLayerStructureTest" --tests "*PaymentApiTest" --tests "*PaymentWebhookApiTest"`

Expected: PASS.

**Step 5: Commit**

```bash
git add src/test/kotlin/com/paymentlab/payment/PaymentLayerStructureTest.kt src/test/kotlin/com/paymentlab/payment/**/*.kt
git commit -m "refactor: payment에서 order 패키지 의존 정리"
```

### Task 6: Update documentation

**Files:**
- Modify: `README.md`
- Modify: `docs/plans/2026-04-08-order-package-boundary-design.md`

**Step 1: Write doc checklist**

List exact points to update:

1. local order creation API belongs to `order` boundary
2. payment starts from an existing order instead of owning order aggregate
3. inventory references order through the `order` package

**Step 2: Update docs minimally**

Reflect the package-boundary cleanup only. Do not overstate architectural separation beyond what the code actually enforces.

**Step 3: Run doc sanity check**

Read the updated docs and confirm they match the code layout.

**Step 4: Commit**

```bash
git add README.md docs/plans/2026-04-08-order-package-boundary-design.md
git commit -m "docs: order 패키지 경계 정리 반영"
```

### Task 7: Full verification

**Files:**
- Verify only

**Step 1: Run targeted tests**

Run:

```bash
./gradlew test --tests "*OrderItemPersistenceTest" \
  --tests "*OrderApplicationServiceTest" \
  --tests "*OrderApiTest" \
  --tests "*InventoryHoldApplicationServiceTest" \
  --tests "*PendingInventoryHoldExpirationSchedulerTest" \
  --tests "*PaymentLayerStructureTest"
```

Expected: PASS.

**Step 2: Run full suite**

Run: `./gradlew test`

Expected: PASS.

**Step 3: Manual sanity check**

Verify that package paths are consistent:

- order aggregate/service/controller/DTO are all under `order.*`
- payment no longer owns `Order`
- inventory imports order from `order.*`

**Step 4: Commit final verification checkpoint**

```bash
git status
```
