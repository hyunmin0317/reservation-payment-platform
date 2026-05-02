# DECISIONS.md

설계 과정에서 고민했던 주요 기술적 쟁점과 선택의 근거를 정리합니다.
각 쟁점은 해당 기능을 구현하면서 실제 테스트 결과를 바탕으로 작성하였습니다.

---

## 쟁점 1. 재고 관리 전략 — 동시성 제어 및 공정성

> 10개 한정 상품에 1000TPS가 몰릴 때, 초과판매 없이 빠르게 재고를 차감하고 모든 사용자에게 동등한 구매 기회를 보장하는 방법

### 검토한 방식

| 방식 | 핵심 원리 |
|------|---------|
| DB 비관적 락 | `SELECT ... FOR UPDATE`로 재고 row에 쓰기 락, 순차 처리 |
| DB 낙관적 락 | `@Version` 기반 충돌 감지 + 재시도 (최대 100회, 랜덤 backoff) |
| Redis Lua | Lua 스크립트로 재고 확인 + 차감을 원자적 처리, DB 접근 불필요 |

### 테스트 결과 비교 (1000 동시 요청, 재고 10개, 스레드 64)

| 지표 | 비관적 락 | 낙관적 락 | Redis Lua |
|------|----------|----------|-----------|
| 총 소요 | 1087ms | 2749ms | **636ms** |
| 평균 | 67ms | 174ms | **38ms** |
| P95 | 139ms | 663ms | **117ms** |
| P99 | 196ms | 729ms | **134ms** |
| 최대 | 251ms | 1161ms | **144ms** |
| 초과판매 | 없음 | 없음 | 없음 |
| DB 커넥션 | 모든 요청 | 모든 요청 + 재시도 | **성공 건만** |

> 테스트 환경: MySQL 8.0 / Redis 7 (Testcontainers), `StockServiceConcurrencyTest`

### 최종 선택: Redis Lua 스크립트

**선택 근거**

- 모든 성능 지표에서 우수하며, DB 커넥션을 점유하지 않아 1000TPS에서도 커넥션 풀 고갈 위험 없음
- Redis 단일 스레드 특성으로 요청 도착 순서대로 처리 (FIFO) → 별도 대기열 없이 **공정성 보장**
- 재고 소진 후 Redis에서 즉시 거절하여 DB 부하 차단
- 낙관적 락은 단일 row 극심한 경합 시 재시도 비용이 누적되어 비관적 락보다 오히려 느림 → 제외

**트레이드오프**

- Redis 장애 시 DB 비관적 락으로 Fallback (성능 저하를 감수하되 서비스 중단 방지, 쟁점 4 참고)
- Redis는 휘발성이므로 DB를 최종 정합성 기준으로 유지
- Redis 서버 1대 추가 운영 필요하나, 멱등성 키(Phase 7)·Rate Limiting(Phase 9) 등에서도 활용하므로 비용 대비 효과 충분

---

## 쟁점 2. 결제 수단 확장성 — Strategy 패턴 도입

> 신용카드, Y페이, Y포인트를 지원하면서 새 결제 수단 추가 시 기존 비즈니스 로직 수정을 최소화하는 구조

### 검토한 방식

| 방식 | 핵심 원리 |
|------|---------|
| if-else 분기 | `PaymentService` 내에서 결제 수단별 `if-else`로 분기 처리 |
| Strategy 패턴 | `PaymentStrategy` 인터페이스 + 결제 수단별 구현체, Spring DI 자동 등록 |

### 구현 과정

**Step 1. if-else 분기로 구현**

가장 단순한 방식으로 먼저 구현하여 동작을 검증하였습니다.

```java
if (request.method() == PaymentMethod.Y_POINT) {
    // 포인트 차감 로직
} else if (request.method() == PaymentMethod.CREDIT_CARD
        || request.method() == PaymentMethod.Y_PAY) {
    // 외부 결제 로직
}
```

- 결제 수단이 3개인 현재는 문제없이 동작
- 단, 새 결제 수단 추가 시 `PaymentService.pay()` 메서드와 보상 트랜잭션 로직 **모두 수정 필요** (OCP 위반)

**Step 2. Strategy 패턴으로 리팩토링**

```java
public interface PaymentStrategy {
    PaymentMethod getMethod();
    void pay(Payment payment, Order order);
    void cancel(Payment payment, Order order);
}
```

