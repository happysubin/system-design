# 분산 락 전 단계 안정성 보강 계획

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**목표:** 분산 락을 도입하기 전에, 결제 시작/승인/최종 확정 흐름을 원자적 상태 전이와 더 안전한 키 소비 방식으로 보강한다.

**아키텍처:** `approve`는 `READY -> PENDING` 전이를 조건부 업데이트로 바꿔서 동시에 두 번 승인되지 않게 한다. `webhook`과 `reconcile`은 `PENDING -> DONE/FAILED`를 조건부 전이로 바꾸고, checkout key 소비는 Redis 측에서 더 원자적으로 처리한다.

**기술 스택:** Kotlin, Spring Boot, Spring Data JPA, Spring Data Redis, JUnit 5, Mockito.

---

### 작업 1: checkout key 소비 원자화

**파일:**
- 수정: `src/main/kotlin/com/paymentlab/payment/infrastructure/redis/RedisCheckoutKeyStore.kt`
- 필요시 테스트 추가: `src/test/kotlin/com/paymentlab/payment/application/CheckoutKeyApplicationServiceTest.kt`

**1단계: 실패 테스트 또는 검증 포인트 추가**
- 같은 checkout key가 두 번 소비되지 않는다는 현재 계약을 유지한다.
- 가능하면 Redis 측 consume 로직이 get+delete가 아니라 더 원자적인 연산으로 바뀌도록 확인한다.

**2단계: 최소 구현**
- `StringRedisTemplate.execute` 또는 `GETDEL` 성격의 원자 연산으로 payload 검증 + 삭제를 한 번에 처리한다.

### 작업 2: approve 경로 원자적 상태 전이

**파일:**
- 수정: `src/main/kotlin/com/paymentlab/payment/infrastructure/persistence/PaymentAttemptRepository.kt`
- 수정: `src/main/kotlin/com/paymentlab/payment/application/PaymentApplicationService.kt`
- 수정: `src/test/kotlin/com/paymentlab/payment/application/PaymentApprovalApplicationServiceTest.kt`

**1단계: 실패 테스트 작성**
- 조건부 업데이트 결과가 0이면 승인 상태 전이가 실패해야 한다는 테스트를 추가한다.

**2단계: 최소 구현**
- `id`와 `status = READY`를 조건으로 `PENDING` 및 `pgTransactionId`를 반영하는 repository update 메서드를 추가한다.
- 서비스는 PG 호출 후 조건부 업데이트 결과를 검사하고, 실패하면 상태 전이 충돌로 처리한다.

### 작업 3: webhook / reconcile 조건부 최종 확정

**파일:**
- 수정: `src/main/kotlin/com/paymentlab/payment/infrastructure/persistence/PaymentAttemptRepository.kt`
- 수정: `src/main/kotlin/com/paymentlab/payment/application/PaymentApplicationService.kt`
- 수정: `src/main/kotlin/com/paymentlab/payment/application/PaymentWebhookApplicationService.kt`
- 수정: `src/test/kotlin/com/paymentlab/payment/application/PaymentReconciliationApplicationServiceTest.kt`
- 수정: `src/test/kotlin/com/paymentlab/payment/application/PaymentWebhookApplicationServiceTest.kt`

**1단계: 실패 테스트 작성**
- `PENDING`이 아닌 상태에서는 조건부 최종 확정이 반영되지 않아야 함을 테스트한다.

**2단계: 최소 구현**
- `paymentAttemptId` 또는 `pgTransactionId` + `status = PENDING` 조건으로만 `DONE/FAILED`가 반영되도록 repository update를 추가한다.

### 작업 4: 전체 검증

**파일:**
- 검증만 수행

**검증 명령:**
- `./gradlew test --tests "*PaymentApprovalApplicationServiceTest"`
- `./gradlew test --tests "*PaymentReconciliationApplicationServiceTest"`
- `./gradlew test --tests "*PaymentWebhookApplicationServiceTest"`
- `./gradlew test`

**수동 확인:**
- checkout key 발급 후 결제 시작
- 동일 결제 승인 중복 요청 시 상태 충돌 응답 확인
- webhook/reconcile 중 하나가 최종 확정한 뒤 다른 쪽이 재반영하지 않는지 확인
