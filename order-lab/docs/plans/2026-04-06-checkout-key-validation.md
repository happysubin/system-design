# 체크아웃 키 검증 구현 계획

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**목표:** 서버가 체크아웃 키를 발급하고 Redis TTL로 저장한 뒤, `POST /payments`에서 이 키를 한 번만 검증하도록 구현한다.

**아키텍처:** 주문서 진입 시점에 대응하는 체크아웃 키 발급 API를 추가하고, 결제 시도 생성 요청은 `checkoutKey`를 함께 받는다. 결제 서비스는 기존 `idempotencyKey` 중복 방지 전에 또는 직후에 Redis의 체크아웃 키를 검증/소비해, 같은 주문서 세션에서 결제 시작이 중복 실행되지 않도록 막는다.

**기술 스택:** Kotlin, Spring Boot, Spring Web, Spring Data JPA, Spring Data Redis, JUnit 5, Mockito.

---

### 작업 1: 실패하는 체크아웃 키 발급/검증 테스트 추가

**파일:**
- 수정: `src/test/kotlin/com/paymentlab/payment/api/PaymentApiTest.kt`
- 수정: `src/test/kotlin/com/paymentlab/payment/application/PaymentApplicationServiceTest.kt`
- 생성: `src/test/kotlin/com/paymentlab/payment/api/CheckoutKeyApiTest.kt`
- 생성: `src/test/kotlin/com/paymentlab/payment/application/CheckoutKeyApplicationServiceTest.kt`

**1단계: 실패 테스트 작성**
- 체크아웃 키 발급 API가 `orderId`, `merchantOrderId`, `amount`를 받아 `checkoutKey`를 반환하는 테스트를 쓴다.
- `POST /payments`는 `checkoutKey` 없이 또는 잘못된 키로는 실패해야 한다는 테스트를 쓴다.
- 정상 키면 결제 시도 생성이 통과해야 한다는 테스트를 쓴다.

**2단계: 테스트 실패 확인**
Run: `./gradlew test --tests com.paymentlab.payment.application.CheckoutKeyApplicationServiceTest --tests com.paymentlab.payment.api.CheckoutKeyApiTest --tests com.paymentlab.payment.application.PaymentApplicationServiceTest --tests com.paymentlab.payment.api.PaymentApiTest`

기대 결과: 관련 클래스/필드/검증이 없어 FAIL.

### 작업 2: Redis 기반 체크아웃 키 계층 추가

**파일:**
- 수정: `build.gradle.kts`
- 수정: `src/main/resources/application.yaml`
- 생성: `src/main/kotlin/com/paymentlab/payment/infrastructure/redis/CheckoutKeyStore.kt`
- 생성: `src/main/kotlin/com/paymentlab/payment/infrastructure/redis/RedisCheckoutKeyStore.kt`

**1단계: 최소 의존성/설정 추가**
- `spring-boot-starter-data-redis` 추가
- Redis 접속 설정과 checkout key TTL 설정 추가

**2단계: 저장 계층 최소 구현**
- 문자열 기반으로 checkout key를 저장/조회/소비하는 인터페이스를 만든다.
- 구현은 `StringRedisTemplate` 기반으로 `setIfAbsent(..., ttl)`와 소비 로직을 사용한다.

### 작업 3: 체크아웃 키 발급/검증 애플리케이션 흐름 구현

**파일:**
- 생성: `src/main/kotlin/com/paymentlab/payment/api/dto/IssueCheckoutKeyRequest.kt`
- 생성: `src/main/kotlin/com/paymentlab/payment/api/dto/IssueCheckoutKeyResponse.kt`
- 수정: `src/main/kotlin/com/paymentlab/payment/api/dto/CreatePaymentAttemptRequest.kt`
- 생성: `src/main/kotlin/com/paymentlab/payment/application/CheckoutKeyApplicationService.kt`
- 생성: `src/main/kotlin/com/paymentlab/payment/api/CheckoutKeyController.kt`
- 수정: `src/main/kotlin/com/paymentlab/payment/application/PaymentApplicationService.kt`

**1단계: 발급 API 구현**
- `POST /api/v1/checkout-keys`를 추가한다.
- `orderId`, `merchantOrderId`, `amount`를 받아 checkout key를 생성하고 Redis에 TTL과 함께 저장한다.

**2단계: 결제 생성 검증 구현**
- `CreatePaymentAttemptRequest`에 `checkoutKey` 추가
- `PaymentApplicationService.createPaymentAttempt()`에서 Redis key 존재/일치 여부를 확인하고 소비한다.
- 유효하지 않으면 예외를 던지고, 유효하면 기존 결제 생성 로직으로 진행한다.

**3단계: 테스트 통과 확인**
Run: 위의 대상 테스트 재실행

기대 결과: PASS.

### 작업 4: README 흐름 정리

**파일:**
- 수정: `README.md`

**1단계: 문서 수정**
- 메인 흐름에 “외부 주문 준비 → checkout key 발급 → `POST /payments`”를 반영한다.
- checkout key가 Redis TTL 기반으로 유효성 검증되는 이유를 추가한다.

### 작업 5: 전체 검증 및 수동 QA

**파일:**
- 검증만 수행

**1단계: 전체 테스트 실행**
Run: `./gradlew test`

**2단계: 수동 API 확인**
- 앱과 Redis를 띄운 뒤 checkout key 발급 API 호출
- 발급된 key로 `POST /payments` 호출
- 동일 key 재사용 시 실패하거나 차단되는지 확인

**3단계: 결과 확인**
- 체크아웃 키가 한 번만 유효하고, 정상 결제 생성은 통과하면 완료.
