# 외부 주문 기반 결제 흐름 구현 계획

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**목표:** 결제 서비스가 내부 주문 생성에 의존하지 않고, 외부 주문서 시스템이 넘겨준 주문 정보로 결제 시도를 시작하도록 구조를 정리한다.

**아키텍처:** `POST /payments`가 `orderId`, `merchantOrderId`, `amount`, `idempotencyKey`를 직접 받아 결제 시도를 생성한다. `PaymentAttempt`는 외부 주문의 스냅샷을 직접 들고 있고, 결제 승인·웹훅·재확정은 이 스냅샷 데이터를 기준으로 동작한다.

**기술 스택:** Kotlin, Spring Boot, Spring Data JPA, JUnit 5, Mockito.

---

### 작업 1: 실패하는 결제 생성 테스트로 외부 주문 계약 고정

**파일:**
- 수정: `src/test/kotlin/com/paymentlab/payment/application/PaymentApplicationServiceTest.kt`
- 수정: `src/test/kotlin/com/paymentlab/payment/api/PaymentApiTest.kt`
- 수정: `src/main/kotlin/com/paymentlab/payment/api/dto/CreatePaymentAttemptRequest.kt`

**1단계: 실패하는 테스트 작성**
- 결제 생성 요청 DTO가 `orderId`, `merchantOrderId`, `amount`, `idempotencyKey`를 받도록 테스트를 바꾼다.
- 서비스 테스트는 더 이상 `OrderRepository` 조회를 기대하지 않도록 바꾼다.

**2단계: 테스트 실패 확인**
Run: `./gradlew test --tests com.paymentlab.payment.application.PaymentApplicationServiceTest --tests com.paymentlab.payment.api.PaymentApiTest`

기대 결과: 기존 구현이 `OrderRepository` 의존이라 FAIL.

### 작업 2: PaymentAttempt를 외부 주문 스냅샷 기반으로 변경

**파일:**
- 수정: `src/main/kotlin/com/paymentlab/payment/domain/PaymentAttempt.kt`
- 수정: `src/main/kotlin/com/paymentlab/payment/application/PaymentApplicationService.kt`
- 수정: `src/main/kotlin/com/paymentlab/payment/infrastructure/persistence/PaymentAttemptRepository.kt` (필요시 변경 최소화)

**1단계: 최소 구현 작성**
- `PaymentAttempt`가 `orderId`, `merchantOrderId`, `amount`를 직접 들도록 바꾼다.
- `PaymentApplicationService.createPaymentAttempt()`는 `OrderRepository`를 조회하지 않고 요청값으로 바로 결제 시도를 만든다.
- `approvePaymentAttempt()`는 `paymentAttempt.merchantOrderId`를 PG 승인 요청에 넘긴다.

**2단계: 테스트 통과 확인**
Run: `./gradlew test --tests com.paymentlab.payment.application.PaymentApplicationServiceTest --tests com.paymentlab.payment.api.PaymentApiTest`

기대 결과: PASS.

### 작업 3: README 흐름 정리

**파일:**
- 수정: `README.md`

**1단계: 문서 수정**
- 주문 API는 주문서/외부 주문 시스템 역할로 설명을 내리고,
- 결제 시작은 외부 주문 정보 기반 `POST /payments`로 설명을 맞춘다.

**2단계: 문서 확인**
- 현재 코드 흐름과 문서가 맞는지 다시 읽어 확인한다.

### 작업 4: 전체 검증 및 수동 QA

**파일:**
- 검증만 수행

**1단계: 전체 테스트 실행**
Run: `./gradlew test`

**2단계: 수동 API 확인**
- 앱을 띄우고 `POST /api/v1/payments`에 외부 주문 정보 JSON을 보내 응답을 확인한다.

**3단계: 결과 확인**
- 결제 시도 생성이 외부 주문 정보만으로 동작하고, `READY` 상태 응답이 나오면 완료.
