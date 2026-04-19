# 멀티 셀러 정산 지원 설계서

## 1. 목적

이 문서는 현재 PG 실험 모델에 **멀티 셀러 정산**을 추가하기 위한 설계 기준을 정리한다.
기존 모델은 더치페이와 복합 결제를 `PaymentOrder -> PaymentAllocation -> Authorization` 구조로 잘 표현하지만,
정산 축이 `PaymentOrder.merchantId` 하나에 묶여 있어서 **하나의 결제 주문 안에 여러 판매자 상품이 섞인 경우**를 정확히 표현하지 못한다.

이번 설계의 목표는 다음 세 가지를 동시에 만족하는 것이다.

- 고객이 보는 결제 주문은 하나로 유지한다.
- 더치페이와 복합 결제처럼 **payer 축** 분해는 그대로 유지한다.
- 정산은 판매자/정산 대상자 기준의 **payee 축**으로 정확하게 집계할 수 있어야 한다.

## 2. 현재 모델의 한계

현재 구현에서 정산은 `LedgerEntry -> PaymentOrder -> merchantId` 경로를 통해 가맹점 기준으로 묶인다.
즉 정산 배치가 읽는 정보는 사실상 주문 상위에 달린 단일 `merchantId` 하나뿐이다.

이 구조는 단일 merchant 주문에는 맞지만, 아래와 같은 상황에서는 부족하다.

- 하나의 주문에 seller A 상품 30,000원, seller B 상품 20,000원이 함께 들어간 경우
- 한 payer가 여러 seller 상품을 한 번에 결제하는 경우
- 더치페이와 멀티 셀러가 동시에 존재하는 경우
- 혼합 결제 후 부분 취소/환불이 발생하는 경우

핵심 문제는 현재 모델이 **누가 냈는가(payer)** 는 표현하지만,
**그 돈이 최종적으로 누구 몫인가(payee)** 는 원장 수준에서 직접 표현하지 못한다는 점이다.

## 3. 핵심 설계 결정

### 3.1 payer 축과 payee 축을 분리한다

더치페이는 "누가 얼마를 부담했는가"의 문제이고,
멀티 셀러 정산은 "그 금액이 누구에게 정산되어야 하는가"의 문제다.

이 둘은 같은 차원이 아니므로 하나의 엔티티에 억지로 합치지 않는다.

- `PaymentAllocation`: payer 축
- `PaymentOrderLine`: payee 축

즉 주문 내부에는 **payer 분해 구조**와 **payee 분해 구조**가 동시에 존재해야 한다.

### 3.2 판매자 귀속 정보는 주문 라인과 승인 라인 분해에 모두 남긴다

`PaymentOrderLine`만 두면 주문 의도 수준의 seller 정보는 생기지만,
실제 승인 결과가 여러 수단으로 나뉘거나 이후 취소/환불이 발생할 때
어떤 승인 금액이 어느 seller 몫인지 잃어버릴 수 있다.

이를 해결하기 위해 `Authorization` 아래에
**`AuthorizationLinePortion`** 이라는 하위 엔티티를 둔다.

이 엔티티는 한 승인 건의 금액이 어느 주문 라인에 얼마씩 귀속되는지 표현한다.

즉 관계는 다음과 같다.

- `PaymentOrder 1:N PaymentOrderLine`
- `PaymentOrder 1:N PaymentAllocation`
- `PaymentAllocation 1:N Authorization`
- `Authorization 1:N AuthorizationLinePortion`
- `PaymentOrderLine 1:N AuthorizationLinePortion`

이 구조를 두면 다음이 가능하다.

- 한 payer가 여러 seller 상품을 한 번에 결제
- 하나의 allocation 아래 여러 승인 수단을 혼합 결제
- 취소/환불 시 기존 승인 라인 분해를 기준으로 seller 귀속 금액 재구성

### 3.3 정산은 원장에 남은 payee 스냅샷을 기준으로 집계한다

정산 배치는 원장만 읽어도 집계가 가능해야 한다.
따라서 `LedgerEntry`에는 단순 참조만 두지 않고,
정산에 필요한 최소한의 seller/payee 스냅샷을 함께 남긴다.

권장 필드는 다음과 같다.

- `paymentOrderLine`
- `payeeId`

이렇게 하면 `SettlementBatchService`는 더 이상 `paymentOrder.merchantId`에 의존하지 않고
원장 자체의 `payeeId` 기준으로 정산 건을 만들 수 있다.

## 4. 권장 엔티티 구조

### 4.1 PaymentOrder

상위 결제 주문 루트로 유지한다.

유지 책임:

