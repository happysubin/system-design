# Multi-Seller Settlement Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 하나의 `PaymentOrder` 안에 여러 seller/payee 상품이 포함되더라도 더치페이와 복합 결제를 유지하면서 seller 기준 정산이 가능하도록 도메인 모델, 원장, 정산 배치, 취소/환불 흐름을 확장한다.

**Architecture:** `PaymentOrder`는 상위 주문 컨텍스트로 유지하고, seller/payee 축은 `PaymentOrderLine`으로 분리한다. 실제 승인 금액의 seller 귀속은 `AuthorizationLinePortion`으로 보존하고, 원장은 `LedgerEntry.payeeId`와 `paymentOrderLine` 기준으로 append-only 사실을 남긴다. 정산은 더 이상 `PaymentOrder.merchantId`가 아니라 원장에 남은 `payeeId`를 기준으로 집계한다.

**Tech Stack:** Kotlin, Spring Boot, Spring Data JPA, H2, JUnit 5

---

### Task 1: seller/payee 축 엔티티 추가

**Files:**
- Create: `src/main/kotlin/com/pglab/payment/order/PaymentOrderLine.kt`
- Modify: `src/main/kotlin/com/pglab/payment/order/PaymentOrder.kt`
- Test: `src/test/kotlin/com/pglab/payment/order/PaymentOrderLineTest.kt`
- Modify: `src/test/kotlin/com/pglab/payment/order/PaymentOrderTest.kt`
- Modify: `src/test/kotlin/com/pglab/payment/jpa/PaymentJpaMappingTest.kt`

**Step 1: Write the failing test**

```kotlin
package com.pglab.payment.order

import com.pglab.payment.shared.CurrencyCode
import com.pglab.payment.shared.Money
import kotlin.test.Test
import kotlin.test.assertEquals

class PaymentOrderLineTest {
    @Test
    fun `order line should keep payee and amount`() {
        val order = PaymentOrder(
            merchantId = "platform-merchant",
            merchantOrderId = "order-001",
            totalAmount = Money(50_000L, CurrencyCode.KRW),
        )
        val line = PaymentOrderLine(
            paymentOrder = order,
            lineReference = "line-1",
            payeeId = "seller-A",
            lineAmount = Money(30_000L, CurrencyCode.KRW),
            quantity = 1,
        )

        assertEquals("seller-A", line.payeeId)
        assertEquals(30_000L, line.lineAmount.amount)
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests com.pglab.payment.order.PaymentOrderLineTest`

Expected: FAIL because `PaymentOrderLine` does not exist

**Step 3: Write minimal implementation**

- `PaymentOrderLine`를 `@Entity`로 추가한다.
- `PaymentOrder`에 `lines` 관계를 추가한다.
- `payeeId`, `lineReference`, `lineAmount`, `quantity`를 구현한다.
- JPA 매핑 테스트에서 `PaymentOrder` 저장 시 line까지 영속화되는지 확인한다.

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests com.pglab.payment.order.PaymentOrderLineTest --tests com.pglab.payment.jpa.PaymentJpaMappingTest`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/kotlin/com/pglab/payment/order/PaymentOrder.kt src/main/kotlin/com/pglab/payment/order/PaymentOrderLine.kt src/test/kotlin/com/pglab/payment/order/PaymentOrderLineTest.kt src/test/kotlin/com/pglab/payment/order/PaymentOrderTest.kt src/test/kotlin/com/pglab/payment/jpa/PaymentJpaMappingTest.kt
git commit -m "feat: add payee-aware payment order lines"
```

### Task 2: 승인 금액의 seller 귀속 모델 추가

**Files:**
- Create: `src/main/kotlin/com/pglab/payment/authorization/AuthorizationLinePortion.kt`
- Modify: `src/main/kotlin/com/pglab/payment/authorization/Authorization.kt`
- Test: `src/test/kotlin/com/pglab/payment/authorization/AuthorizationLinePortionTest.kt`
- Modify: `src/test/kotlin/com/pglab/payment/authorization/AuthorizationTest.kt`
- Modify: `src/test/kotlin/com/pglab/payment/jpa/PaymentJpaMappingTest.kt`

