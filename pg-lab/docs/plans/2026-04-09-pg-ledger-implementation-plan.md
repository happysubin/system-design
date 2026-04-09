# PG 원장/승인 도메인 모델 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 부분 취소, 더치페이, 복합 결제를 지원할 수 있도록 `PaymentOrder`, `PaymentAllocation`, `Authorization`, `LedgerEntry`, `Settlement` 중심의 JPA 도메인 모델을 구현한다.

**Architecture:** 결제 의도, 승인 결과, 금전 원장을 분리하는 구조를 사용한다. `PaymentOrder`는 상위 비즈니스 컨텍스트를 맡고, `Authorization`은 외부 승인 결과를 표현하며, `LedgerEntry`는 append-only 원장을 담당한다.

**Tech Stack:** Kotlin, Spring Boot, Spring Data JPA, H2, JUnit 5

---

### Task 1: 패키지 구조와 기본 테스트 스캐폴드 만들기

**Files:**
- Create: `src/test/kotlin/com/pglab/domain/DomainStructureSmokeTest.kt`
- Create: `src/main/kotlin/com/pglab/payment/`
- Create: `src/main/kotlin/com/pglab/payment/order/`
- Create: `src/main/kotlin/com/pglab/payment/allocation/`
- Create: `src/main/kotlin/com/pglab/payment/authorization/`
- Create: `src/main/kotlin/com/pglab/payment/ledger/`
- Create: `src/main/kotlin/com/pglab/payment/settlement/`

**Step 1: Write the failing test**

```kotlin
package com.pglab.domain

import kotlin.test.Test

class DomainStructureSmokeTest {
    @Test
    fun `payment domain packages should be available`() {
        Class.forName("com.pglab.payment.order.PaymentOrder")
        Class.forName("com.pglab.payment.allocation.PaymentAllocation")
        Class.forName("com.pglab.payment.authorization.Authorization")
        Class.forName("com.pglab.payment.ledger.LedgerEntry")
        Class.forName("com.pglab.payment.settlement.Settlement")
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests com.pglab.domain.DomainStructureSmokeTest`

Expected: FAIL with `ClassNotFoundException`

**Step 3: Write minimal implementation**

- 위 다섯 클래스를 빈 클래스로 우선 생성한다.
- 패키지명은 테스트의 FQCN과 정확히 맞춘다.

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests com.pglab.domain.DomainStructureSmokeTest`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/kotlin/com/pglab/payment src/test/kotlin/com/pglab/domain/DomainStructureSmokeTest.kt
git commit -m "feat: 결제 도메인 기본 패키지 구조 추가"
```

### Task 2: 공통 enum과 값 객체 도입

**Files:**
- Create: `src/main/kotlin/com/pglab/payment/shared/Money.kt`
- Create: `src/main/kotlin/com/pglab/payment/shared/CurrencyCode.kt`
- Create: `src/main/kotlin/com/pglab/payment/order/PaymentOrderStatus.kt`
- Create: `src/main/kotlin/com/pglab/payment/allocation/PaymentAllocationStatus.kt`
- Create: `src/main/kotlin/com/pglab/payment/authorization/AuthorizationStatus.kt`
- Create: `src/main/kotlin/com/pglab/payment/authorization/InstrumentType.kt`
- Create: `src/main/kotlin/com/pglab/payment/ledger/LedgerEntryType.kt`
- Create: `src/main/kotlin/com/pglab/payment/settlement/SettlementStatus.kt`
- Test: `src/test/kotlin/com/pglab/payment/shared/MoneyTest.kt`

**Step 1: Write the failing test**

```kotlin
package com.pglab.payment.shared

import kotlin.test.Test
import kotlin.test.assertFailsWith

class MoneyTest {
    @Test
    fun `money should reject negative amount`() {
        assertFailsWith<IllegalArgumentException> {
            Money(amount = -1L, currency = CurrencyCode.KRW)
        }
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests com.pglab.payment.shared.MoneyTest`

Expected: FAIL because `Money` does not exist

