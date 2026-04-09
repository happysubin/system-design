# PG 원장/승인 도메인 설계서

## 1. 목적

이 문서는 PG사 관점에서 결제 도메인을 JPA 엔티티 중심으로 설계하기 위한 기준 문서다.
핵심 목표는 다음 세 가지를 동시에 만족하는 것이다.

- 부분 취소를 정확하게 표현할 수 있어야 한다.
- 더치페이와 복합 결제(예: 계좌 + 카드)를 자연스럽게 모델링할 수 있어야 한다.
- 승인 결과와 금전 원장을 분리하여 감사 추적성과 조회 성능을 모두 확보해야 한다.

이 설계에서 결제는 단순 상태값이 아니라, 결제 의도와 승인 결과와 금전 변화를 분리해서 다루는 도메인으로 본다.

## 2. 핵심 설계 결정

### 2.1 결제와 승인을 분리한다

`PaymentOrder`는 고객 또는 주문 관점의 결제 의도를 표현한다.
즉, 무엇을 얼마 결제해야 하는지와 결제 전체의 비즈니스 문맥을 담당한다.

반면 `Authorization`은 외부 금융망 또는 결제수단과의 상호작용 결과를 표현한다.
즉, 실제로 얼마가 승인되었고 어떤 승인번호나 거래번호를 받았는지를 담당한다.

이 둘을 분리하는 이유는 다음과 같다.

- 하나의 결제 주문 안에 여러 승인 건이 생길 수 있다.
- 더치페이와 복합 결제에서는 결제 의도와 승인 결과의 개수가 일치하지 않는다.
- 부분 취소는 결제 전체가 아니라 특정 승인 금액 일부에 대해 발생한다.

### 2.2 원장은 append-only로 관리한다

`LedgerEntry`는 승인, 취소, 환불, 수수료, 정산 같은 금전 사실을 기록하는 공식 장부다.
이미 적힌 원장 데이터를 수정하지 않고, 항상 새로운 엔트리를 추가한다.

이 원칙을 두는 이유는 다음과 같다.

- 감사 추적이 쉬워진다.
- 부분 취소와 부분 환불을 금액 단위로 안전하게 기록할 수 있다.
- 미래에 정산 배치나 회계 연동이 추가되어도 확장성이 높다.

### 2.3 조회 최적화를 위해 Authorization과 LedgerEntry를 함께 둔다

`Authorization`만 두면 회계적 진실이 약해지고, `LedgerEntry`만 두면 실무 조회가 불편해진다.
따라서 다음 원칙을 함께 사용한다.

- 현재 승인 상태나 승인 잔액 같은 운영 조회는 `Authorization`이 빠르게 제공한다.
- 금액의 진실과 감사 추적은 `LedgerEntry`가 보장한다.

## 3. 엔티티 구조

### 3.1 PaymentOrder

결제 주문 전체를 나타내는 루트 엔티티다.

주요 책임:

- 주문 식별자와 외부 참조값 보관
- 총 결제 대상 금액 보관
- 전체 결제 흐름의 상위 상태 보관
- 여러 부담 주체와 여러 결제수단을 포괄하는 상위 컨텍스트 제공

예상 필드:

- `id`
- `merchantOrderId`
- `paymentOrderStatus`
- `currency`
- `totalAmount`
- `createdAt`, `updatedAt`

### 3.2 PaymentAllocation

`PaymentOrder`를 실제 부담 단위로 쪼갠 엔티티다.
더치페이와 복합 결제를 지탱하는 핵심 엔티티다.

주요 책임:

- 누가 얼마를 부담하는지 표현
- 하나의 주문을 여러 payer 단위로 분리
- 하나의 부담분 아래 여러 승인 건을 허용

예상 필드:

- `id`
- `paymentOrderId`
- `payerId` 또는 `payerReference`
- `allocationAmount`
- `allocationStatus`
- `sequence`

### 3.3 Authorization

외부 결제수단과의 승인 결과를 표현하는 엔티티다.

주요 책임:

- 카드 승인, 계좌이체 승인 등 실제 승인 결과 기록
- 승인번호, 거래번호, 결제수단 유형 보관
- 현재 취소 가능 금액, 환불 가능 금액 같은 운영 지표 제공

예상 필드:

- `id`
- `paymentAllocationId`
- `instrumentType`
- `authorizationStatus`
- `requestedAmount`
- `approvedAmount`
- `remainingCancelableAmount`
- `remainingRefundableAmount`
- `pgTransactionId`
- `approvalCode`
- `approvedAt`

### 3.4 LedgerEntry

금전 변화의 공식 원장 엔트리다.

주요 책임:

- 승인, 부분 취소, 환불, 수수료, 정산 이벤트 기록
- 금액 변화의 근거와 방향성 보관
- 외부 참조와 내부 참조 연결

예상 필드:

- `id`
- `paymentOrderId`
- `paymentAllocationId`
- `authorizationId`
- `ledgerEntryType`
- `amount`
- `currency`
- `occurredAt`
- `referenceTransactionId`
- `description`

### 3.5 Settlement

정산 배치 또는 정산 지급 단위를 표현하는 엔티티다.

주요 책임:

- 원장 엔트리 여러 개를 정산 관점으로 묶음
- 정산 대상 기간과 상태 표현
- 실제 지급 예정/완료 금액 관리

예상 필드:

- `id`
- `merchantId`
- `settlementStatus`
- `scheduledDate`
- `settledAt`
- `grossAmount`
- `feeAmount`
- `netAmount`

## 4. 연관관계