- `merchantOrderId`
- `totalAmount`
- 주문 상위 상태
- 전체 결제 컨텍스트

변경 사항:

- `merchantId`는 "결제 접수 주체" 또는 외부 merchant 참조로는 유지할 수 있다.
- 단, 정산 집계 기준 키로 사용하지 않는다.
- `lines: MutableList<PaymentOrderLine>` 관계를 추가한다.

### 4.2 PaymentOrderLine (신규)

주문 안의 실제 판매자 귀속 단위를 표현한다.

권장 필드:

- `id`
- `paymentOrder`
- `lineReference`
- `payeeId`
- `lineAmount`
- `quantity`
- `description` 또는 `productReference`
- 선택: `feePolicySnapshot`, `taxAmount`

역할:

- 어떤 seller/payee 몫 주문인지 표현
- 주문 총액의 seller별 분해 기준 제공

### 4.3 PaymentAllocation

기존 역할을 유지한다.

유지 필드:

- `payerReference`
- `allocationAmount`
- `sequence`
- `status`

중요 원칙:

- `sellerId` / `payeeId`는 넣지 않는다.
- 이 엔티티는 끝까지 "누가 내는가"만 표현한다.

### 4.4 Authorization

기존처럼 실제 승인 결과를 표현한다.

유지 책임:

- 승인 금액
- 승인번호
- 거래번호
- 취소 가능 잔액
- 환불 가능 잔액

추가 관계:

- `linePortions: MutableList<AuthorizationLinePortion>`

### 4.5 AuthorizationLinePortion (신규)

한 승인 금액이 어떤 주문 라인에 얼마씩 귀속되는지 표현한다.

권장 필드:

- `id`
- `authorization`
- `paymentOrderLine`
- `payeeId`
- `amount`
- `sequence`

역할:

- 승인 금액의 seller/payee 귀속 구조 보존
- 이후 취소/환불 시 seller별 음수 원장 생성 기준 제공

### 4.6 LedgerEntry

원장 엔트리는 seller/payee 축이 직접 보이도록 확장한다.

추가 필드:

- `paymentOrderLine`
- `payeeId`

변경 의미:

- 승인 시 `Authorization` 하나당 원장 1개가 아니라,
  **`AuthorizationLinePortion` 하나당 원장 1개**를 만드는 쪽이 정산 관점에서 더 안전하다.
- 그래야 멀티 셀러 혼합 주문에서도 각 seller 몫이 원장에 독립적으로 남는다.

### 4.7 Settlement

정산 엔티티의 기준 키는 `merchantId`에서 `payeeId` 중심으로 이동한다.

권장 방향:

- `payeeId`를 필수 필드로 둔다.
- 필요하면 `merchantId`는 보조 운영 필드로 유지한다.

의미:

- Settlement는 더 이상 "주문 merchant" 기준 집계가 아니라
  **실제 지급 대상자(payee)** 기준 집계다.

### 4.8 Payout

구조는 그대로 유지해도 된다.
다만 `Settlement`가 payee 기준이 되므로,
`Payout` 역시 결과적으로 seller/payee 지급 시도를 표현하게 된다.

## 5. API 및 서비스 입력 모델 변경

현재 authorize 입력은 `merchantId`, `merchantOrderId`, `allocations` 중심이다.
멀티 셀러를 지원하려면 주문 라인과 승인 라인 분해 정보가 함께 들어와야 한다.

권장 요청 구조:

- `merchantId`
- `merchantOrderId`
- `totalAmount`
- `lines[]`
  - `lineReference`
  - `payeeId`
  - `lineAmount`
  - `quantity`
- `allocations[]`
  - `payerReference`
  - `allocationAmount`
  - `authorizations[]`
    - `instrumentType`
    - `requestedAmount`
    - `approvedAmount`
    - `pgTransactionId`
    - `linePortions[]`
      - `lineReference`
      - `amount`

이렇게 하면 authorize 시점에 이미
"누가 냈는가"와 "어느 seller 몫인가"를 함께 확정할 수 있다.

## 6. 서비스 흐름

### 6.1 승인

`AuthorizePaymentService`는 다음 순서로 동작한다.

1. `PaymentOrder` 생성
2. `PaymentOrderLine` 생성
3. `PaymentAllocation` 생성
4. `Authorization` 생성
5. 각 authorization 아래 `AuthorizationLinePortion` 생성
6. 각 `AuthorizationLinePortion`마다 `LedgerEntry(AUTH_CAPTURED)` 생성

즉 승인 후 원장에는 seller/payee 단위의 사실이 남는다.

### 6.2 부분 취소 / 환불