**Step 3: Write minimal implementation**

- `Money`를 `@Embeddable` 후보 형태로 만든다.
- `amount`는 `Long` 또는 `BigDecimal` 중 하나로 일관되게 선택한다. 초기 버전은 KRW 중심이면 `Long`이 단순하다.
- 음수 방지 검증을 생성 시점에 넣는다.
- enum은 설계서 기준 이름으로 모두 만든다.

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests com.pglab.payment.shared.MoneyTest`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/kotlin/com/pglab/payment/shared src/main/kotlin/com/pglab/payment/order/PaymentOrderStatus.kt src/main/kotlin/com/pglab/payment/allocation/PaymentAllocationStatus.kt src/main/kotlin/com/pglab/payment/authorization src/main/kotlin/com/pglab/payment/ledger/LedgerEntryType.kt src/main/kotlin/com/pglab/payment/settlement/SettlementStatus.kt src/test/kotlin/com/pglab/payment/shared/MoneyTest.kt
git commit -m "feat: 결제 도메인 공통 enum과 금액 값 객체 추가"
```

### Task 3: PaymentOrder 엔티티 구현

**Files:**
- Create: `src/main/kotlin/com/pglab/payment/order/PaymentOrder.kt`
- Test: `src/test/kotlin/com/pglab/payment/order/PaymentOrderTest.kt`

**Step 1: Write the failing test**

```kotlin
package com.pglab.payment.order

import com.pglab.payment.shared.CurrencyCode
import com.pglab.payment.shared.Money
import kotlin.test.Test
import kotlin.test.assertEquals

class PaymentOrderTest {
    @Test
    fun `payment order should keep total amount and initial status`() {
        val order = PaymentOrder(
            merchantOrderId = "order-001",
            totalAmount = Money(50_000L, CurrencyCode.KRW)
        )

        assertEquals(PaymentOrderStatus.READY, order.status)
        assertEquals(50_000L, order.totalAmount.amount)
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests com.pglab.payment.order.PaymentOrderTest`

Expected: FAIL because constructor/properties are missing

**Step 3: Write minimal implementation**

- `@Entity`로 선언한다.
- 상세 KDoc 주석을 추가한다.
- `merchantOrderId`, `status`, `totalAmount`, 타임스탬프 필드를 만든다.
- 초기 상태는 `READY`로 둔다.

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests com.pglab.payment.order.PaymentOrderTest`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/kotlin/com/pglab/payment/order/PaymentOrder.kt src/test/kotlin/com/pglab/payment/order/PaymentOrderTest.kt
git commit -m "feat: PaymentOrder 엔티티 추가"
```

### Task 4: PaymentAllocation 엔티티 구현

**Files:**
- Create: `src/main/kotlin/com/pglab/payment/allocation/PaymentAllocation.kt`
- Test: `src/test/kotlin/com/pglab/payment/allocation/PaymentAllocationTest.kt`

**Step 1: Write the failing test**

```kotlin
package com.pglab.payment.allocation

import com.pglab.payment.shared.CurrencyCode
import com.pglab.payment.shared.Money
import kotlin.test.Test
import kotlin.test.assertEquals

class PaymentAllocationTest {
    @Test
    fun `allocation should keep payer and amount`() {
        val allocation = PaymentAllocation(
            payerReference = "user-A",
            allocationAmount = Money(20_000L, CurrencyCode.KRW),
            sequence = 1
        )

        assertEquals("user-A", allocation.payerReference)
        assertEquals(20_000L, allocation.allocationAmount.amount)
        assertEquals(PaymentAllocationStatus.READY, allocation.status)
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests com.pglab.payment.allocation.PaymentAllocationTest`

Expected: FAIL because entity does not exist

**Step 3: Write minimal implementation**

