# first-come-coupon

선착순 쿠폰 발급 시스템을 Spring Boot + Kotlin 기반으로 학습하기 위한 프로젝트입니다.

## 프로젝트 목표

- 다중 서버 환경을 가정한 선착순 쿠폰 발급 시스템을 설계하고 구현한다.
- Redis를 활용해 대량 요청 구간의 빠른 중복 체크와 선착순 제어를 수행한다.
- PostgreSQL을 최종 데이터 정합성의 기준으로 사용한다.

## 핵심 요구사항

### 1. 다중 서버 환경 전제

- 애플리케이션은 로드밸런서 뒤에 여러 대의 서버가 존재하는 환경을 가정한다.
- 각 서버는 무상태(stateless)로 동작해야 하며, 로컬 메모리에 쿠폰 재고나 발급 완료 상태를 저장하지 않는다.

### 2. 유저당 1개 제한

- 동일한 유저는 동일한 쿠폰 이벤트에 대해 최대 1개만 발급받을 수 있다.
- 중복 클릭, 네트워크 재시도, 여러 서버로 동시에 들어오는 요청이 발생해도 최종적으로는 1건만 성공해야 한다.

### 3. 선착순 수량 보장

- 쿠폰 발급 성공 수는 사전에 정의된 총 발급 수량을 초과하면 안 된다.
- 초과 발급(oversell)이 발생하지 않도록 동시성 제어가 필요하다.

### 4. Redis와 SQL의 역할 분리

- Redis는 선착순 경쟁 구간에서 빠른 중복 체크와 재고 제어를 담당한다.
- PostgreSQL은 최종 발급 성공 여부를 확정하는 source of truth 역할을 담당한다.
- Redis gate 통과 후 SQL 최종 저장이 실패하면 Redis 재고/claim marker를 원자적으로 rollback하고, 필요 시 SQL 기준으로 해당 쿠폰 재고를 즉시 재동기화한다.

### 5. API 버저닝

- 외부 API는 `/api/v1` 경로를 기준으로 설계한다.
- 예: `/api/v1/coupons/{couponId}/claim`

## 현재 기술 스택

- Kotlin
- Spring Boot
- Spring MVC (Servlet)
- Spring Data JPA
- Spring Data Redis
- PostgreSQL
- H2 (test)

## 로컬 실행 환경

이 프로젝트는 Spring Boot의 Docker Compose 연동 기능을 사용해 로컬에서 PostgreSQL과 Redis를 함께 기동할 수 있도록 구성한다.

### 로컬 개발 실행

- 로컬 개발 환경은 `local` 프로필 기준으로 실행한다.
- `local` 프로필로 애플리케이션을 실행하면 Spring Boot가 `compose.yaml`을 감지하고 PostgreSQL, Redis를 자동으로 기동한다.

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

### 테스트 실행

- 테스트는 Docker Compose를 사용하지 않는다.
- 기본 테스트는 `test` 프로필과 H2 기반 설정을 사용한다.
- 일부 통합 테스트는 `postgres-test` 프로필과 Testcontainers 기반 PostgreSQL/Redis 조합으로 실행한다.

```bash
./gradlew test
```

### Docker Compose 확인

- 로컬 실행 중 컨테이너 상태가 궁금하면 아래 명령으로 확인할 수 있다.

```bash
docker compose ps
docker compose logs postgres redis
```

### 쿠폰 등록 API 예시

- 로컬에서 애플리케이션 실행 후 아래 요청으로 쿠폰 이벤트를 등록할 수 있다.

```bash
curl -X POST http://localhost:8080/api/v1/coupons \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "선착순 쿠폰",
    "totalQuantity": 100,
    "issueStartAt": "2026-03-28T10:00:00",
    "issueEndAt": "2026-03-29T10:00:00"
  }'
```

### 쿠폰 발급 API 예시

- 등록된 쿠폰에 대해 특정 회원이 선착순 발급을 요청할 수 있다.

```bash
curl -X POST http://localhost:8080/api/v1/coupons/1/claim \
  -H 'Content-Type: application/json' \
  -d '{
    "memberId": 1
  }'
```

### 쿠폰 발급 결과 및 오류 동작

- `201 CREATED`: `ISSUED`
- `409 CONFLICT`: `ALREADY_CLAIMED`, `SOLD_OUT`
- `404 NOT_FOUND`: `COUPON_NOT_FOUND`, `MEMBER_NOT_FOUND`
- `422 UNPROCESSABLE_ENTITY`: `NOT_IN_ISSUE_WINDOW`
- `500 INTERNAL_SERVER_ERROR`: `INTERNAL_FAILURE` (예: Redis stock 미초기화/유실, Redis gate 실행 예외(연결 장애 등), 예기치 못한 런타임 오류)

### 실패 처리 동작

- 쿠폰 발급 요청은 크게 **eligibility 확인 → Redis gate → SQL 최종 저장** 순서로 진행된다.
- **Redis gate 단계에서 실패하는 경우**
  - 이미 같은 회원의 claim marker가 있으면 `ALREADY_CLAIMED`로 종료된다.
  - Redis stock이 0 이하이면 `SOLD_OUT`으로 종료된다.
  - Redis stock key가 초기화되지 않았거나 유실된 경우 `INTERNAL_FAILURE`로 종료된다.
  - Redis gate 호출 자체가 예외로 실패하면(예: Redis 연결 장애) 발급은 fail-closed로 `INTERNAL_FAILURE(HTTP 500)`로 종료된다. 이 경로에서는 SQL 최종 저장, rollback, reconciliation 단계로 진행하지 않는다.
  - Redis gate 예외가 연속으로 발생하면 circuit breaker가 열리고, open 상태에서는 Redis를 다시 호출하지 않고 즉시 fail-fast 한다.
