# DECISIONS.md

설계 과정에서 고민했던 주요 기술적 쟁점과 선택의 근거를 정리합니다.
각 쟁점은 해당 기능을 구현하면서 실제 테스트 결과를 바탕으로 작성하였습니다.

## 목차

1. [재고 관리 전략 — 동시성 제어 및 공정성](#쟁점-1-재고-관리-전략--동시성-제어-및-공정성)
2. [결제 수단 확장성 — Strategy 패턴 도입](#쟁점-2-결제-수단-확장성--strategy-패턴-도입)
3. [멱등성 처리 전략](#쟁점-3-멱등성-처리-전략)
4. [장애 대응 및 고가용성](#쟁점-4-장애-대응-및-고가용성)
5. [트랜잭션 설계 및 보상 전략](#쟁점-5-트랜잭션-설계-및-보상-전략)
6. [DB 커넥션 풀 설정](#쟁점-6-db-커넥션-풀-설정)
7. [기타 설계 판단](#쟁점-7-기타-설계-판단)

---

## 쟁점 1. 재고 관리 전략 — 동시성 제어 및 공정성

### 상황

10개 한정 상품에 00시 오픈 시 1000TPS가 몰릴 때, 초과판매 없이 빠르게 재고를 차감하고 모든 사용자에게 동등한 구매 기회를 보장해야 합니다.

### 선택지

| 방식 | 핵심 원리 |
|------|---------|
| DB 비관적 락 | `SELECT ... FOR UPDATE`로 재고 row에 쓰기 락, 순차 처리 |
| DB 낙관적 락 | `@Version` 기반 충돌 감지 + 재시도 (최대 100회, 랜덤 backoff) |
| Redis Lua | Lua 스크립트로 재고 확인 + 차감을 원자적 처리, DB 접근 불필요 |

### 왜 그렇게 판단했는지

세 가지 방식을 모두 구현한 뒤 동시성 벤치마크를 수행하였습니다.

**재고 차감 벤치마크 (1000 동시 요청, 재고 10개, 스레드 64)**

| 지표 | 비관적 락 | 낙관적 락 | Redis Lua |
|------|----------|----------|-----------|
| 총 소요 | 1165ms | 2258ms | **585ms** |
| 평균 | 72ms | 142ms | **35ms** |
| P95 | 210ms | 491ms | **83ms** |
| P99 | 263ms | 640ms | **99ms** |
| 최대 | 437ms | 804ms | **127ms** |
| 초과판매 | 없음 | 없음 | 없음 |
| DB 커넥션 | 모든 요청 | 모든 요청 + 재시도 | **성공 건만** |

> 테스트 환경: MySQL 8.0 / Redis 7 (Testcontainers), `StockServiceConcurrencyTest`

**API 부하테스트 결과 (k6, Docker Compose 환경)**

| 지표 | Booking API | Checkout API |
|------|------------|-------------|
| 동시 요청 | 1000건 (VU 100) | 50 VUs × 10초 |
| 처리량(RPS) | ~706 | **~1960** |
| 평균 응답 | 137ms | **25ms** |
| P95 | 652ms | **68ms** |
| 성공 | 10건 (재고 수량) | 19626건 (100%) |
| 재고 부족 거절 | 990건 | - |
| 초과판매 | **0건** | - |

> 테스트 환경: Nginx + Spring Boot 2대 + MySQL + Redis (Docker Compose), `infra/k6/`

**최종 선택: Redis Lua 스크립트**

- 모든 성능 지표에서 우수하며, DB 커넥션을 점유하지 않아 1000TPS에서도 커넥션 풀 고갈 위험 없음
- Redis 단일 스레드 특성으로 도착한 요청을 순서대로 원자 처리 (FIFO) → 서버 간 경쟁 조건을 제거하여 **공정성 확보**. 네트워크 지연·JVM 스케줄링 등 애플리케이션 외부 변수로 완전한 확률적 공정성을 보장하기는 어려우나, 사용자별 Rate Limit(초당 5회)으로 봇·반복 클릭의 기회 독점을 억제하여 실무적으로 불공정을 최소화
- 재고 소진 후 Redis에서 즉시 거절하여 DB 부하 차단
- 낙관적 락은 단일 row 극심한 경합 시 재시도 비용이 누적되어 비관적 락보다 오히려 느림 → 제외
- API 부하테스트에서 1000건 동시 요청 시 정확히 10건만 성공하고 초과판매 0건을 확인

**트레이드오프**

- Redis 장애 시 DB 비관적 락으로 Fallback (성능 저하를 감수하되 서비스 중단 방지, 쟁점 4 참고)
- Redis는 휘발성이므로 DB를 최종 정합성 기준으로 유지
- Redis 서버 1대 추가 운영 필요하나, 멱등성 키·Rate Limiting 등에서도 활용하므로 비용 대비 효과 충분

---

## 쟁점 2. 결제 수단 확장성 — Strategy 패턴 도입

### 상황

신용카드, Y페이, Y포인트를 지원하면서 새 결제 수단 추가 시 기존 비즈니스 로직 수정을 최소화하는 구조가 필요합니다.

### 선택지

| 방식 | 핵심 원리 |
|------|---------|
| if-else 분기 | `PaymentService` 내에서 결제 수단별 `if-else`로 분기 처리 |
| Strategy 패턴 | `PaymentStrategy` 인터페이스 + 결제 수단별 구현체, Spring DI 자동 등록 |

### 왜 그렇게 판단했는지

가장 단순한 방식부터 시작하여 단계적으로 리팩토링하며 필요성을 검증하였습니다.

**Step 1. if-else 분기로 구현**

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

**최종 선택: Strategy 패턴 + PaymentMethodType 일반화**

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

### 상황

사용자가 결제 버튼을 빠르게 연속 클릭하거나, 네트워크 오류로 요청이 재전송되는 경우 중복 결제를 방지해야 합니다.

### 선택지

| 방식 | 핵심 원리 |
|------|---------|
| DB UNIQUE 제약조건 | `orders.idempotency_key`에 UNIQUE 제약 |
| Redis 기반 멱등성 키 | Redis에 키 저장 (TTL 24h), O(1) 조회 |
| 서버 메모리 캐시 | 서버 내 Map에 키 저장 |

### 왜 그렇게 판단했는지

**최종 선택: Redis SETNX 기반 원자적 선점 + DB UNIQUE Fallback**

- 멱등성 체크는 모든 Booking 요청의 첫 단계이므로 속도가 중요 → Redis O(1) 조회
- TTL 24시간으로 불필요한 키가 자동 정리됨
- 분산 환경(서버 2대)에서 동일한 Redis를 바라보므로, 어느 서버로 요청이 들어와도 중복 체크 가능
- 서버 메모리 캐시는 분산 환경에서 서버 간 공유 불가 → 제외
- 클라이언트가 `Idempotency-Key` 헤더로 UUID를 전송하는 표준 방식 채택

**SETNX 도입 배경**

초기에는 `exists()`(확인)와 `save()`(저장)를 분리하여 구현하였으나, 동일한 멱등성 키로 동시에 2개 요청이 들어오면 둘 다 `exists() == false`를 통과하여 재고가 2개 차감되는 문제가 있었습니다. DB UNIQUE 제약조건이 최종 방어선이 되지만, 불필요한 재고 차감/복구가 발생합니다.

이를 해결하기 위해 `IdempotencyService.tryAcquire()`에서 Redis `SETNX`(SET if Not eXists)를 사용하여 확인과 선점을 원자적으로 처리합니다.

**처리 흐름**

1. Redis SETNX로 멱등성 키 선점 시도 (TTL 24h)
2. 선점 실패(이미 존재) → DB에서 기존 주문 조회 후 반환
3. 선점 성공 → 예약 플로우 진행
4. 예약 실패 시 → `release()`로 키 삭제 (재시도 허용)

**트레이드오프**

- Redis와 DB 양쪽에 멱등성 체크 수단을 두어 단일 장애점 제거
- Redis 장애 시 DB에서 `idempotency_key`로 기존 주문 존재 여부를 확인하여 Fallback
- TTL 24시간 이후 같은 키로 재요청하면 새 주문이 생성될 수 있으나, 24시간 이후 동일 키 재사용은 실무적으로 발생하지 않는 시나리오

---

## 쟁점 4. 장애 대응 및 고가용성

### 상황

평시 50TPS에서 00시 프로모션 시 500~1000TPS로 급증하며, Redis 장애·PG 장애 등 다양한 실패 시나리오에서도 서비스 중단 없이 핵심 기능을 유지해야 합니다. 인프라 증설(Scale-up/out)이 제한적인 상황을 가정합니다.

### 선택지

**Redis 장애 대응**

| 방식 | 핵심 원리 |
|------|---------|
| 서비스 중단 (fail-fast) | Redis 장애 시 즉시 에러 반환 |
| DB Fallback | Redis 장애 감지 → DB 비관적 락으로 자동 전환 |
| 로컬 캐시 Fallback | 서버 메모리에 재고 캐싱 |

**외부 결제 장애 대응**

| 방식 | 핵심 원리 |
|------|---------|
| 단순 재시도 | 실패 시 N회 재시도 |
| 서킷브레이커 | 장애 감지 시 호출 차단, 자동 복구 |

### 왜 그렇게 판단했는지

#### Redis 장애 → DB Fallback (비관적 락)

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
| Rate Limiting | Redis Lua Script | 로컬 메모리 Rate Limiting |

**구현 방식**

- `RedisStockService`에 Resilience4j 서킷브레이커(`redis`)를 적용하여 Redis 반복 장애 시 타임아웃 대기 없이 즉시 DB Fallback 전환
- 서킷 OPEN 시 Redis 연결 시도 자체를 생략하여 응답 지연 방지
- `RedisStockService.decrease()`가 `boolean`(Redis 사용 여부)을 반환하여 보상 트랜잭션 시 Redis 복구 필요 여부를 판단
- DB Fallback으로 재고를 이미 차감한 경우, `OrderTransactionService`에서 DB 재고 중복 차감을 방지

**Redis 재고 초기화 (`StockInitializer`)**

- 애플리케이션 시작 시 `ApplicationRunner`로 DB 재고를 Redis에 동기화
- 분산 환경(서버 2대)에서 두 서버가 동시에 `initStock`을 호출하지만, Redis `SET`은 멱등 연산이므로 동일한 값을 덮어쓸 뿐 정합성에 영향 없음
- 별도의 분산 락이나 리더 선출 없이도 안전하게 동작하여 불필요한 복잡도를 피함

#### 외부 결제 장애 → 서킷브레이커 (Resilience4j)

`ExternalPaymentStrategy`에서 외부 결제 클라이언트 호출을 `CircuitBreaker.executeSupplier()`로 감싸 장애 전파를 방지합니다. 서킷브레이커는 PG사별로 분리(`creditCard`, `yPay`)하여 한 PG사의 장애가 다른 정상 PG사까지 차단하지 않도록 격리합니다.

| 설정 | 값 | 근거 |
|------|---|------|
| sliding-window-type | COUNT_BASED | 요청 수 기반이 시간 기반보다 직관적 |
| sliding-window-size | 10 | 최근 10건 기준으로 판단 |
| failure-rate-threshold | 50% | 10건 중 5건 실패 시 서킷 오픈 |
| wait-duration-in-open-state | 10s | 10초 후 반오픈 상태로 전환 |
| permitted-number-of-calls-in-half-open-state | 3 | 3건 시도하여 복구 여부 판단 |
| minimum-number-of-calls | 5 | 최소 5건 이후부터 실패율 계산 |

서킷 OPEN 시 `CallNotPermittedException` → 외부 호출 없이 즉시 `PAYMENT_SERVICE_UNAVAILABLE` 반환 → 스레드 점유 방지

**결제 실패 케이스 분류**

| 실패 유형 | 원인 | ErrorCode | HTTP |
|----------|------|-----------|------|
| 한도 초과 | 사용자 결제 한도 초과 | PAYMENT_LIMIT_EXCEEDED | 400 |
| 타임아웃/네트워크 오류 | PG/Y페이 응답 지연, 연결 실패 | PAYMENT_FAILED | 500 |
| 서킷 오픈 | 장애 누적으로 서킷브레이커 차단 | PAYMENT_SERVICE_UNAVAILABLE | 503 |
| 기타 실패 | 그 외 거절 사유 | PAYMENT_FAILED | 500 |

#### 트래픽 급증 대응 (인프라 증설 없이)

| 구간 | 1000TPS 중 처리량 | 설명 |
|------|-----------------|------|
| Rate Limiting | 봇/반복 요청 차단 | Lua 스크립트로 사용자별 초당 5회 제한 (`application.yml`에서 설정 변경 가능), Redis 장애 시 로컬 메모리 Fallback |
| Redis 재고 차감 | 10건 성공, 나머지 즉시 거절 | 재고 소진 후 DB 접근 0 |
| DB 결제/주문 | 최대 10건 | 커넥션 풀 여유 |

Redis가 대부분의 트래픽을 흡수하므로 DB에 도달하는 요청은 실제 결제 건수로 제한됩니다.

글로벌(서버 전체) Rate Limit은 의도적으로 적용하지 않았습니다. 재고가 10개이므로 Redis Lua 스크립트에서 재고 소진 후 즉시 거절하는 구조가 이미 글로벌 트래픽 제한 역할을 합니다. 별도의 글로벌 Rate Limit을 추가하면 정상 사용자의 요청도 임계값에 따라 거절될 수 있어, 오히려 공정성을 해칠 수 있다고 판단하였습니다.

Nginx 기본 round-robin을 사용합니다. 재고 차감은 Redis 단일 스레드에서 FIFO로 처리되므로, 어느 서버로 라우팅되든 요청 도착 순서에 따라 공정하게 처리됩니다. `ip_hash` 등 sticky session은 특정 서버에 부하가 집중될 수 있어 오히려 불공정합니다.

### 트레이드오프

- DB Fallback 시 성능 저하는 불가피하나, 재고 10개가 빠르게 소진되므로 실제 락 경합 시간은 짧음
- 서킷 오픈 중에는 정상 요청도 즉시 실패하지만, PG 장애 상태에서 어차피 실패할 요청으로 서버 자원을 낭비하는 것보다 나음
- Redis decrease 성공 후 결제 실패 시 increase로 재고를 복구하는데, 이 시점에 Redis 장애가 발생하면 Redis 재고가 DB보다 1 적게 남을 수 있음. DB 트랜잭션은 롤백되므로 DB 재고는 정상이며, 서버 재시작 시 `StockInitializer`가 DB 기준으로 Redis를 재동기화하여 해소됨
- DB Fallback으로 재고를 차감한 후 결제가 실패하면, `decreaseWithPessimisticLock()`이 별도 트랜잭션에서 이미 커밋되어 있으므로 `increaseWithPessimisticLock()`으로 보상 복구 처리. Redis 정상 경로와 동일하게 결제 실패 시 재고가 원상 복구됨

---

## 쟁점 5. 트랜잭션 설계 및 보상 전략

### 상황

예약 플로우는 Redis, DB, 외부 결제 등 서로 다른 저장소에 걸쳐 있어 하나의 ACID 트랜잭션으로 묶을 수 없습니다. 또한 복합 결제(포인트 + 카드) 시 포인트 동시성 제어와 결제 실패 시 보상 처리가 필요합니다.

### 선택지

**트랜잭션 관리**

| 방식 | 핵심 원리 |
|------|---------|
| 분산 트랜잭션 (2PC) | 모든 저장소에 걸친 원자적 커밋 |
| 보상 트랜잭션 (Saga) | 각 단계 실패 시 이전 단계를 역으로 되돌림 |

**포인트 동시성 제어**

| 방식 | 핵심 원리 |
|------|---------|
| 낙관적 락 (@Version) | 충돌 시 재시도 |
| 비관적 락 (SELECT FOR UPDATE) | 사용자 row에 쓰기 락, 순차 처리 보장 |
| UPDATE WHERE 조건부 | `UPDATE users SET point_balance = point_balance - ? WHERE point_balance >= ?` |

### 왜 그렇게 판단했는지

#### 보상 트랜잭션 (Saga 패턴)

분산 트랜잭션(2PC)은 구현 복잡도와 성능 부담이 크므로, 각 단계의 실패 시 이전 단계를 역으로 되돌리는 보상 트랜잭션 방식을 선택하였습니다.

**예약 플로우**

```
멱등성 키 선점(SETNX) → 오픈 시간 검증 → Redis 재고 차감 → [DB 트랜잭션: 주문 생성 → 결제 금액 검증 → 결제 → DB 재고 차감 → 주문 확정]
```

**트랜잭션 경계 설계**

`BookingService`(비트랜잭션)가 전체 흐름을 조율하고, `OrderTransactionService`(@Transactional)가 DB 작업을 묶습니다. self-invocation 시 Spring AOP 프록시가 동작하지 않는 문제를 방지하기 위해 별도 클래스로 분리하였습니다.

| 구간 | 트랜잭션 범위 | 이유 |
|------|-------------|------|
| 멱등성 키 선점 (SETNX) | 트랜잭션 외부 | Redis 원자적 연산으로 처리 |
| 오픈 시간 검증 | 트랜잭션 외부 | 재고 차감 전 빠른 차단 |
| Redis 재고 차감 | 트랜잭션 외부 | Redis는 DB 트랜잭션에 참여할 수 없음 |
| 주문 생성 → 결제 금액 검증 → 결제 → DB 재고 차감 → 주문 확정 | @Transactional | DB 작업은 원자적으로 처리 |

**DB 재고 이중 차감 방지 (`decreaseDbStock` 플래그)**

`OrderTransactionService.process()`는 `decreaseDbStock` 파라미터로 DB 재고 차감 여부를 분기합니다.

- Redis 정상 시: Redis에서 재고 차감 성공 → `decreaseDbStock = true` → 트랜잭션 내에서 DB 재고도 동기화
- Redis 장애 시: `StockService.decreaseWithPessimisticLock()`으로 DB에서 이미 차감 → `decreaseDbStock = false` → 트랜잭션 내 DB 차감 생략

**실패 시나리오별 보상 전략**

| 실패 지점 | 보상 동작 |
|----------|---------|
| 오픈 시간 검증 실패 | 멱등성 키 해제 (release) |
| 결제 금액 불일치 | 멱등성 키 해제 + 재고 복구 (Redis INCR 또는 DB increase) |
| 결제 중 포인트 부족 | 멱등성 키 해제 + 재고 복구 (Redis INCR 또는 DB increase) |
| 결제 중 외부 결제 실패 | 포인트 환불 + 멱등성 키 해제 + 재고 복구 (Redis INCR 또는 DB increase) |
| DB 저장 실패 | 결제 취소 + 포인트 환불 + 멱등성 키 해제 + 재고 복구 (Redis INCR 또는 DB increase) |

결제 내부의 보상(포인트 환불, 외부 결제 취소)은 `PaymentService.rollback()`이 Strategy별 `cancel()`로 처리하고, Redis 재고 복구와 멱등성 키 해제는 `BookingService`에서 처리합니다.

#### 포인트 동시성 → 비관적 락

`YPointPaymentStrategy`에서 포인트 잔액 확인 후 차감하는 사이에 다른 트랜잭션이 동일 사용자의 포인트를 차감하면, 잔액이 음수가 될 수 있습니다.

- 재고 관리의 DB Fallback에서도 비관적 락(`findWithLockByProductId`)을 사용하므로, 동일 패턴으로 일관성 유지
- 포인트 차감은 결제 트랜잭션 내에서 실행되므로 락 점유 시간이 짧음
- `UserRepository.findWithLockById()`로 사용자를 조회하여 잔액 검증과 차감이 원자적으로 처리됨

### 트레이드오프

- `catch (Exception e)`로 모든 예외를 포착하여 Redis 재고가 누락 없이 복구되도록 보장
- 결제 실패 시 DB 트랜잭션이 롤백되어 주문(Order) 자체가 저장되지 않음 — 실패 이력을 남기려면 주문 생성과 결제를 별도 트랜잭션으로 분리해야 하는데, 이 경우 결제 성공 후 주문 확정 실패 시 보상 로직이 더 복잡해짐
- 실패 추적을 위해 `OrderTransactionService.logFailure()`에서 userId, productId, idempotencyKey, 실패 사유를 구조화된 로그로 기록
- 결제 취소 실패 시 `Payment.status`를 `CANCEL_FAILED`로 마킹하여 추후 배치 처리 또는 수동 확인이 가능하도록 함

---

## 쟁점 6. DB 커넥션 풀 설정

### 상황

서버 2대 환경에서 MySQL 커넥션 풀 크기를 어떻게 설정할 것인지 결정해야 합니다.

### 선택지

pool size 5 / 10(기본값) / 20 / 30 / 50을 각각 부하테스트로 비교하였습니다.

### 왜 그렇게 판단했는지

**부하테스트 결과 (k6, 1000 동시 요청, pool size별 비교)**

정상 (Redis 사용)

| pool size | 평균 | P90 | P95 | 최대 |
|-----------|------|-----|-----|------|
| 5 | **192ms** | **459ms** | **523ms** | **926ms** |
| 10 (기본값) | 204ms | 533ms | 746ms | 1180ms |
| **20** | 196ms | 514ms | 641ms | 1130ms |
| 30 | 205ms | 519ms | 673ms | 1280ms |
| 50 | 215ms | 530ms | 656ms | 1210ms |

Redis 장애 (DB Fallback)

| pool size | 평균 | P90 | P95 | 최대 |
|-----------|------|-----|-----|------|
| 5 | **168ms** | **359ms** | **449ms** | **709ms** |
| 10 (기본값) | 219ms | 568ms | 700ms | 1180ms |
| **20** | 220ms | 477ms | 558ms | 774ms |
| 30 | 223ms | 549ms | 642ms | 1190ms |
| 50 | 198ms | 413ms | 681ms | 902ms |

> 테스트 환경: Nginx + Spring Boot 2대 + MySQL + Redis (Docker Compose), `infra/k6/booking-load-test.js`

**최종 설정**

| 설정 | 값 | 근거 |
|------|---|------|
| maximum-pool-size | 20 | 정상/Fallback 모두 준수한 성능 + 여유 확보 |
| minimum-idle | 5 | 평시 50TPS에서 불필요한 유휴 커넥션 방지 |
| connection-timeout | 3000ms | 커넥션 확보 실패 시 빠르게 에러 반환 |

### 트레이드오프

- Redis가 대부분의 트래픽을 흡수하여 DB에 도달하는 요청이 실제 결제 건수(최대 10건)로 제한되므로, pool size에 따른 성능 차이가 크지 않음
- pool size 5가 성능 최고이나, Redis 장애 시 모든 요청이 DB 비관적 락을 거치므로 여유를 두어 20으로 설정
- 서버 2대 × 20 = 총 40 커넥션으로 MySQL `max_connections=151`의 26%를 사용하여 충분한 여유 확보
- pool size를 과도하게 키우면(30, 50) 오히려 DB 측 컨텍스트 스위칭 비용으로 성능 저하 확인

---

## 쟁점 7. 기타 설계 판단

### 라이브러리 도입 사유

**Resilience4j**

- 외부 결제 연동부(`creditCard`, `yPay`)와 Redis 연동부(`redis`)에 서킷브레이커 패턴을 적용하기 위해 도입
- PG사별, Redis별로 서킷브레이커를 분리하여 장애가 격리되도록 설계
- Spring Boot 3과의 통합이 우수하며, Netflix Hystrix의 후속으로 가볍고 모듈화되어 있음
- YAML auto-configuration으로 인스턴스별 설정을 선언적으로 관리

**Spring Data Redis**

- Redis Lua 스크립트 기반 재고 차감, 멱등성 키 저장, Rate Limiting에 활용
- `StringRedisTemplate`, `RedisScript` 등 Spring 추상화를 통해 Redis 연동을 간결하게 처리
- Lettuce 기반 비동기 커넥션으로 성능 확보

### 동일 사용자 중복 구매 제한 미적용

현재 구조에서는 같은 사용자가 다른 멱등성 키로 동일 상품을 여러 번 예약할 수 있습니다. 이는 의도적인 판단입니다.

- 요구사항에 1인 1건 제한이 명시되지 않았으며, 실무에서는 비즈니스 정책에 따라 결정되는 사항
- 숙소 예약 특성상 동일 상품을 여러 건 구매하는 시나리오(가족/단체 예약 등)가 존재할 수 있음
- 필요 시 `orders` 테이블에 `(user_id, product_id)` UNIQUE 제약조건을 추가하여 간단히 구현 가능