권장 관계는 다음과 같다.

- `PaymentOrder 1:N PaymentAllocation`
- `PaymentAllocation 1:N Authorization`
- `PaymentOrder 1:N LedgerEntry`
- `PaymentAllocation 1:N LedgerEntry`
- `Authorization 1:N LedgerEntry`
- `Settlement 1:N LedgerEntry` 또는 `Settlement 1:N SettlementLine`

핵심 포인트는 `PaymentAllocation`과 `Authorization`을 1:1로 고정하지 않는 것이다.
그래야 한 부담분이 여러 승인 시도로 이어지거나, 한 사람의 부담분이 카드와 계좌로 다시 분할되는 경우를 표현할 수 있다.

## 5. 주요 시나리오별 해석

### 5.1 부분 취소

예: 카드 승인 30,000원 중 10,000원만 취소

처리 방식:

- 원래 승인 건은 `Authorization`으로 유지한다.
- 취소는 기존 Authorization을 수정하지 않고 `LedgerEntry(CANCEL)`를 추가한다.
- 동시에 `Authorization.remainingCancelableAmount`를 줄여 운영 조회를 빠르게 만든다.

### 5.2 더치페이

예: 총 50,000원을 A 20,000원, B 30,000원으로 분담

처리 방식:

- `PaymentOrder`는 하나다.
- `PaymentAllocation`은 둘 이상 생긴다.
- 각 allocation은 별도의 승인 흐름을 가진다.

즉, 한 주문 안에 여러 부담 주체가 공존하는 구조다.

### 5.3 복합 결제

예: 한 사람의 50,000원 부담분 중 40,000원은 계좌, 10,000원은 카드

처리 방식:

- 하나의 `PaymentAllocation` 아래 여러 `Authorization`을 둔다.
- 각 승인 건은 서로 다른 `instrumentType`을 가진다.
- 취소와 환불도 승인 건별로 추적한다.

즉, 더치페이와 복합 결제는 서로 다른 문제가 아니라, `PaymentAllocation -> Authorization`의 다중 구조로 함께 해결된다.

## 6. 상태와 불변식

아래 불변식은 엔티티 설계와 서비스 로직에서 반드시 지켜야 한다.

1. `PaymentOrder.totalAmount`는 모든 `PaymentAllocation.allocationAmount`의 합과 같아야 한다.
2. 하나의 `Authorization`에서 취소 누적 금액은 승인 금액을 초과할 수 없다.
3. 하나의 `Authorization`에서 환불 누적 금액은 환불 가능 금액을 초과할 수 없다.
4. `LedgerEntry`는 수정/삭제 대신 추가로만 상태를 반영해야 한다.
5. 운영 조회용 잔액 필드는 원장과 모순되면 안 된다.
6. 정산 금액은 원장 엔트리의 집계 결과로 계산되어야 한다.

## 7. JPA 설계 가이드

### 7.1 Aggregate 경계

- `PaymentOrder`를 상위 aggregate root로 본다.
- `Authorization`은 독립 조회가 많으므로 별도 repository 대상이 될 수 있다.
- `LedgerEntry`는 append-only 성격을 유지하도록 수정 API를 최소화한다.

### 7.2 값 객체 후보

다음 항목은 `@Embeddable` 후보로 본다.

- 금액(`Money`)
- 외부 거래 식별자(`PgReference`)
- 승인 메타데이터(`AuthorizationMetadata`)

### 7.3 enum 후보

- `PaymentOrderStatus`
- `PaymentAllocationStatus`
- `AuthorizationStatus`
- `InstrumentType`
- `LedgerEntryType`
- `SettlementStatus`

## 8. 주석 작성 원칙

사용자는 엔티티에 자세한 주석을 원한다.
따라서 각 엔티티와 핵심 필드에는 Kotlin KDoc 또는 블록 주석으로 아래 내용을 남긴다.

- 이 엔티티가 비즈니스 의도인지, 승인 결과인지, 원장 사실인지
- 왜 별도 엔티티로 분리했는지
- 어떤 시나리오(부분 취소, 더치페이, 복합 결제)를 위해 존재하는지
- 이 필드를 직접 수정하면 안 되는 이유가 있는지

특히 `Authorization.remainingCancelableAmount` 같은 요약 필드는
"원장의 캐시성 조회 필드이며 진실의 원천은 LedgerEntry 집계 결과"라는 설명을 반드시 주석으로 남긴다.

## 9. 이번 설계의 범위와 제외 사항

이번 설계 범위:

- 결제/승인/원장/정산의 엔티티 경계 수립
- 부분 취소, 더치페이, 복합 결제 대응 구조 정의
- JPA 엔티티 설계의 기반 마련

이번 설계에서 제외:

- 실제 카드사/은행 연동 프로토콜
- 비동기 이벤트 아웃박스 설계
- 회계 시스템용 완전한 차변/대변 더블엔트리
- 운영 배치와 정산 스케줄러의 세부 구현

## 10. 결론

이 도메인에서는 단일 `Payment` 엔티티로 모든 것을 표현하면 빠르게 한계에 부딪힌다.
따라서 다음 구조를 기준으로 삼는다.

- 비즈니스 의도: `PaymentOrder`
- 부담 분배: `PaymentAllocation`
- 승인 결과: `Authorization`
- 금전 사실: `LedgerEntry`
- 정산 묶음: `Settlement`

이 구조는 부분 취소, 더치페이, 복합 결제를 무리 없이 수용하면서,
운영 조회와 감사 추적을 동시에 만족시키는 최소한의 확장 가능한 모델이다.