- `CreditCardPaymentStrategy`, `YPayPaymentStrategy`, `YPointPaymentStrategy` 구현
- `PaymentService` 생성자에서 `List<PaymentStrategy>`를 주입받아 `Map<PaymentMethod, PaymentStrategy>`로 변환
- 결제 실행과 보상 취소 모두 Strategy에 위임

**Step 3. 클라이언트 분리 및 일반화**

- `ExternalPaymentClient` 인터페이스 정의, 구현체를 `PgClient`(신용카드)와 `YPayClient`(Y페이)로 분리
- `ExternalPaymentStrategy` 추상 클래스로 외부 결제 공통 로직(승인/취소) 추출
- `PaymentMethod`에 `PaymentMethodType`(EXTERNAL/INTERNAL) 도입하여 조합 검증과 실행 순서를 일반화

### 최종 선택: Strategy 패턴 + PaymentMethodType 일반화

**선택 근거**

- 새 결제 수단 추가 시 `PaymentStrategy` 구현체 하나와 `PaymentMethod` enum 값 하나만 추가하면 됨 → `PaymentService` 수정 불필요 (OCP 준수)
- Spring DI가 `List<PaymentStrategy>`를 자동 수집하므로 별도 등록 코드 불필요
- 각 결제 수단의 결제/취소 로직이 구현체에 캡슐화되어 테스트와 유지보수가 용이
- 복합 결제 시 보상 트랜잭션도 `strategy.cancel()`로 위임하여 `PaymentService`가 결제 수단별 취소 방법을 알 필요 없음
- `PaymentMethodType`으로 조합 검증("EXTERNAL은 1개만")과 실행 순서("INTERNAL 먼저")를 일반화하여 결제 수단 추가 시에도 검증/정렬 로직 수정 불필요

**if-else 대비 개선점**

| 항목 | if-else | Strategy 패턴 |
|------|---------|--------------|
| 결제 수단 추가 시 수정 파일 | PaymentService (결제 + 보상 + 검증 + 정렬) | 구현체 1개 + enum 값 1개 추가만 |
| PaymentService 코드량 | 결제 수단 증가에 비례하여 증가 | 결제 수단 수와 무관 |
| 단위 테스트 | 전체 서비스 테스트 필요 | 구현체별 독립 테스트 가능 |

**복합 결제 처리 전략**

- `PaymentMethodType.INTERNAL`(포인트)을 먼저 차감한 뒤 `EXTERNAL`(카드/Y페이) 결제를 진행 (`sortInternalFirst`)
- 외부 결제 실패 시 이미 차감된 포인트를 `cancel()`로 환불하는 보상 트랜잭션 실행
- 외부 결제 수단 혼용은 `validateCombination`에서 "EXTERNAL 수단 2개 이상"을 사전 차단

**트레이드오프**

- 결제 수단 3개인 현 규모에서는 if-else도 충분히 단순하나, 요구사항이 "비즈니스 로직 수정 최소화"를 명시하므로 Strategy 패턴이 적합
- 조합 검증 로직(`validateCombination`)은 `PaymentService`에 유지 — 개별 Strategy가 아닌 조합 간 규칙이므로 서비스 레벨에서 관리하는 것이 자연스러움
- 외부 결제 클라이언트를 PgClient/YPayClient로 분리하여 향후 PG사 API와 Y페이 API가 달라져도 독립적으로 대응 가능

---

## 쟁점 3. 멱등성 처리 전략

> 사용자가 결제 버튼을 빠르게 연속 클릭하거나, 네트워크 오류로 요청이 재전송되는 경우 중복 결제를 방지하는 방법

### 검토한 방식

| 방식 | 핵심 원리 |
|------|---------|
| DB UNIQUE 제약조건 | `orders.idempotency_key`에 UNIQUE 제약 |
| Redis 기반 멱등성 키 | Redis에 키 저장 (TTL 24h), O(1) 조회 |
| 서버 메모리 캐시 | 서버 내 Map에 키 저장 |

### 최종 선택: Redis SETNX 기반 원자적 선점 + DB UNIQUE Fallback

**선택 근거**

