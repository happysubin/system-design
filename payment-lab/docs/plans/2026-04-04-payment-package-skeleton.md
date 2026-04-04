# 결제 패키지 골격 구현 계획

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**목표:** `first-come-coupon`의 계층 구조를 따라 `payment-lab`에 초기 `payment` 패키지 골격을 추가한다.

**아키텍처:** `com.paymentlab.payment` 아래에 `api`, `application`, `domain`, `infrastructure`의 4계층을 둔다. 첫 단계에서는 전체 결제 기능을 완성하지 않고, 테스트로 패키지 경계를 고정할 수 있을 만큼의 최소한의 도메인 타입, 서비스/컨트롤러 골격, 저장소/PG 어댑터 인터페이스만 만든다.

**기술 스택:** Kotlin, Spring Boot, Spring Web, Spring Data JPA, JUnit 5, Mockito.

---

### 작업 1: 실패하는 계층 구조 테스트 추가

**파일:**
- Create: `src/test/kotlin/com/paymentlab/payment/PaymentLayerStructureTest.kt`

**1단계: 실패하는 테스트 작성**

아래 클래스가 존재해야만 통과하는 reflection 기반 테스트를 작성한다.
- `com.paymentlab.payment.api.PaymentController`
- `com.paymentlab.payment.api.WebhookController`
- `com.paymentlab.payment.application.PaymentApplicationService`
- `com.paymentlab.payment.application.PaymentWebhookApplicationService`
- `com.paymentlab.payment.domain.Payment`
- `com.paymentlab.payment.domain.PaymentStatus`
- `com.paymentlab.payment.infrastructure.persistence.PaymentRepository`
- `com.paymentlab.payment.infrastructure.pg.PgClient`

**2단계: 테스트가 실제로 실패하는지 확인**

Run: `./gradlew test --tests com.paymentlab.payment.PaymentLayerStructureTest`

기대 결과: 패키지와 클래스가 아직 없으므로 FAIL.

### 작업 2: 골격 컴파일에 필요한 최소 의존성 추가

**파일:**
- Modify: `build.gradle.kts`

**1단계: 최소 의존성 추가**

패키지 골격이 컴파일될 만큼만 추가한다.
- `kotlin("plugin.jpa")`
- `spring-boot-starter-web`
- `spring-boot-starter-data-jpa`
- Mockito JUnit Jupiter for application-layer tests if needed

**2단계: 실패 테스트를 다시 실행**

Run: `./gradlew test --tests com.paymentlab.payment.PaymentLayerStructureTest`

기대 결과: 여전히 FAIL이지만, 이제는 프레임워크 타입 부족이 아니라 클래스 부재로 실패해야 한다.

### 작업 3: 최소 소스 골격 구현

**파일:**
- Create: `src/main/kotlin/com/paymentlab/payment/api/PaymentController.kt`
- Create: `src/main/kotlin/com/paymentlab/payment/api/WebhookController.kt`
- Create: `src/main/kotlin/com/paymentlab/payment/api/dto/CreatePaymentRequest.kt`
- Create: `src/main/kotlin/com/paymentlab/payment/api/dto/PaymentWebhookRequest.kt`
- Create: `src/main/kotlin/com/paymentlab/payment/application/PaymentApplicationService.kt`
- Create: `src/main/kotlin/com/paymentlab/payment/application/PaymentWebhookApplicationService.kt`
- Create: `src/main/kotlin/com/paymentlab/payment/domain/Payment.kt`
- Create: `src/main/kotlin/com/paymentlab/payment/domain/PaymentStatus.kt`
- Create: `src/main/kotlin/com/paymentlab/payment/infrastructure/persistence/PaymentRepository.kt`
- Create: `src/main/kotlin/com/paymentlab/payment/infrastructure/pg/PgClient.kt`

**1단계: 최소 코드 작성**

구현은 골격 수준으로 유지한다.
- Controllers can expose placeholder endpoints with request DTOs
- Application services can be empty `@Service` classes
- `Payment` should be a minimal JPA entity with status and identifiers aligned to README
- `PaymentStatus` should contain `READY`, `AUTH_REQUESTED`, `PENDING`, `DONE`, `FAILED`, `CANCELLED`, `EXPIRED`
- Repository should extend `JpaRepository<Payment, Long>`
- `PgClient` should be an interface only

**2단계: 테스트 통과 확인**

Run: `./gradlew test --tests com.paymentlab.payment.PaymentLayerStructureTest`

기대 결과: PASS

### 작업 4: 애플리케이션 계층 스타일 테스트 1개 추가

**파일:**
- Create: `src/test/kotlin/com/paymentlab/payment/application/PaymentApplicationServiceTest.kt`

**1단계: 실패하는 테스트 작성**

`PaymentApplicationService`가 `PaymentRepository`, `PgClient`를 주입받는 구조인지 검증하는 테스트를 작성한다.

**2단계: 실패 확인**

Run: `./gradlew test --tests com.paymentlab.payment.application.PaymentApplicationServiceTest`

기대 결과: 생성자 의존성이 없으면 FAIL.

**3단계: 최소 생성자 기반 서비스 형태 구현**

메서드는 최소화하거나 생략하고, 의존 방향만 고정한다.

**4단계: 대상 테스트 재실행**

Run: `./gradlew test --tests com.paymentlab.payment.PaymentLayerStructureTest --tests com.paymentlab.payment.application.PaymentApplicationServiceTest`

기대 결과: PASS

### 작업 5: 프로젝트 상태 검증

**파일:**
- No new files; verification only

**1단계: 검증 실행**

Run:
- `./gradlew test`

**2단계: 범위 확인**

결과물은 전체 결제 기능이 아니라 초기 패키지 골격이어야 한다.