**Step 1: Write the failing test**

```kotlin
package com.pglab.payment.authorization

import com.pglab.payment.order.PaymentOrder
import com.pglab.payment.order.PaymentOrderLine
import com.pglab.payment.allocation.PaymentAllocation
import com.pglab.payment.shared.CurrencyCode
import com.pglab.payment.shared.Money
import kotlin.test.Test
import kotlin.test.assertEquals

class AuthorizationLinePortionTest {
    @Test
    fun `authorization line portion should keep payee allocation amount`() {
        val order = PaymentOrder(merchantId = "platform", merchantOrderId = "order-001", totalAmount = Money(50_000L, CurrencyCode.KRW))
        val line = PaymentOrderLine(paymentOrder = order, lineReference = "line-1", payeeId = "seller-A", lineAmount = Money(30_000L, CurrencyCode.KRW), quantity = 1)
        val allocation = PaymentAllocation(paymentOrder = order, payerReference = "payer-A", allocationAmount = Money(50_000L, CurrencyCode.KRW), sequence = 1)
        val authorization = Authorization(paymentAllocation = allocation, instrumentType = InstrumentType.CARD, requestedAmount = Money(50_000L, CurrencyCode.KRW), approvedAmount = Money(50_000L, CurrencyCode.KRW), pgTransactionId = "pg-1")
        val portion = AuthorizationLinePortion(authorization = authorization, paymentOrderLine = line, payeeId = "seller-A", amount = Money(30_000L, CurrencyCode.KRW), sequence = 1)

        assertEquals("seller-A", portion.payeeId)
        assertEquals(30_000L, portion.amount.amount)
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests com.pglab.payment.authorization.AuthorizationLinePortionTest`

Expected: FAIL because `AuthorizationLinePortion` does not exist

**Step 3: Write minimal implementation**

- `AuthorizationLinePortion`를 `Authorization 1:N`, `PaymentOrderLine N:1` 관계로 구현한다.
- `Authorization`에 `linePortions` 컬렉션을 추가한다.
- `payeeId`와 `amount`를 snapshot 필드로 둔다.
- JPA 매핑 테스트를 line portion까지 확장한다.

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests com.pglab.payment.authorization.AuthorizationLinePortionTest --tests com.pglab.payment.jpa.PaymentJpaMappingTest`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/kotlin/com/pglab/payment/authorization/Authorization.kt src/main/kotlin/com/pglab/payment/authorization/AuthorizationLinePortion.kt src/test/kotlin/com/pglab/payment/authorization/AuthorizationLinePortionTest.kt src/test/kotlin/com/pglab/payment/authorization/AuthorizationTest.kt src/test/kotlin/com/pglab/payment/jpa/PaymentJpaMappingTest.kt
git commit -m "feat: add authorization line portions for seller attribution"
```

### Task 3: authorize API와 서비스에 line-aware 입력 추가

**Files:**
- Modify: `src/main/kotlin/com/pglab/payment/api/AuthorizePaymentController.kt`
- Modify: `src/main/kotlin/com/pglab/payment/authorization/AuthorizePaymentService.kt`
- Modify: `src/main/kotlin/com/pglab/payment/authorization/AuthorizationFacade.kt`
- Modify: `src/test/kotlin/com/pglab/payment/api/AuthorizePaymentApiTest.kt`
- Modify: `src/test/kotlin/com/pglab/payment/authorization/AuthorizePaymentServiceTest.kt`
- Modify: `src/test/kotlin/com/pglab/payment/authorization/MixedPaymentAuthorizationServiceTest.kt`
- Modify: `src/test/kotlin/com/pglab/payment/domain/PaymentDomainInvariantTest.kt`

**Step 1: Write the failing test**

```kotlin
@Test
fun `authorize request should accept order lines and authorization line portions`() {
    // one order, two sellers, one payer, one authorization split across two lines
    // expected: service result contains order lines and line portions, and validation passes
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests com.pglab.payment.api.AuthorizePaymentApiTest --tests com.pglab.payment.authorization.AuthorizePaymentServiceTest`

