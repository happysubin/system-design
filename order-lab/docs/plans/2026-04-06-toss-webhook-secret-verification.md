# Toss 웹훅 secret 검증 구현 계획

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**목표:** 토스페이먼츠 기준으로 결제 승인 시 받은 secret을 저장하고, 웹훅 수신 시 같은 secret인지 검증한 뒤에만 상태를 바꾸도록 구현한다.

**아키텍처:** 결제 승인 결과가 `pgTransactionId`와 `webhookSecret`을 함께 반환하도록 바꾸고, `PaymentAttempt`에 secret을 저장한다. 웹훅 요청은 `pgTransactionId`, `merchantOrderId`, `secret`, `result`를 받아 대상 결제를 찾고 secret이 일치할 때만 `PENDING -> DONE/FAILED` 조건부 전이를 수행한다.

**기술 스택:** Kotlin, Spring Boot, Spring Data JPA, JUnit 5, Mockito.

---

### 작업 1: 승인 결과 contract 변경 테스트 추가

**파일:**
- 수정: `src/test/kotlin/com/paymentlab/payment/application/PaymentApprovalApplicationServiceTest.kt`
- 수정: `src/main/kotlin/com/paymentlab/payment/infrastructure/pg/PgClient.kt`
- 수정: `src/main/kotlin/com/paymentlab/payment/infrastructure/pg/StubPgClient.kt`

승인 결과가 `pgTransactionId`와 `webhookSecret`을 함께 다루도록 실패 테스트를 먼저 추가한다.

### 작업 2: PaymentAttempt에 webhook secret 저장

**파일:**
- 수정: `src/main/kotlin/com/paymentlab/payment/domain/PaymentAttempt.kt`
- 수정: `src/main/kotlin/com/paymentlab/payment/infrastructure/persistence/PaymentAttemptRepository.kt`
- 수정: `src/main/kotlin/com/paymentlab/payment/application/PaymentApplicationService.kt`

`READY -> PENDING` 조건부 전이 시 `pgTransactionId`와 함께 `webhookSecret`을 저장한다.

### 작업 3: 웹훅 payload 확장과 secret 검증

**파일:**
- 수정: `src/main/kotlin/com/paymentlab/payment/api/dto/PaymentWebhookRequest.kt`
- 수정: `src/test/kotlin/com/paymentlab/payment/application/PaymentWebhookApplicationServiceTest.kt`
- 수정: `src/test/kotlin/com/paymentlab/payment/api/PaymentWebhookApiTest.kt`
- 수정: `src/main/kotlin/com/paymentlab/payment/application/PaymentWebhookApplicationService.kt`

웹훅 요청에 `merchantOrderId`, `secret`을 추가하고, 저장된 값과 다르면 즉시 거부한다.

### 작업 4: 웹훅 검증 실패 HTTP 응답 정리

**파일:**
- 수정: `src/main/kotlin/com/paymentlab/payment/api/PaymentErrorHandler.kt`
- 수정: `src/test/kotlin/com/paymentlab/payment/api/PaymentWebhookApiTest.kt`

secret 또는 merchantOrderId 불일치는 400으로 내려가게 한다.

### 작업 5: README 반영 및 검증

**파일:**
- 수정: `README.md`

토스 기준으로 secret 저장/비교 방식이라는 점을 문서에 반영하고, 전체 테스트와 수동 QA를 수행한다.