- `PaymentOrder`와 `@ManyToOne` 관계를 둔다.
- payer 식별자, 부담 금액, 순번, 상태를 구현한다.
- KDoc에 더치페이/복합결제를 위해 존재하는 엔티티라는 설명을 남긴다.

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests com.pglab.payment.allocation.PaymentAllocationTest`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/kotlin/com/pglab/payment/allocation/PaymentAllocation.kt src/test/kotlin/com/pglab/payment/allocation/PaymentAllocationTest.kt
git commit -m "feat: PaymentAllocation 엔티티 추가"
```

### Task 5: Authorization 엔티티 구현

**Files:**
- Create: `src/main/kotlin/com/pglab/payment/authorization/Authorization.kt`
- Test: `src/test/kotlin/com/pglab/payment/authorization/AuthorizationTest.kt`

**Step 1: Write the failing test**

```kotlin
package com.pglab.payment.authorization

import com.pglab.payment.shared.CurrencyCode
import com.pglab.payment.shared.Money
import kotlin.test.Test
import kotlin.test.assertEquals

class AuthorizationTest {
    @Test
    fun `authorization should initialize remaining cancelable amount with approved amount`() {
        val authorization = Authorization(
            instrumentType = InstrumentType.CARD,
            requestedAmount = Money(30_000L, CurrencyCode.KRW),
            approvedAmount = Money(30_000L, CurrencyCode.KRW),
            pgTransactionId = "pg-tx-001"
        )

        assertEquals(30_000L, authorization.remainingCancelableAmount.amount)
        assertEquals(AuthorizationStatus.APPROVED, authorization.status)
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests com.pglab.payment.authorization.AuthorizationTest`

Expected: FAIL because entity does not exist

**Step 3: Write minimal implementation**

- `PaymentAllocation`과 `@ManyToOne` 관계를 둔다.
- 승인 금액, 취소 가능 잔액, 환불 가능 잔액, PG 거래 식별자를 구현한다.
- KDoc에 운영 조회용 요약 필드이며 원장의 진실을 캐시하는 성격이라는 설명을 남긴다.

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests com.pglab.payment.authorization.AuthorizationTest`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/kotlin/com/pglab/payment/authorization/Authorization.kt src/test/kotlin/com/pglab/payment/authorization/AuthorizationTest.kt
git commit -m "feat: Authorization 엔티티 추가"
```

### Task 6: LedgerEntry 엔티티 구현

**Files:**
- Create: `src/main/kotlin/com/pglab/payment/ledger/LedgerEntry.kt`
- Test: `src/test/kotlin/com/pglab/payment/ledger/LedgerEntryTest.kt`

**Step 1: Write the failing test**

```kotlin
package com.pglab.payment.ledger

import com.pglab.payment.shared.CurrencyCode
import com.pglab.payment.shared.Money
import kotlin.test.Test
import kotlin.test.assertEquals

class LedgerEntryTest {
    @Test
    fun `ledger entry should keep event type and amount`() {
        val entry = LedgerEntry(
            type = LedgerEntryType.AUTH_CAPTURED,
            amount = Money(30_000L, CurrencyCode.KRW),
            referenceTransactionId = "pg-tx-001"
        )

        assertEquals(LedgerEntryType.AUTH_CAPTURED, entry.type)
        assertEquals(30_000L, entry.amount.amount)
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests com.pglab.payment.ledger.LedgerEntryTest`

Expected: FAIL because entity does not exist

**Step 3: Write minimal implementation**

- `PaymentOrder`, `PaymentAllocation`, `Authorization`에 대한 선택적 참조를 둔다.
- `type`, `amount`, `occurredAt`, `referenceTransactionId`를 구현한다.
- 업데이트 메서드를 만들지 말고 생성 중심으로 설계한다.

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests com.pglab.payment.ledger.LedgerEntryTest`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/kotlin/com/pglab/payment/ledger/LedgerEntry.kt src/test/kotlin/com/pglab/payment/ledger/LedgerEntryTest.kt
git commit -m "feat: LedgerEntry 엔티티 추가"
```

### Task 7: Settlement 엔티티 구현

**Files:**
- Create: `src/main/kotlin/com/pglab/payment/settlement/Settlement.kt`
- Test: `src/test/kotlin/com/pglab/payment/settlement/SettlementTest.kt`