- 멱등성 체크는 모든 Booking 요청의 첫 단계이므로 속도가 중요 → Redis O(1) 조회
- TTL 24시간으로 불필요한 키가 자동 정리됨
- 분산 환경(서버 2대)에서 동일한 Redis를 바라보므로, 어느 서버로 요청이 들어와도 중복 체크 가능
- 서버 메모리 캐시는 분산 환경에서 서버 간 공유 불가 → 제외

**SETNX 도입 배경**

초기에는 `exists()`(확인)와 `save()`(저장)를 분리하여 구현하였으나, 동일한 멱등성 키로 동시에 2개 요청이 들어오면 둘 다 `exists() == false`를 통과하여 재고가 2개 차감되는 문제가 있었습니다. DB UNIQUE 제약조건이 최종 방어선이 되지만, 불필요한 재고 차감/복구가 발생합니다.

이를 해결하기 위해 `IdempotencyService.tryAcquire()`에서 Redis `SETNX`(SET if Not eXists)를 사용하여 확인과 선점을 원자적으로 처리합니다.

**Redis 장애 시 Fallback**

- `IdempotencyService.tryAcquire()`: Redis 장애 시 DB에서 `idempotency_key`로 기존 주문 존재 여부 확인
- 클라이언트가 `Idempotency-Key` 헤더로 UUID를 전송하는 표준 방식 채택

**처리 흐름**

1. Redis SETNX로 멱등성 키 선점 시도 (TTL 24h)
2. 선점 실패(이미 존재) → DB에서 기존 주문 조회 후 반환
3. 선점 성공 → 예약 플로우 진행
4. 예약 실패 시 → `release()`로 키 삭제 (재시도 허용)

**트레이드오프**

- Redis와 DB 양쪽에 멱등성 체크 수단을 두어 단일 장애점 제거
- TTL 24시간 이후 같은 키로 재요청하면 새 주문이 생성될 수 있으나, 24시간 이후 동일 키 재사용은 실무적으로 발생하지 않는 시나리오

---

## 쟁점 4. Redis 장애 Fallback 전략

> Redis가 장애 상태일 때 서비스 중단 없이 핵심 기능을 유지하는 방법

### 검토한 방식

| 방식 | 핵심 원리 |
|------|---------|
| 서비스 중단 (fail-fast) | Redis 장애 시 즉시 에러 반환 |
| DB Fallback | Redis 장애 감지 → DB 비관적 락으로 자동 전환 |
| 로컬 캐시 Fallback | 서버 메모리에 재고 캐싱 |

### 최종 선택: DB Fallback (비관적 락)

**선택 근거**

- 00시 프로모션 시간에 Redis 장애가 나면 매출 손실이 크므로, 성능 저하를 감수하더라도 서비스를 지속하는 것이 유리
- 로컬 캐시는 분산 환경(서버 2대)에서 재고 정합성을 보장할 수 없음
- DB 비관적 락은 단일 row 경합에서 낙관적 락보다 효율적 — 재고 10개에 1000TPS가 몰리면 성공률 1%이므로 낙관적 락의 재시도 비용이 폭증
- Redis 복구 시 별도 전환 작업 없이 자동으로 Redis 모드로 복귀

**Fallback 범위**

| 기능 | 정상 시 | Redis 장애 시 |
|------|---------|-------------|
| 재고 관리 | Redis Lua Script | DB 비관적 락 |
| 재고 조회 (Checkout) | Redis GET | DB 조회 |
| 멱등성 선점 | Redis SETNX (TTL 24h) | DB UNIQUE 제약조건 |
| Rate Limiting | Redis Lua Script | 비활성화 |

**구현 방식**

- `RedisConnectionFailureException` catch로 장애 감지
- `RedisStockService.decrease()`가 `boolean`(Redis 사용 여부)을 반환하여 보상 트랜잭션 시 Redis 복구 필요 여부를 판단
- DB Fallback으로 재고를 이미 차감한 경우, `OrderTransactionService`에서 DB 재고 중복 차감을 방지

**Redis 재고 초기화 (`StockInitializer`)**

- 애플리케이션 시작 시 `ApplicationRunner`로 DB 재고를 Redis에 동기화
- 분산 환경(서버 2대)에서 두 서버가 동시에 `initStock`을 호출하지만, Redis `SET`은 멱등 연산이므로 동일한 값을 덮어쓸 뿐 정합성에 영향 없음
- 별도의 분산 락이나 리더 선출 없이도 안전하게 동작하여 불필요한 복잡도를 피함