Expected: FAIL because request/command fields are missing

**Step 3: Write minimal implementation**

- `AuthorizePaymentApiRequest`에 `lines`를 추가한다.
- `AuthorizationRequest` 입력 모델에 `linePortions`를 추가한다.
- `AuthorizePaymentCommand`에 `lines`와 line-portion 정보를 추가한다.
- 아래 불변식을 구현한다.
  - order total = sum(order lines)
  - order total = sum(allocations)
  - authorization approved amount = sum(line portions)
  - unknown line reference 금지
- `AuthorizePaymentResult`에 생성된 `orderLines`, `authorizationLinePortions`를 포함시킨다.

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests com.pglab.payment.api.AuthorizePaymentApiTest --tests com.pglab.payment.authorization.AuthorizePaymentServiceTest --tests com.pglab.payment.authorization.MixedPaymentAuthorizationServiceTest --tests com.pglab.payment.domain.PaymentDomainInvariantTest`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/kotlin/com/pglab/payment/api/AuthorizePaymentController.kt src/main/kotlin/com/pglab/payment/authorization/AuthorizePaymentService.kt src/main/kotlin/com/pglab/payment/authorization/AuthorizationFacade.kt src/test/kotlin/com/pglab/payment/api/AuthorizePaymentApiTest.kt src/test/kotlin/com/pglab/payment/authorization/AuthorizePaymentServiceTest.kt src/test/kotlin/com/pglab/payment/authorization/MixedPaymentAuthorizationServiceTest.kt src/test/kotlin/com/pglab/payment/domain/PaymentDomainInvariantTest.kt
git commit -m "feat: accept payee-aware order lines in authorize flow"
```

### Task 4: seller-aware 원장 생성으로 authorize 결과 변경

**Files:**
- Modify: `src/main/kotlin/com/pglab/payment/ledger/LedgerEntry.kt`
- Modify: `src/main/kotlin/com/pglab/payment/authorization/AuthorizePaymentService.kt`
- Modify: `src/main/kotlin/com/pglab/payment/authorization/JpaAuthorizePaymentWriter.kt`
- Modify: `src/test/kotlin/com/pglab/payment/ledger/LedgerEntryTest.kt`
- Modify: `src/test/kotlin/com/pglab/payment/authorization/AuthorizePaymentServiceTest.kt`
- Modify: `src/test/kotlin/com/pglab/payment/jpa/PaymentJpaMappingTest.kt`

**Step 1: Write the failing test**

```kotlin
@Test
fun `authorize should create ledger entries per authorization line portion`() {
    // expected: one authorization with two line portions -> two AUTH_CAPTURED ledger entries
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests com.pglab.payment.authorization.AuthorizePaymentServiceTest --tests com.pglab.payment.ledger.LedgerEntryTest`

Expected: FAIL because ledger entry does not keep `paymentOrderLine` and `payeeId`

**Step 3: Write minimal implementation**

- `LedgerEntry`에 `paymentOrderLine`와 `payeeId`를 추가한다.
- `AuthorizePaymentService`가 authorization마다 ledger 1개를 만들던 로직을 없애고,
  `AuthorizationLinePortion`마다 `AUTH_CAPTURED` ledger 1개를 만들도록 바꾼다.
- writer가 새 관계까지 영속화하도록 수정한다.

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests com.pglab.payment.authorization.AuthorizePaymentServiceTest --tests com.pglab.payment.ledger.LedgerEntryTest --tests com.pglab.payment.jpa.PaymentJpaMappingTest`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/kotlin/com/pglab/payment/ledger/LedgerEntry.kt src/main/kotlin/com/pglab/payment/authorization/AuthorizePaymentService.kt src/main/kotlin/com/pglab/payment/authorization/JpaAuthorizePaymentWriter.kt src/test/kotlin/com/pglab/payment/ledger/LedgerEntryTest.kt src/test/kotlin/com/pglab/payment/authorization/AuthorizePaymentServiceTest.kt src/test/kotlin/com/pglab/payment/jpa/PaymentJpaMappingTest.kt
git commit -m "feat: create seller-aware ledger entries from authorization line portions"
```

