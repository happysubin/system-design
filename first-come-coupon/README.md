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
- Redis 처리 결과와 SQL 최종 결과가 어긋날 수 있으므로, 보상 처리 또는 재동기화 전략을 고려한다.

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
- 테스트는 `test` 프로필과 H2 기반 설정을 사용한다.

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

## 설계 원칙

- Redis는 빠른 admission gate로 사용하고, 최종 정합성은 SQL에서 보장한다.
- 중복 발급 방지는 Redis 1차 제어 + SQL unique 제약조건으로 이중 방어한다.
- 발급 성공의 동기식 최종 근거는 `CouponIssue` 저장이며, `Coupon.issuedQuantity`는 성공 요청마다 즉시 갱신하지 않는다.
- 구현은 먼저 정합성을 만족시키는 방향으로 진행하고, 이후 성능 최적화를 추가한다.