현재 구현은 취소/환불 시 authorization 잔액을 먼저 줄인 뒤,
음수 원장을 seller-aware line 단위로 다시 생성한다.

구현 규칙:

- `authorization.linePortions`를 `sequence`, `lineReference`, `payeeId` 순으로 정렬해 분배 기준을 고정한다.
- 기존 취소/환불 이력(`CANCELLED`, `REFUNDED`)을 함께 읽어 각 line의 **남은 수용량(remaining capacity)** 을 계산한다.
- 새 취소/환불 금액은 현재 남은 수용량 비율로 배분한다.
- 정수 잔차는 마지막 active line이 흡수한다.
- 이미 남은 수용량이 0인 line은 건너뛰고, 결과 `splitAmount == 0`인 음수 원장은 생성하지 않는다.

즉 v1은 단순 "원래 비율 재사용"보다 한 단계 더 나아가,
과거 음수 원장 이력을 반영한 **history-aware remaining-capacity split** 으로 seller별 순액을 유지한다.

### 6.3 정산 배치

`SettlementBatchService`는 `LedgerEntry.payeeId` 기준으로 묶는다.

즉 지금의:

- `groupBy { merchantId }`

를 다음으로 바꾼다.

- `groupBy { payeeId }`

그리고 `SettlementLedgerReader`도 `PaymentOrder.merchantId`가 아니라
원장 자체에 저장된 `payeeId`를 읽는다.

## 7. 불변식

다음 불변식은 서비스와 테스트에서 반드시 지켜야 한다.

1. `PaymentOrder.totalAmount`는 모든 `PaymentOrderLine.lineAmount` 합과 같아야 한다.
2. `PaymentOrder.totalAmount`는 모든 `PaymentAllocation.allocationAmount` 합과 같아야 한다.
3. 각 `Authorization.approvedAmount`는 해당 authorization의 `AuthorizationLinePortion.amount` 합과 같아야 한다.
4. 각 `PaymentOrderLine.lineAmount`는 관련 `AuthorizationLinePortion.amount` 누적 합과 모순되면 안 된다.
5. `LedgerEntry.payeeId`는 연결된 `PaymentOrderLine.payeeId`와 같아야 한다.
6. 취소/환불 후 seller별 순액 합은 전체 취소/환불 금액과 정확히 일치해야 한다.
7. 정산은 원장 집계 결과로만 계산해야 한다.

## 8. 선택하지 않은 대안

### 8.1 PaymentAllocation에 sellerId를 넣는 방식

이 방식은 구현이 쉬워 보이지만,
한 엔티티가 payer와 payee 의미를 동시에 가지게 되어 모델이 흐려진다.
특히 여러 seller 상품을 한 payer가 복합 결제하는 순간 확장성이 급격히 떨어진다.

### 8.2 PaymentOrder를 seller별로 분할하는 방식

정산은 단순해지지만,
고객이 보는 주문 1개라는 UX와 어긋나고
더치페이/통합취소/통합조회가 더 복잡해진다.

## 9. 구현 순서 권장안

1. `PaymentOrderLine`과 `AuthorizationLinePortion` 엔티티 추가
2. authorize API/서비스 입력에 `lines`와 `linePortions` 추가
3. 승인 시 line-aware ledger 생성
4. settlement reader/batch를 `payeeId` 기준으로 전환
5. 취소/환불 시 payee별 음수 원장 생성
6. JPA 매핑 테스트와 서비스/API 테스트 보강

## 9.1 구현 후 보정 메모

실제 구현은 위 권장안의 방향을 따르되, 취소/환불 분배 규칙을 더 구체화했다.

- `PartialCancellationService` / `RefundService`는 과거 음수 원장 이력을 함께 읽는다.
- seller별 음수 원장은 `paymentOrderLine`과 `payeeId`를 유지한 채 생성된다.
- 정산 배치는 `LedgerEntry.payeeId`만으로 집계 가능하도록 유지되어 상위 `merchantId` 의존을 제거했다.

## 10. 결론

멀티 셀러 정산을 제대로 지원하려면
단순히 `merchantId` 필드를 옮기는 수준으로는 부족하다.

핵심은 다음 세 가지다.

- 주문은 `PaymentOrder` 하나로 유지한다.
- payer 축은 `PaymentAllocation`으로 유지한다.
- payee 축은 `PaymentOrderLine` + `AuthorizationLinePortion` + `LedgerEntry.payeeId`로 새로 명시한다.

이 구조를 적용하면 더치페이, 복합 결제, 멀티 셀러 정산, 부분 취소/환불을
서로 충돌하지 않는 직교 구조로 다룰 수 있다.