**트레이드오프**

- DB Fallback 시 성능 저하는 불가피하나, 재고 10개가 빠르게 소진되므로 실제 락 경합 시간은 짧음
- 매 요청마다 Redis를 먼저 시도하므로 Redis 복구 시 자동으로 정상 모드 복귀

---

## 쟁점 5. 고가용성 — 트래픽 급증 대응

> 평시 50TPS에서 00시 프로모션 시 500~1000TPS로 급증할 때, 인프라 증설 없이 시스템을 보호하는 방법

### 적용한 전략

**1. 조기 차단 (Early Rejection)**

Redis Lua 스크립트에서 재고가 0이면 즉시 `return 0` → DB까지 가지 않고 Redis 레벨에서 거절합니다.

- 재고 10개가 소진된 이후의 990+TPS는 Redis에서 즉시 처리
- DB 부하는 실제 결제 건수(최대 10건)로 제한

**2. Rate Limiting**

Lua 스크립트로 INCR + EXPIRE를 원자적으로 처리하여 사용자별 초당 5회 요청을 제한합니다.

- 악의적 반복 요청이나 봇을 차단하여 정상 사용자의 기회 보호
- `/api/bookings` 경로에만 적용 (조회 API는 제한 불필요)
- Redis 장애 시 비활성화 — DB 비관적 락이 자연스러운 속도 제한 역할
- Lua 스크립트로 INCR과 EXPIRE를 하나의 원자적 연산으로 처리하여 Race Condition 방지

**3. 서킷브레이커 (Resilience4j)**

외부 결제(PG, Y페이) 연동 장애 시 서킷브레이커가 빠르게 실패를 반환합니다.

- PG 타임아웃으로 인한 스레드 점유를 방지하여 서버 전체가 멈추는 것을 방지
- 서킷 OPEN 시 `PAYMENT_SERVICE_UNAVAILABLE` 즉시 반환

### 인프라 증설 없이 대응 가능한 이유

| 구간 | 1000TPS 중 처리량 | 설명 |
|------|-----------------|------|
| Rate Limiting | 봇/반복 요청 차단 | 정상 사용자만 통과 |
| Redis 재고 차감 | 10건 성공, 나머지 즉시 거절 | DB 부하 0 |
| DB 결제/주문 | 최대 10건 | 커넥션 풀 여유 |

Redis가 대부분의 트래픽을 흡수하므로 DB에 도달하는 요청은 실제 결제 건수로 제한됩니다.

---

## 쟁점 6. 결제 실패 케이스별 대응 전략

> 외부 결제 연동 장애 시 서버 전체가 멈추지 않도록 보호하는 방법

### 결제 실패 케이스 분류

| 실패 유형 | 원인 | ErrorCode | HTTP |
|----------|------|-----------|------|
| 한도 초과 | 사용자 결제 한도 초과 | PAYMENT_LIMIT_EXCEEDED | 400 |
| 타임아웃 | PG/Y페이 응답 지연 | PAYMENT_TIMEOUT | 503 |
| 네트워크 오류 | 연결 실패, 예외 발생 | PAYMENT_TIMEOUT | 503 |
| 서킷 오픈 | 장애 누적으로 서킷브레이커 차단 | PAYMENT_TIMEOUT | 503 |
| 기타 실패 | 그 외 거절 사유 | PAYMENT_FAILED | 500 |

### 서킷브레이커 (Resilience4j)

`ExternalPaymentStrategy`에서 외부 결제 클라이언트 호출을 `CircuitBreaker.executeSupplier()`로 감싸 장애 전파를 방지합니다.

**설정값 및 근거**

| 설정 | 값 | 근거 |
|------|---|------|
| sliding-window-type | COUNT_BASED | 요청 수 기반이 시간 기반보다 직관적 |
| sliding-window-size | 10 | 최근 10건 기준으로 판단 |
| failure-rate-threshold | 50% | 10건 중 5건 실패 시 서킷 오픈 |
| wait-duration-in-open-state | 10s | 10초 후 반오픈 상태로 전환 |
| permitted-number-of-calls-in-half-open-state | 3 | 3건 시도하여 복구 여부 판단 |
| minimum-number-of-calls | 5 | 최소 5건 이후부터 실패율 계산 |

