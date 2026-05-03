# 코드 리뷰 피드백

## 1. 코드 이슈

### 1-1. YPointPaymentStrategy.cancel() 비관적 락 누락 (심각도: 높음)

**파일**: `YPointPaymentStrategy.java:42`

**현재 코드**:
```java
public void cancel(Payment payment, Order order) {
    User user = userRepository.findById(order.getUser().getId()).orElseThrow();
    user.restorePoints(payment.getAmount());
}
```

**문제**: `findById()`는 락 없이 조회하므로, 포인트 환불 중 다른 트랜잭션이 동시에 포인트를 차감하면 Race Condition이 발생하여 포인트 정합성이 깨질 수 있다.

**수정**:
```java
User user = userRepository.findWithLockById(order.getUser().getId()).orElseThrow();
```

---

### 1-2. Circuit Breaker 단일 인스턴스 공유 (심각도: 높음)

**파일**: `ExternalPaymentStrategy.java:20`

**현재 코드**:
```java
this.circuitBreaker = registry.circuitBreaker("externalPayment");
```

**문제**: PgClient와 YPayClient가 동일한 서킷브레이커를 공유한다. PG사 한 곳의 장애가 정상적인 다른 PG사까지 차단할 수 있어, 장애 격리가 되지 않는다.

**수정**: 생성자에서 PG사별 이름을 받아 분리한다.
```java
protected ExternalPaymentStrategy(ExternalPaymentClient client, CircuitBreakerRegistry registry, String circuitBreakerName) {
    this.circuitBreaker = registry.circuitBreaker(circuitBreakerName);
}
```

---

### 1-3. DataIntegrityViolationException 발생 시 멱등성 키 미해제 (심각도: 중간)

**파일**: `BookingService.java:53-57`

**현재 코드**:
```java
} catch (DataIntegrityViolationException e) {
    if (redisUsed) {
        redisStockService.increase(product.getId());
    }
    throw new GeneralException(ErrorCode.DUPLICATE_ORDER);
}
```

**문제**: Redis에 선점한 멱등성 키를 `release()` 하지 않는다. `DataIntegrityViolationException`이 idempotencyKey UNIQUE 위반이 아닌 다른 제약조건 위반으로 발생한 경우, 24시간 동안 해당 키로 재시도가 불가능해진다.

**수정**: release 호출을 추가하거나, 예외 원인을 구분하여 처리한다.

---

### 1-4. ExternalPaymentStrategy - 모든 예외를 PAYMENT_TIMEOUT으로 처리 (심각도: 중간)

**파일**: `ExternalPaymentStrategy.java:31-33`

**현재 코드**:
```java
} catch (Exception e) {
    payment.fail();
    throw new GeneralException(ErrorCode.PAYMENT_TIMEOUT);
}
```

**문제**: `CallNotPermittedException` 외의 모든 예외를 타임아웃으로 처리한다. 네트워크 오류, NullPointerException 등도 전부 `PAYMENT_TIMEOUT(503)`이 되어 디버깅이 어렵고, 원본 예외 정보가 유실된다.

**수정**:
```java
} catch (Exception e) {
    log.error("외부 결제 실패: {}", e.getMessage(), e);
    payment.fail();
    throw new GeneralException(ErrorCode.PAYMENT_FAILED);
}
```

---

### 1-5. PaymentService.rollback() - 취소 실패 시 보상 없음 (심각도: 중간)

**파일**: `PaymentService.java:89-101`

**현재 코드**:
```java
private void rollback(List<Payment> completedPayments, Order order) {
    for (Payment completed : completedPayments) {
        try {
            strategy.cancel(completed, order);
            completed.cancel();
            paymentRepository.save(completed);
        } catch (Exception e) {
            log.error("결제 취소 실패 ...");
        }
    }
}
```

**문제**: 외부 PG 취소가 실패해도 로그만 남기고 넘어간다. 결제는 성공했는데 취소가 안 된 상태가 되어 사용자에게 돈이 빠져나간 채로 남을 수 있다. 재시도 메커니즘이나 보상 큐가 없어 운영 환경에서 수동 처리가 필요하다.

**개선 방안**: 취소 실패 건을 별도 테이블에 기록하여 추후 배치 처리 또는 수동 확인이 가능하도록 한다.

---

### 1-6. RateLimitInterceptor - userIdHeader 파싱 예외 미처리 (심각도: 낮음)

**파일**: `RateLimitInterceptor.java:25`

**현재 코드**:
```java
Long userId = Long.valueOf(userIdHeader);
```