**Step 1: Write the failing test**

```kotlin
package com.pglab.payment.settlement

import com.pglab.payment.shared.CurrencyCode
import com.pglab.payment.shared.Money
import kotlin.test.Test
import kotlin.test.assertEquals

class SettlementTest {
    @Test
    fun `settlement should keep gross fee and net amount`() {
        val settlement = Settlement(
            merchantId = "merchant-1",
            grossAmount = Money(50_000L, CurrencyCode.KRW),
            feeAmount = Money(1_000L, CurrencyCode.KRW),
            netAmount = Money(49_000L, CurrencyCode.KRW)
        )

        assertEquals(49_000L, settlement.netAmount.amount)
        assertEquals(SettlementStatus.READY, settlement.status)
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests com.pglab.payment.settlement.SettlementTest`

Expected: FAIL because entity does not exist

**Step 3: Write minimal implementation**

- 상태, 정산 금액, 정산 일시 필드를 추가한다.
- KDoc에 원장 엔트리를 정산 관점으로 묶는 엔티티라는 설명을 남긴다.

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests com.pglab.payment.settlement.SettlementTest`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/kotlin/com/pglab/payment/settlement/Settlement.kt src/test/kotlin/com/pglab/payment/settlement/SettlementTest.kt
git commit -m "feat: Settlement 엔티티 추가"
```

### Task 8: 엔티티 연관관계와 불변식 테스트 추가

**Files:**
- Create: `src/test/kotlin/com/pglab/payment/domain/PaymentDomainInvariantTest.kt`
- Modify: `src/main/kotlin/com/pglab/payment/order/PaymentOrder.kt`
- Modify: `src/main/kotlin/com/pglab/payment/allocation/PaymentAllocation.kt`
- Modify: `src/main/kotlin/com/pglab/payment/authorization/Authorization.kt`
- Modify: `src/main/kotlin/com/pglab/payment/ledger/LedgerEntry.kt`

**Step 1: Write the failing test**

```kotlin
package com.pglab.payment.domain

import com.pglab.payment.shared.CurrencyCode
import com.pglab.payment.shared.Money
import kotlin.test.Test
import kotlin.test.assertFailsWith

class PaymentDomainInvariantTest {
    @Test
    fun `cancel amount should not exceed remaining cancelable amount`() {
        // 승인 금액보다 큰 취소 요청을 막는 도메인 규칙을 검증한다.
        assertFailsWith<IllegalArgumentException> {
            // 도메인 메서드 호출 코드 작성
        }
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests com.pglab.payment.domain.PaymentDomainInvariantTest`

Expected: FAIL because domain guard logic is missing

**Step 3: Write minimal implementation**

- 승인 금액 초과 취소를 막는 검증 메서드를 추가한다.
- 필요하면 `Authorization`에 `cancel(amount: Money)` 같은 최소 도메인 메서드를 추가한다.
- 원장 엔트리 추가와 요약 필드 감소가 함께 움직이도록 최소 규칙을 만든다.

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests com.pglab.payment.domain.PaymentDomainInvariantTest`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/kotlin/com/pglab/payment/order/PaymentOrder.kt src/main/kotlin/com/pglab/payment/allocation/PaymentAllocation.kt src/main/kotlin/com/pglab/payment/authorization/Authorization.kt src/main/kotlin/com/pglab/payment/ledger/LedgerEntry.kt src/test/kotlin/com/pglab/payment/domain/PaymentDomainInvariantTest.kt
git commit -m "test: 결제 도메인 불변식 검증 추가"
```

### Task 9: JPA 매핑 검증 테스트 추가

**Files:**
- Create: `src/test/kotlin/com/pglab/payment/jpa/PaymentJpaMappingTest.kt`

**Step 1: Write the failing test**