**서킷 OPEN 시 동작**: `CallNotPermittedException` → 외부 호출 없이 즉시 `PAYMENT_SERVICE_UNAVAILABLE` 반환 → 스레드 점유 방지

**트레이드오프**

- 서킷 오픈 중에는 정상 요청도 즉시 실패하지만, PG 장애 상태에서 어차피 실패할 요청으로 서버 자원을 낭비하는 것보다 나음
- 재고는 이미 Redis에서 차감된 상태이므로 보상 트랜잭션으로 복구

---

## 쟁점 7. 트랜잭션 범위 및 보상 전략

> 예약 플로우는 Redis, DB, 외부 결제 등 서로 다른 저장소에 걸쳐 있어 하나의 ACID 트랜잭션으로 묶을 수 없는 상황에서 일관성을 보장하는 방법

### 예약 플로우

```
멱등성 키 선점(SETNX) → 오픈 시간 검증 → Redis 재고 차감 → [DB 트랜잭션: 주문 생성 → 결제 금액 검증 → 결제 → DB 재고 차감 → 주문 확정]
```

### 트랜잭션 경계 설계

`BookingService`(비트랜잭션)가 전체 흐름을 조율하고, `OrderTransactionService`(@Transactional)가 DB 작업을 묶습니다.

| 구간 | 트랜잭션 범위 | 이유 |
|------|-------------|------|
| 멱등성 키 선점 (SETNX) | 트랜잭션 외부 | Redis 원자적 연산으로 처리 |
| 오픈 시간 검증 | 트랜잭션 외부 | 재고 차감 전 빠른 차단 |
| Redis 재고 차감 | 트랜잭션 외부 | Redis는 DB 트랜잭션에 참여할 수 없음 |
| 주문 생성 → 결제 금액 검증 → 결제 → DB 재고 차감 → 주문 확정 | @Transactional | DB 작업은 원자적으로 처리 |

self-invocation 시 Spring AOP 프록시가 동작하지 않는 문제를 방지하기 위해 `OrderTransactionService`를 별도 클래스로 분리하였습니다.

### 실패 시나리오별 보상 전략

| 실패 지점 | 보상 동작 |
|----------|---------|
| 오픈 시간 검증 실패 | 멱등성 키 해제 (release) |
| 결제 금액 불일치 | 멱등성 키 해제 + Redis 재고 복구 (INCR) |
| 결제 중 포인트 부족 | 멱등성 키 해제 + Redis 재고 복구 (INCR) |
| 결제 중 외부 결제 실패 | 포인트 환불 + 멱등성 키 해제 + Redis 재고 복구 |
| DB 저장 실패 | 결제 취소 + 포인트 환불 + 멱등성 키 해제 + Redis 재고 복구 (DB는 트랜잭션 롤백) |

결제 내부의 보상(포인트 환불, 외부 결제 취소)은 `PaymentService.rollback()`이 Strategy별 `cancel()`로 처리하고, Redis 재고 복구와 멱등성 키 해제는 `BookingService`에서 처리합니다.

### 선택 근거

- 분산 트랜잭션(2PC)은 구현 복잡도와 성능 부담이 큼
- 보상 트랜잭션 방식은 각 단계의 실패 시 이전 단계를 역으로 되돌리는 Saga 패턴과 유사
- `catch (Exception e)`로 모든 예외를 포착하여 Redis 재고가 누락 없이 복구되도록 보장

---

## 쟁점 8. 라이브러리 도입 사유

### Resilience4j

- 외부 결제 연동부에 서킷브레이커 패턴을 적용하기 위해 도입
- Spring Boot 3과의 통합이 우수하며, Netflix Hystrix의 후속으로 가볍고 모듈화되어 있음
- `CircuitBreakerRegistry`를 통한 프로그래밍 방식 적용으로 추상 클래스(`ExternalPaymentStrategy`)에서도 유연하게 사용 가능

### Spring Data Redis

- Redis Lua 스크립트 기반 재고 차감, 멱등성 키 저장, Rate Limiting에 활용
- `StringRedisTemplate`, `RedisScript` 등 Spring 추상화를 통해 Redis 연동을 간결하게 처리
- Lettuce 기반 비동기 커넥션으로 성능 확보
