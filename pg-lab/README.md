# pg-lab

PG(Payment Gateway) 도메인을 JPA 엔티티 중심으로 실험하고 정리하는 Spring Boot + Kotlin 프로젝트입니다.

이 저장소는 단순 결제 상태 테이블이 아니라, **결제 의도 / 승인 결과 / 금전 원장**을 분리한 모델을 기준으로 설계합니다.
현재 목표는 다음 시나리오를 무리 없이 담을 수 있는 최소 도메인 모델을 만드는 것입니다.

- 부분 취소
- 더치페이
- 복합 결제(예: 계좌 + 카드)
- 정산 대상 금액 계산을 위한 원장 기록

## 핵심 개념

이 프로젝트는 결제 흐름을 다음 일곱 축으로 나눠서 바라봅니다.

- `PaymentOrder`: 고객/가맹점이 인식하는 상위 결제 주문
- `PaymentOrderLine`: 주문 내부의 payee(판매자) 귀속 단위
- `PaymentAllocation`: 실제 부담 단위
- `Authorization`: 외부 결제수단의 실제 승인 결과
- `AuthorizationLinePortion`: 승인 금액의 주문 라인별 귀속 단위
- `LedgerEntry`: 승인/취소/환불/수수료/정산 같은 금전 사실의 append-only 원장
- `Settlement`: 원장 흐름을 가맹점 정산 관점으로 다시 묶은 단위

핵심 설계 원칙은 아래와 같습니다.

1. 결제 의도와 승인 결과를 분리한다.
2. 원장은 수정이 아니라 추가로 쌓는다.
3. 운영 조회용 상태(`Authorization`)와 금전의 진실(`LedgerEntry`)을 함께 둔다.

## 현재 구현 상태

현재 레포에는 아래 항목이 구현되어 있습니다.

- payee-aware 주문/승인 모델
  - `PaymentOrder` + `PaymentOrderLine`
  - `PaymentAllocation` + `Authorization` + `AuthorizationLinePortion`
- 승인 시 `AuthorizationLinePortion`마다 `LedgerEntry(AUTH_CAPTURED)` 생성
- 취소/환불 시 seller-aware 음수 원장 생성
  - 기존 line portion과 과거 음수 원장을 함께 읽어 남은 line별 수용량 기준으로 분배
  - 마지막 active line이 잔차를 흡수하고, 0원 엔트리는 생략
- `payeeId` 기준 정산 배치 및 payout/reconciliation 흐름
- Spring MVC API 엔드포인트와 서비스 계층
- JPA 매핑, 도메인 불변식, API/서비스/정산 회귀 테스트

아직 범위 밖으로 남겨 둔 것은 주로 아래 항목입니다.

- 외부 PG 실연동
- 완전한 회계용 더블 엔트리 모델

## 기술 스택

- Kotlin 2.2.21
- Spring Boot 4.0.5
- Spring Data JPA
- Spring Web MVC
- H2 Database
- JUnit 5 / Kotlin Test

## 개발 환경

- Java Toolchain: 24

> 참고: 이 프로젝트는 Kotlin 호환성 때문에 JVM 레벨을 24로 사용합니다.

## 실행 방법

### 테스트 실행

```bash
./gradlew test
```

### 애플리케이션 실행

```bash
./gradlew bootRun
```

## 프로젝트 구조

```text
src/main/kotlin/com/pglab
├── PgLabApplication.kt
└── payment
    ├── allocation
    ├── authorization
    ├── ledger
    ├── order
    ├── settlement
    └── shared

src/test/kotlin/com/pglab
├── PgLabApplicationTests.kt
└── payment
```

## 참고 문서

상세 설계와 구현/보정 메모는 아래 문서에 정리되어 있습니다.

- 설계서: `docs/plans/2026-04-09-pg-ledger-design.md`
- 구현 플랜: `docs/plans/2026-04-09-pg-ledger-implementation-plan.md`
- 멀티 셀러 정산 설계서: `docs/plans/2026-04-18-multi-seller-settlement-design.md`
- 멀티 셀러 정산 구현 플랜: `docs/plans/2026-04-18-multi-seller-settlement-implementation-plan.md`

특히 아래 내용을 이해하려면 설계서를 먼저 보는 것이 좋습니다.

- 왜 `Payment` 하나로 뭉치지 않고 `PaymentOrder` / `Authorization` / `LedgerEntry`를 분리했는지
- 더치페이와 복합 결제를 `PaymentAllocation -> Authorization` 구조로 푸는 이유
- 부분 취소를 상태 수정이 아니라 원장 추가로 다루는 이유

## 현재 테스트에서 검증하는 것

현재 테스트는 아래 수준을 검증합니다.

- 주문 total / allocation / authorization line portion 합 일치 불변식
- `PaymentOrderLine` / `AuthorizationLinePortion` / `LedgerEntry.payeeId` 정합성
- 승인 시 line portion별 원장 생성
- 부분 취소/환불 시 이력 기반 seller 분배, 잔차 흡수, 0원 엔트리 생략
- `SettlementBatchService`의 `payeeId` 기준 집계
- JPA 영속성 저장 가능 여부와 API 회귀

즉, 현재 단계는 **payee-aware 결제/원장/정산 흐름의 핵심 불변식과 회귀 시나리오**를 먼저 고정하는 단계입니다.

## 다음 단계 제안

다음 단계로는 보통 아래 확장을 고려할 수 있습니다.

1. 외부 PG/정산 시스템 어댑터 연결
2. 수수료/세금/보류금 등 정산 정책 스냅샷 확장
3. 회계용 더블 엔트리 또는 분개 모델 보강
4. 운영 조회/리포트용 read model 최적화

## 메모

이 레포는 “바로 운영에 투입할 완성형 PG”보다는,
**PG 도메인을 모델링하는 방식 자체를 분해하고 검증하기 위한 실험실**에 가깝습니다.

그래서 구현 속도보다 다음을 더 중요하게 둡니다.

- 경계가 명확한 엔티티 분리
- 테스트 가능한 도메인 규칙
- 나중에 확장 가능한 원장 구조