```kotlin
package com.pglab.payment.jpa

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest

@DataJpaTest
class PaymentJpaMappingTest(
    @Autowired private val entityManager: jakarta.persistence.EntityManager,
) {
    @Test
    fun `payment aggregates should be persistable`() {
        // 엔티티 생성 후 persist/flush 검증
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests com.pglab.payment.jpa.PaymentJpaMappingTest`

Expected: FAIL because repository/mapping details are incomplete

**Step 3: Write minimal implementation**

- 엔티티의 기본 생성 요건, 연관관계, 임베디드 매핑을 보완한다.
- 필요 시 protected no-arg 생성자를 추가한다.
- FK와 nullable 여부를 명시한다.

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests com.pglab.payment.jpa.PaymentJpaMappingTest`

Expected: PASS

**Step 5: Commit**

```bash
git add src/test/kotlin/com/pglab/payment/jpa/PaymentJpaMappingTest.kt src/main/kotlin/com/pglab/payment
git commit -m "test: 결제 JPA 매핑 검증 추가"
```

### Task 10: 문서와 주석 정리

**Files:**
- Modify: `src/main/kotlin/com/pglab/payment/order/PaymentOrder.kt`
- Modify: `src/main/kotlin/com/pglab/payment/allocation/PaymentAllocation.kt`
- Modify: `src/main/kotlin/com/pglab/payment/authorization/Authorization.kt`
- Modify: `src/main/kotlin/com/pglab/payment/ledger/LedgerEntry.kt`
- Modify: `src/main/kotlin/com/pglab/payment/settlement/Settlement.kt`
- Modify: `HELP.md`

**Step 1: Write the failing test**

이 작업은 테스트보다 문서 품질 점검이 핵심이므로, 별도의 자동 테스트 대신 수동 체크리스트를 사용한다.

체크리스트:

- 각 엔티티 상단에 KDoc이 있는가
- 각 KDoc에 존재 이유와 시나리오가 적혀 있는가
- `Authorization` 요약 필드가 캐시성 조회 필드라는 설명이 있는가
- HELP.md 또는 별도 문서에서 설계 방향을 찾을 수 있는가

**Step 2: Run verification to confirm current gap**

Run: `grep -R "^/\*\*\|^ \*\|^\*\*" src/main/kotlin/com/pglab/payment HELP.md`

Expected: 핵심 엔티티 설명이 충분하지 않음을 확인

**Step 3: Write minimal implementation**

- 모든 핵심 엔티티에 한글 KDoc을 추가한다.
- HELP.md에 설계 문서 위치를 링크한다.

**Step 4: Run verification to confirm improvement**

Run: `./gradlew test`

Expected: PASS

추가 확인:

- 주석이 실제 필드 책임을 정확하게 설명하는지 수동 검토

**Step 5: Commit**

```bash
git add src/main/kotlin/com/pglab/payment HELP.md
git commit -m "docs: 결제 도메인 주석과 참고 문서 정리"
```

### Task 11: 전체 회귀 검증

**Files:**
- Verify only

**Step 1: Run focused tests**

Run: `./gradlew test --tests com.pglab.payment.*`

Expected: PASS

**Step 2: Run full test suite**

Run: `./gradlew test`

Expected: PASS

**Step 3: Review generated schema**

Run: `./gradlew test --tests com.pglab.payment.jpa.PaymentJpaMappingTest --info`

Expected: 엔티티 매핑 오류 없음

**Step 4: Commit**

이 단계는 검증 단계이므로 새 코드 변경이 없다면 커밋하지 않는다.

**Step 5: Handoff note**

- 부분 취소/복합 결제/더치페이 시나리오가 모두 테스트 이름에 드러나는지 확인한다.
- 다음 단계에서 서비스 계층과 repository를 추가할 때도 반드시 TDD를 유지한다.

---

Plan complete and saved to `docs/plans/2026-04-09-pg-ledger-implementation-plan.md`. Two execution options:

**1. Subagent-Driven (this session)** - I dispatch fresh subagent per task, review between tasks, fast iteration

**2. Parallel Session (separate)** - Open new session with executing-plans, batch execution with checkpoints

Which approach?
