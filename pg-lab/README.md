# pg-lab

PG(Payment Gateway) 도메인을 JPA 엔티티 중심으로 실험하고 정리하는 Spring Boot + Kotlin 프로젝트입니다.

이 저장소는 단순 결제 상태 테이블이 아니라, **결제 의도 / 승인 결과 / 금전 원장**을 분리한 모델을 기준으로 설계합니다.
현재 목표는 다음 시나리오를 무리 없이 담을 수 있는 최소 도메인 모델을 만드는 것입니다.

- 부분 취소
- 더치페이
- 복합 결제(예: 계좌 + 카드)
- 정산 대상 금액 계산을 위한 원장 기록

## 핵심 개념

이 프로젝트는 결제 흐름을 다음 다섯 축으로 나눠서 바라봅니다.

- `PaymentOrder`: 고객/가맹점이 인식하는 상위 결제 주문
- `PaymentAllocation`: 실제 부담 단위
- `Authorization`: 외부 결제수단의 실제 승인 결과
- `LedgerEntry`: 승인/취소/환불/수수료/정산 같은 금전 사실의 append-only 원장
- `Settlement`: 원장 흐름을 가맹점 정산 관점으로 다시 묶은 단위

핵심 설계 원칙은 아래와 같습니다.

1. 결제 의도와 승인 결과를 분리한다.
2. 원장은 수정이 아니라 추가로 쌓는다.
3. 운영 조회용 상태(`Authorization`)와 금전의 진실(`LedgerEntry`)을 함께 둔다.

## 현재 구현 상태

현재 레포에는 아래 항목이 구현되어 있습니다.

- Kotlin + Spring Boot 4.0.5 기반 프로젝트 설정
- JPA 엔티티 골격
  - `PaymentOrder`
  - `PaymentAllocation`
  - `Authorization`
  - `LedgerEntry`
  - `Settlement`
- 공통 값 객체와 enum
  - `Money`
  - 상태 enum / 수단 enum / 원장 타입 enum
- 도메인 규칙 예시
  - `Authorization.cancel(amount)`
  - 취소 가능 금액 초과 방지
- JPA 저장 가능 여부 검증 테스트

아직 구현하지 않은 범위는 다음과 같습니다.

- repository / service 계층
- 외부 PG 연동
- 정산 배치 로직
- API 엔드포인트
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

상세 설계와 구현 계획은 아래 문서에 정리되어 있습니다.

- 설계서: `docs/plans/2026-04-09-pg-ledger-design.md`
- 구현 플랜: `docs/plans/2026-04-09-pg-ledger-implementation-plan.md`

특히 아래 내용을 이해하려면 설계서를 먼저 보는 것이 좋습니다.

- 왜 `Payment` 하나로 뭉치지 않고 `PaymentOrder` / `Authorization` / `LedgerEntry`를 분리했는지
- 더치페이와 복합 결제를 `PaymentAllocation -> Authorization` 구조로 푸는 이유
- 부분 취소를 상태 수정이 아니라 원장 추가로 다루는 이유

## 현재 테스트에서 검증하는 것

현재 테스트는 아래 수준을 검증합니다.

- 도메인 클래스 골격 존재 여부
- 금액 값 객체의 기본 제약(음수 방지)
- 엔티티의 기본 상태/필드 보관
- 부분 취소 시 취소 가능 금액 감소
- 취소 가능 금액 초과 금지
- JPA 영속성 저장 가능 여부

즉, 지금 단계는 **도메인 모델의 구조와 핵심 불변식**을 먼저 안전하게 고정하는 단계입니다.

## 다음 단계 제안

다음 구현 단계로는 보통 아래 순서를 권장합니다.

1. repository 추가
2. 결제/승인/취소 서비스 계층 추가
3. LedgerEntry 생성 규칙을 서비스 흐름에 연결
4. 정산 계산 로직 추가
5. API 또는 시뮬레이션용 어댑터 추가

## 메모

이 레포는 “바로 운영에 투입할 완성형 PG”보다는,
**PG 도메인을 모델링하는 방식 자체를 분해하고 검증하기 위한 실험실**에 가깝습니다.

그래서 구현 속도보다 다음을 더 중요하게 둡니다.

- 경계가 명확한 엔티티 분리
- 테스트 가능한 도메인 규칙
- 나중에 확장 가능한 원장 구조