### Task 5: settlement를 payee 기준으로 전환

**Files:**
- Modify: `src/main/kotlin/com/pglab/payment/settlement/Settlement.kt`
- Modify: `src/main/kotlin/com/pglab/payment/settlement/SettlementBatchService.kt`
- Modify: `src/main/kotlin/com/pglab/payment/settlement/JpaSettlementLedgerReader.kt`
- Modify: `src/main/kotlin/com/pglab/payment/settlement/SettlementLine.kt`
- Modify: `src/main/kotlin/com/pglab/payment/settlement/Payout.kt`
- Modify: `src/test/kotlin/com/pglab/payment/settlement/SettlementTest.kt`
- Modify: `src/test/kotlin/com/pglab/payment/settlement/SettlementBatchServiceTest.kt`
- Modify: `src/test/kotlin/com/pglab/payment/api/SettlementBatchApiTest.kt`
- Modify: `src/test/kotlin/com/pglab/payment/settlement/PayoutTest.kt`
- Modify: `src/test/kotlin/com/pglab/payment/settlement/PayoutServiceTest.kt`

**Step 1: Write the failing test**

```kotlin
@Test
fun `settlement batch should group ledger entries by payee id`() {
    // one order with seller-A and seller-B ledger entries
    // expected: two settlements keyed by payee
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests com.pglab.payment.settlement.SettlementBatchServiceTest --tests com.pglab.payment.api.SettlementBatchApiTest`

Expected: FAIL because settlement still groups by merchant id

**Step 3: Write minimal implementation**

- `Settlement`의 핵심 식별 축을 `payeeId`로 바꾼다.
- `SettlementBatchService`를 `groupBy { it.payeeId }`로 변경한다.
- `SettlementLedgerRecord`도 `merchantId` 대신 `payeeId`를 담게 바꾼다.
- `JpaSettlementLedgerReader`가 `LedgerEntry.payeeId`를 읽도록 바꾼다.
- payout 관련 테스트를 새 settlement 기준에 맞게 수정한다.

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests com.pglab.payment.settlement.SettlementTest --tests com.pglab.payment.settlement.SettlementBatchServiceTest --tests com.pglab.payment.api.SettlementBatchApiTest --tests com.pglab.payment.settlement.PayoutTest --tests com.pglab.payment.settlement.PayoutServiceTest`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/kotlin/com/pglab/payment/settlement/Settlement.kt src/main/kotlin/com/pglab/payment/settlement/SettlementBatchService.kt src/main/kotlin/com/pglab/payment/settlement/JpaSettlementLedgerReader.kt src/main/kotlin/com/pglab/payment/settlement/SettlementLine.kt src/main/kotlin/com/pglab/payment/settlement/Payout.kt src/test/kotlin/com/pglab/payment/settlement/SettlementTest.kt src/test/kotlin/com/pglab/payment/settlement/SettlementBatchServiceTest.kt src/test/kotlin/com/pglab/payment/api/SettlementBatchApiTest.kt src/test/kotlin/com/pglab/payment/settlement/PayoutTest.kt src/test/kotlin/com/pglab/payment/settlement/PayoutServiceTest.kt
git commit -m "feat: settle payouts by payee instead of order merchant"
```

### Task 6: 취소와 환불을 seller-aware 음수 원장으로 확장

**Files:**
- Modify: `src/main/kotlin/com/pglab/payment/authorization/PartialCancellationService.kt`
- Modify: `src/main/kotlin/com/pglab/payment/authorization/JpaPartialCancellationWriter.kt`
- Modify: `src/main/kotlin/com/pglab/payment/authorization/RefundService.kt`
- Modify: `src/main/kotlin/com/pglab/payment/authorization/JpaRefundWriter.kt`
- Modify: `src/test/kotlin/com/pglab/payment/authorization/PartialCancellationServiceTest.kt`
- Modify: `src/test/kotlin/com/pglab/payment/authorization/RefundServiceTest.kt`
- Modify: `src/test/kotlin/com/pglab/payment/api/PartialCancellationApiTest.kt`
- Modify: `src/test/kotlin/com/pglab/payment/api/RefundApiTest.kt`