- **Redis gate는 통과했지만 SQL 최종 저장 단계에서 실패하는 경우**
  - `coupon_stock` 감소에 실패하면 SQL에서 `SoldOut`으로 판정된다.
  - 같은 회원에 대한 중복 저장이 발생하면 SQL unique 제약조건으로 최종 방어된다. 이 경우 stock 차감은 같은 트랜잭션 롤백으로 함께 취소된다.
  - 그 외에도 비중복 무결성 오류나 예기치 못한 런타임 오류가 발생할 수 있다.
- **복원 로직**
  - Redis gate를 통과한 뒤 SQL 단계에서 실패하면 1차로 Redis rollback을 수행한다.
  - rollback은 claim marker 삭제와 재고 복구를 Lua 스크립트 1회로 같은 원자 구간에서 처리한다.
  - SQL에서 품절 판정, 비중복 무결성 오류, 예기치 못한 런타임 오류가 발생한 경우에는 rollback 이후 해당 coupon의 Redis stock을 SQL truth 기준으로 다시 reconciliation한다.
  - reconciliation은 Redis stock뿐 아니라 SQL 발급 이력이 없는 orphan claim marker도 함께 정리한다.
  - 같은 회원의 중복 발급은 Redis claim marker와 SQL unique 제약조건으로 함께 방어하며, 최종적으로는 `ALREADY_CLAIMED`로 수렴한다.
- 즉, 이 시스템의 복원 로직은 **Redis 즉시 복원(rollback)** 과 **SQL truth 기준 재정렬(reconciliation)** 의 두 단계로 구성된다.

## 설계 원칙

- Redis는 빠른 admission gate로 사용하고, 최종 정합성은 SQL에서 보장한다.
- 중복 발급 방지는 Redis 1차 제어 + SQL unique 제약조건으로 이중 방어한다.
- 발급 성공의 SQL truth는 `CouponIssue` 이력이다.
- 최종 발급 확정은 SQL에서 `coupon_stock` 감소 + `coupon_issue` unique 저장으로 수행하며, Redis는 admission gate 역할로 한정한다.
- 발급 수와 남은 수량 조회는 `coupon_stock.remaining_quantity`와 `CouponIssue` 이력을 함께 기준으로 계산한다.
- Redis rollback은 Lua 스크립트 1회 호출로 처리하며, claim marker 삭제와 재고 복구를 같은 원자 구간에서 수행한다.
- Redis stock은 runtime gate 용도이며, startup/periodic reconciliation에 더해 SQL 저장 실패(품절 판정, 비중복 무결성 오류, 예기치 못한 런타임 오류) 시 해당 coupon만 즉시 reconciliation한다.
- 반대로 Redis gate 호출 예외(연결 장애 등)처럼 gate 이전 실패는 fail-closed로 종료하며, SQL 저장/복원 경로를 타지 않는다.
- 구현은 먼저 정합성을 만족시키는 방향으로 진행하고, 이후 성능 최적화를 추가한다.

## Redis 장애 대응 정책

### 현재 동작(구현됨)

- 현재 발급 경로는 `eligibility 확인 -> Redis gate -> SQL 최종 저장` 순서다.
- Redis gate를 수행할 수 없으면 발급은 **fail-closed**로 종료되며 `INTERNAL_FAILURE`를 반환한다.
- Redis gate 예외가 누적되면 circuit breaker가 열리고, open 상태에서는 Redis를 다시 호출하지 않고 즉시 fail-fast 한다.
- 이 경우 SQL 최종 저장 단계는 실행하지 않는다.

### 확장 시 검토 가능(미구현)

- 운영 요구가 커질 경우, 아래 옵션을 단계적으로 검토할 수 있다.
  1. **제한적 SQL fallback**: 비상 상황에서 낮은 트래픽 구간에 한해 Redis gate를 우회하고 SQL 최종 검증만 수행
  2. **degraded mode**: 요청을 즉시 확정하지 않고 임시 접수 후 복구 시점에 확정 처리
- 단, 현재 SQL 최종화는 예전보다 가벼워졌지만 여전히 전면 SQL fallback은 고경합에서 DB 병목 위험이 크다.
- 따라서 기본 정책은 fail-closed를 유지하고, fallback/degraded mode는 별도 부하 검증 후 도입한다.

## 현재 한계와 다음 개선 과제

현재 구현은 정합성과 기본적인 실패 복구를 우선한 구조다. 다만 실서비스 수준으로 고도화하려면 아래 항목이 추가로 필요하다.

- 현재 동시성 정합성 테스트를 넘어, 고경합 상황에서의 성능 측정과 부하 검증 시나리오 확장
- Redis rollback 또는 reconciliation 자체가 실패했을 때 후속 복구가 가능하도록 재시도/로그/메트릭/알람 추가
- 발급 가능 시간 경계에 걸친 요청도 일관되게 처리할 수 있도록 SQL 최종 저장 시점의 issue window 재검증 검토
- claim marker 정리 과정에서 Redis key 조회 비용을 줄이기 위한 scan/구조 개선 검토
- `countByCouponId` 기반 최종 검증 비용을 줄이기 위한 카운터 전략 또는 비동기 확정 구조 검토
