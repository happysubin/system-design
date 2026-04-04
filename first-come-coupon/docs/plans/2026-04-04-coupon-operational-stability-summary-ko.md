# 쿠폰 발급 운영 안정성 개선 요약

## 변경 목적

기존 구현은 Redis gate + SQL truth 구조 자체는 올바른 편이었지만, 실패 시 Redis 보상 처리와 예외 응답이 운영 환경에서 다소 불안정했습니다. 이번 작업은 구조를 바꾸지 않고도 실패 후 상태를 더 안전하게 복구하고, API 응답을 더 예측 가능하게 만드는 데 초점을 맞췄습니다.

## 이번에 바꾼 내용

### 1. Redis rollback을 원자적으로 변경

- 기존에는 `INCR`와 `DEL`을 두 번 호출했습니다.
- 이제는 Lua 스크립트 한 번으로 처리합니다.
- claim marker가 있을 때만 stock을 복구하고, marker 삭제까지 같은 원자 구간에서 수행합니다.

대상 파일:

- `src/main/kotlin/com/firstcomecoupon/coupon/infrastructure/redis/CouponClaimRedisGate.kt`

### 2. 중복 발급 예외 분류를 더 엄격하게 변경

- 기존에는 `DataIntegrityViolationException`이면 전부 `ALREADY_CLAIMED`로 처리했습니다.
- 이제는 실제로 `uk_coupon_issue_coupon_member` 제약조건 위반이 확인된 경우에만 중복 발급으로 간주합니다.
- 그 외 무결성 오류는 내부 실패로 취급할 수 있게 분기 기반을 정리했습니다.

대상 파일:

- `src/main/kotlin/com/firstcomecoupon/coupon/application/CouponClaimCompensationHandler.kt`

### 3. 실패 후 Redis 재고를 SQL truth 기준으로 다시 맞추도록 보강

- sold-out 또는 예기치 못한 실패가 발생한 경우, rollback 이후 `reconcileCouponStock(couponId)`를 호출하도록 변경했습니다.
- 이로 인해 실패 직후 Redis 재고가 stale 값으로 남지 않고 SQL 기준으로 다시 정렬됩니다.

대상 파일:

- `src/main/kotlin/com/firstcomecoupon/coupon/application/CouponClaimCompensationHandler.kt`

### 4. 내부 실패 응답을 안정화

- `CouponClaimResult.InternalFailure`를 메시지를 포함하는 형태로 확장했습니다.
- Redis 미초기화와 예기치 못한 런타임 실패를 서로 다른 메시지로 응답할 수 있게 했습니다.
- 컨트롤러에서도 예상 못 한 런타임 예외를 동일한 응답 계약으로 감싸도록 보완했습니다.

대상 파일:

- `src/main/kotlin/com/firstcomecoupon/coupon/domain/CouponClaimResult.kt`
- `src/main/kotlin/com/firstcomecoupon/coupon/api/CouponClaimResponseMapper.kt`
- `src/main/kotlin/com/firstcomecoupon/coupon/application/CouponClaimApplicationService.kt`
- `src/main/kotlin/com/firstcomecoupon/coupon/api/CouponController.kt`

## 테스트 보강

다음 테스트를 추가/수정했습니다.

- non-duplicate integrity failure는 재던지기 되는지 검증
- rollback이 Redis script 기반으로 수행되는지 검증
- 예기치 못한 실패가 발생해도 API가 `INTERNAL_FAILURE` 응답 계약을 유지하는지 검증
- Redis 미초기화 경로가 동일한 응답 계약을 유지하는지 검증
- PostgreSQL + Redis 통합 테스트에서 sold-out 이후 Redis 재고가 SQL truth(`0`)로 재정렬되는지 검증

대상 테스트 파일:

- `src/test/kotlin/com/firstcomecoupon/coupon/application/CouponClaimCompensationHandlerTest.kt`
- `src/test/kotlin/com/firstcomecoupon/coupon/infrastructure/redis/CouponClaimRedisGateTest.kt`
- `src/test/kotlin/com/firstcomecoupon/coupon/api/CouponClaimApiExceptionHandlerTest.kt`
- `src/test/kotlin/com/firstcomecoupon/coupon/api/CouponClaimApiPostgresTest.kt`
- `src/test/kotlin/com/firstcomecoupon/coupon/api/CouponClaimApiTest.kt`

## 검증 결과

- `./gradlew test` 통과
- `./gradlew build` 통과

## 아직 남아 있는 개선 여지

- 진짜 고경합 상황을 검증하는 멀티스레드/부하 테스트는 아직 없습니다.
- stale claim marker 정리까지 포함한 reconciliation 확장은 아직 하지 않았습니다.
- 운영 로그/메트릭은 아직 추가하지 않았습니다.

## 결론

이번 변경으로 시스템은 기존보다 다음 측면에서 더 안전해졌습니다.

- 실패 시 Redis 보상 처리가 더 안전해짐
- 중복 발급 판정이 더 정확해짐
- sold-out 이후 Redis 재고 drift가 줄어듦
- 내부 실패 응답이 더 일관돼짐

즉, 구조를 크게 바꾸지 않고도 운영 안정성의 가장 약한 부분을 우선 보강한 상태입니다.
