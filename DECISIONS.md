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

- Redis 장애 시 DB 비관적 락으로 Fallback (성능 저하를 감수하되 서비스 중단 방지, Phase 8에서 구현)
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

> Phase 7에서 작성 예정

---

## 쟁점 4. Redis 장애 Fallback 전략

> Phase 8에서 작성 예정

---

## 쟁점 5. 고가용성 — 트래픽 급증 대응

> Phase 9에서 작성 예정

---

## 쟁점 6. 결제 실패 케이스별 대응 전략

> Phase 8에서 작성 예정

---

## 쟁점 7. 트랜잭션 범위 및 보상 전략

> 예약 플로우는 Redis, DB, 외부 결제 등 서로 다른 저장소에 걸쳐 있어 하나의 ACID 트랜잭션으로 묶을 수 없는 상황에서 일관성을 보장하는 방법

### 예약 플로우

```
Redis 재고 차감 → [DB 트랜잭션: 주문 생성 → 결제 → DB 재고 차감 → 주문 확정] → Redis 멱등성 키 저장
```

### 트랜잭션 경계 설계

`BookingService`(비트랜잭션)가 전체 흐름을 조율하고, `OrderTransactionService`(@Transactional)가 DB 작업을 묶습니다.

| 구간 | 트랜잭션 범위 | 이유 |
|------|-------------|------|
| Redis 재고 차감 | 트랜잭션 외부 | Redis는 DB 트랜잭션에 참여할 수 없음 |
| 주문 생성 → 결제 → DB 재고 차감 → 주문 확정 | @Transactional | DB 작업은 원자적으로 처리 |
| Redis 멱등성 키 저장 | 트랜잭션 외부 | 주문 확정 후에만 저장 |

self-invocation 시 Spring AOP 프록시가 동작하지 않는 문제를 방지하기 위해 `OrderTransactionService`를 별도 클래스로 분리하였습니다.

### 실패 시나리오별 보상 전략

| 실패 지점 | 보상 동작 |
|----------|---------|
| 결제 중 포인트 부족 | Redis 재고 복구 (INCR) |
| 결제 중 외부 결제 실패 | 포인트 환불 + Redis 재고 복구 |
| DB 저장 실패 | 결제 취소 + 포인트 환불 + Redis 재고 복구 (DB는 트랜잭션 롤백) |

결제 내부의 보상(포인트 환불, 외부 결제 취소)은 `PaymentService.rollback()`이 Strategy별 `cancel()`로 처리하고, Redis 재고 복구와 주문 상태 변경은 `BookingService`에서 처리합니다.

### 선택 근거

- 분산 트랜잭션(2PC)은 구현 복잡도와 성능 부담이 큼
- 보상 트랜잭션 방식은 각 단계의 실패 시 이전 단계를 역으로 되돌리는 Saga 패턴과 유사
- `catch (Exception e)`로 모든 예외를 포착하여 Redis 재고가 누락 없이 복구되도록 보장

---

## 쟁점 8. 라이브러리 도입 사유

> 각 라이브러리 도입 시점에 작성 예정