**문제**: `X-User-Id`에 숫자가 아닌 값이 오면 `NumberFormatException`이 발생하고, `GeneralExceptionHandler`의 catch-all `Exception` 핸들러에 걸려서 500 응답이 된다. 400이 적절하다.

---

### 1-7. Product.isSaleOpen() - 날짜 없이 시간만 비교 (심각도: 낮음)

**파일**: `Product.java:53-55`

**현재 코드**:
```java
public boolean isSaleOpen() {
    return !LocalTime.now().isBefore(saleStartTime);
}
```

**문제**: `LocalTime`만 비교하므로 판매 시작일(날짜) 개념이 없다. 상품이 판매 종료된 후에도 다음 날 00시에 다시 `isSaleOpen = true`로 보인다. 재고가 0이면 구매 불가지만, Checkout API에서 판매 중으로 표시되는 것은 혼동의 여지가 있다.

---

### 1-8. User 엔티티 detached 상태로 트랜잭션 전달 (심각도: 낮음)

**파일**: `BookingService.java:45` → `OrderTransactionService.java:27`

**현재 코드**:
```java
// BookingService (트랜잭션 없음)
User user = userService.getUser(userId);

// OrderTransactionService (@Transactional)
public OrderResult process(User user, ...) {
    Order order = Order.builder().user(user)...
```

**문제**: `BookingService`에는 `@Transactional`이 없어 `UserService.getUser()`에서 조회한 User는 detached 상태이다. 이 User를 `OrderTransactionService`의 새 트랜잭션에서 Order에 연결한다. 현재는 `@ManyToOne` FK만 저장되고, `YPointPaymentStrategy.pay()`에서 `findWithLockById`로 다시 조회하고 있어 동작에 문제는 없지만, 코드의 의도가 명확하지 않다.

---

## 2. 설계/아키텍처 개선점

### 2-1. 결제 실패 시 Order 이력 미저장

현재 결제 실패 시 트랜잭션이 롤백되면서 Order가 DB에 저장되지 않아 실패 이력 추적이 불가능하다. 별도 트랜잭션으로 실패 로그를 남기는 방안을 고려할 수 있다.

### 2-2. Redis 재초기화 전략 부재

`StockInitializer`가 애플리케이션 시작 시에만 Redis 재고를 초기화한다. 운영 중 Redis 데이터 손실(재시작, 메모리 부족 등) 시 재고 동기화 방법이 없다. Admin API나 스케줄링 기반 동기화 메커니즘이 있으면 좋다.

### 2-3. Redis Fallback 시 Rate Limiting 완전 비활성화

Redis 장애 시 Rate Limiting이 완전히 꺼져 서버가 무방비 상태가 된다. 로컬 메모리 기반 간이 제한(예: `ConcurrentHashMap` + `AtomicInteger`)으로 최소한의 방어를 추가하는 것이 좋다.

---

## 3. 테스트 개선점

### 3-1. 단위 테스트 부재

모든 테스트가 `@SpringBootTest` 통합 테스트이다. 핵심 비즈니스 로직(결제 조합 검증, 금액 계산 등)에 대한 순수 단위 테스트를 추가하면 빠른 피드백 루프를 확보할 수 있다.

### 3-2. 컨트롤러 레이어 테스트 없음

API 요청/응답 형식, 헤더 검증(`X-User-Id`, `Idempotency-Key`), HTTP 상태 코드 등을 검증하는 `@WebMvcTest` 또는 MockMvc 기반 테스트가 없다.

### 3-3. Edge Case 테스트 보강

- 결제 금액 0원 요청
- 존재하지 않는 상품/사용자 요청
- 동시에 같은 사용자가 다른 상품을 예약하는 시나리오

---

## 4. 이슈 요약

| # | 이슈 | 심각도 | 상태 |
|---|------|--------|------|
| 1-1 | YPointPaymentStrategy.cancel() 락 누락 | 높음 | 수정 필요 |
| 1-2 | Circuit Breaker 단일 인스턴스 공유 | 높음 | 수정 필요 |
| 1-3 | DataIntegrityViolation 시 멱등성 키 미해제 | 중간 | 수정 권장 |
| 1-4 | 모든 PG 예외를 TIMEOUT으로 처리 | 중간 | 수정 권장 |
| 1-5 | rollback() 취소 실패 시 보상 없음 | 중간 | 수정 권장 |
| 1-6 | RateLimitInterceptor 파싱 예외 500 응답 | 낮음 | 개선 권장 |
| 1-7 | Product.isSaleOpen() 날짜 미고려 | 낮음 | 인지 |
| 1-8 | User detached 상태로 트랜잭션 전달 | 낮음 | 인지 |