**Step 1: Write the failing test**

```kotlin
@Test
fun `partial cancel should create negative ledger entries for each affected payee`() {
    // expected: canceling one authorization with two original line portions creates two CANCELLED ledger entries
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests com.pglab.payment.authorization.PartialCancellationServiceTest --tests com.pglab.payment.authorization.RefundServiceTest`

Expected: FAIL because cancel/refund still writes one ledger entry without payee breakdown

**Step 3: Write minimal implementation**

- `PartialCancellationService`와 `RefundService`가 기존 authorization의 `AuthorizationLinePortion` 목록을 읽도록 한다.
- 요청이 line breakdown을 직접 주지 않는 현재 API 기준에서는 기존 portion 비율로 금액을 분배한다.
- 반올림/잔차 규칙은 마지막 portion이 잔여 금액을 흡수하도록 고정한다.
- 취소/환불 writer가 여러 ledger entry를 저장하도록 결과 모델을 확장한다.

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests com.pglab.payment.authorization.PartialCancellationServiceTest --tests com.pglab.payment.authorization.RefundServiceTest --tests com.pglab.payment.api.PartialCancellationApiTest --tests com.pglab.payment.api.RefundApiTest`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/kotlin/com/pglab/payment/authorization/PartialCancellationService.kt src/main/kotlin/com/pglab/payment/authorization/JpaPartialCancellationWriter.kt src/main/kotlin/com/pglab/payment/authorization/RefundService.kt src/main/kotlin/com/pglab/payment/authorization/JpaRefundWriter.kt src/test/kotlin/com/pglab/payment/authorization/PartialCancellationServiceTest.kt src/test/kotlin/com/pglab/payment/authorization/RefundServiceTest.kt src/test/kotlin/com/pglab/payment/api/PartialCancellationApiTest.kt src/test/kotlin/com/pglab/payment/api/RefundApiTest.kt
git commit -m "feat: keep seller attribution in cancel and refund ledgers"
```

### Task 7: 전체 회귀 검증과 문서 정리

**Files:**
- Modify: `README.md`
- Modify: `docs/plans/2026-04-18-multi-seller-settlement-design.md`
- Modify: `docs/plans/2026-04-18-multi-seller-settlement-implementation-plan.md`

**Step 1: Write the failing documentation/test expectation**

- README와 설계서가 여전히 settlement를 `merchantId` 중심으로 설명하는 부분을 체크리스트로 적는다.
- 멀티 셀러 회귀 테스트 목록을 문서에 적는다.

**Step 2: Run verification to expose remaining gaps**

Run: `./gradlew test`

Expected: Any remaining failures should point to unupdated assumptions or missed tests

**Step 3: Write minimal updates**

- README의 핵심 개념과 구현 상태를 payee-aware 정산 기준으로 수정한다.
- 설계서와 구현 계획에서 최종 실제 구조와 어긋나는 설명을 정리한다.

**Implementation note:** 실제 구현에서는 취소/환불 분배가 단순 비율 재사용을 넘어서, 기존 `CANCELLED`/`REFUNDED` 원장 이력을 반영한 remaining-capacity 기준 분배로 구체화되었다. 또한 0원 음수 원장은 저장하지 않는다.

**Step 4: Run test to verify it passes**

Run: `./gradlew test`

Expected: PASS

**Step 5: Commit**

```bash
git add README.md docs/plans/2026-04-18-multi-seller-settlement-design.md docs/plans/2026-04-18-multi-seller-settlement-implementation-plan.md
git commit -m "docs: document payee-aware multi-seller settlement model"
```

Plan complete and saved to `docs/plans/2026-04-18-multi-seller-settlement-implementation-plan.md`. Two execution options:

**1. Subagent-Driven (this session)** - I dispatch fresh subagent per task, review between tasks, fast iteration

**2. Parallel Session (separate)** - Open new session with executing-plans, batch execution with checkpoints

**Which approach?**
