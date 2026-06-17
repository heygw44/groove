# ADR: 비동기 처리 — 도메인 이벤트 + Outbox (vs 메시지 큐)

| 항목 | 값 |
|---|---|
| 상태 | Accepted |
| 날짜 | 2026-06-17 |
| 연관 이슈 | #257 (W12-2 ADR 정리) |
| 작성자 | ParkGunWoo |
| 관련 문서 | [ARCHITECTURE.md](../ARCHITECTURE.md), [ERD.md §outbox_event](../ERD.md) |

---

## Context

도메인 간에 **부수효과를 비동기로 전파**해야 하는 흐름이 두 종류 있다.

1. **도메인 내 정합성 정리** — 회원 탈퇴 시 장바구니·리프레시 토큰을 비워야 한다. 탈퇴 트랜잭션과 강결합하면 정리 실패가 탈퇴를 롤백시키는 문제가 생긴다.
2. **도메인 간 신뢰성 전달** — 결제가 `PAID` 되면 배송을 생성해야 한다. 결제 트랜잭션 안에서 배송 도메인을 직접 호출하면 결합도가 높아지고, 트랜잭션 밖에서 호출하면 "커밋은 됐는데 배송 트리거는 유실"이 발생할 수 있다.

제약:
- 인프라에 **Kafka/RabbitMQ/SQS 같은 외부 브로커가 없다**(단일 인스턴스·단일 MySQL).
- "상태 변경과 이벤트 발행이 원자적이어야 한다"는 요구(이중 쓰기 문제, dual-write)가 핵심이다.

본 ADR 은 "외부 큐를 도입할 것인가, 아니면 무엇으로 대체할 것인가"를 기록한다.

---

## Decision

**외부 메시지 브로커 없이 두 계층으로 비동기 처리를 구성한다.**

### 1) 도메인 내 정합성 → Spring 인메모리 이벤트 (`@TransactionalEventListener(AFTER_COMMIT)`)

탈퇴 트랜잭션이 **커밋된 뒤** 리스너가 자신의 `REQUIRES_NEW` 트랜잭션에서 정리한다. 정리 실패는 로그로 흡수(탈퇴는 이미 확정).

```java
// cart/application/CartCleanupOnMemberWithdrawnListener.java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void onMemberWithdrawn(MemberWithdrawnEvent event) {
    try {
        cartService.deleteForMember(event.memberId());
    } catch (RuntimeException e) {
        log.error("회원 탈퇴 장바구니 정리 실패 — 탈퇴는 확정", e);  // 흡수
    }
}
```

같은 이벤트를 `auth` 의 리프레시 토큰 정리 리스너도 구독한다(관심사별 독립 리스너).

### 2) 도메인 간 신뢰성 전달 → Outbox 패턴

상태 변경과 이벤트 기록을 **단일 트랜잭션에 함께 커밋**하고, 별도 스케줄러가 미발행 행을 폴링해 컨슈머에 디스패치한다(at-least-once).

```java
// payment/application/PaymentCallbackService.java — PAID 처리 시
order.changeStatus(OrderStatus.PAID, null, now);
outboxEventPublisher.publish(OrderPaidEvent.OUTBOX_AGGREGATE_TYPE, order.getId(),
        OrderPaidEvent.OUTBOX_EVENT_TYPE,
        new OrderPaidEvent(order.getId(), order.getOrderNumber(), order.getMemberId(), payment.getId()));
```

```java
// common/outbox/OutboxEventPublisher.java — 활성 트랜잭션 강제
if (!TransactionSynchronizationManager.isActualTransactionActive()) {
    throw new IllegalStateException("아웃박스 발행은 활성 트랜잭션 안에서 호출해야 합니다 (상태 변경과 원자 커밋)");
}
repository.save(OutboxEvent.of(aggregateType, aggregateId, eventType, json));
```

`OutboxRelayScheduler` 가 `published_at IS NULL` 행을 id FIFO 로 batch 조회 → 건별 `OutboxEventHandler` 디스패치 → 성공 시 `markPublished`(독립 트랜잭션). 한 건 실패는 격리해 다음 주기에 재시도하므로 **컨슈머는 멱등이어야 한다**. 실제로 `OrderPaidOutboxHandler` 는 배송이 이미 있으면 `DataIntegrityViolationException` 을 흡수한다.

---

## Considered Options

### Option A — 직접 동기 메서드 호출 ❌

| 항목 | 내용 |
|---|---|
| 방식 | 결제 서비스가 배송 서비스를 같은 트랜잭션에서 직접 호출 |
| 단점 | 도메인 강결합, 배송 실패가 결제를 롤백, 트랜잭션 비대화 |

### Option B — `@Async` 메서드 ❌

| 항목 | 내용 |
|---|---|
| 방식 | 부수효과를 `@Async` 비동기 실행 |
| 단점 | **커밋 전 발행 시 유실**(트랜잭션 경계와 무관하게 즉시 실행), 재시도·내구성 없음 → 프로젝트에서 미사용(`@Async`/`@EnableAsync` 0건) |

### Option C — 인메모리 이벤트만 (Outbox 없음) ⚠️

| 항목 | 내용 |
|---|---|
| 방식 | 모든 전파를 `@TransactionalEventListener` 로 |
| 적합 | **유실 허용** 정합성 정리(장바구니·토큰) |
| 단점 | AFTER_COMMIT 후 프로세스 다운 시 **이벤트 유실** → 배송 생성처럼 유실 불가 흐름엔 부적합 |

### Option D — Kafka/RabbitMQ 외부 브로커 ❌

| 항목 | 내용 |
|---|---|
| 방식 | 브로커에 이벤트 발행, 컨슈머 그룹 처리 |
| 장점 | 멀티 인스턴스·고처리량·내구 큐 |
| 단점 | **인프라 신규 도입**(운영·모니터링), dual-write 문제는 여전(브로커+DB) → 결국 Outbox 필요. 단일 인스턴스엔 과투자 |

### Option E — 인메모리 이벤트 + Outbox ✅ (채택)

| 항목 | 내용 |
|---|---|
| 방식 | 유실 허용 정리=인메모리 이벤트, 유실 불가 전달=Outbox(DB 기반 at-least-once) |
| 장점 | 상태 변경과 이벤트의 **원자 커밋**(dual-write 해소), 신규 인프라 0 |
| 한계 | 폴링 지연, 컨슈머 멱등 필수 |

---

## 비교 요약

| 기준 | A 동기호출 | B @Async | C 이벤트만 | D 브로커 | **E 이벤트+Outbox** |
|---|---|---|---|---|---|
| 원자성(dual-write 해소) | ✓(결합) | ✗ | ✗(유실) | ✗(브로커+DB) | **✓** |
| 내구성(재시도) | — | ✗ | ✗ | ✓ | **✓** |
| 도메인 결합도 | 높음 | 중 | 낮음 | 낮음 | **낮음** |
| 신규 인프라 | 없음 | 없음 | 없음 | 브로커 | **없음** |

---

## Consequences

**긍정적**
- **dual-write 해소**: 주문 상태 변경과 `OrderPaidEvent` 가 한 트랜잭션에 커밋돼, "결제는 됐는데 배송 트리거 유실"이 구조적으로 불가능.
- 도메인 간 단방향·느슨한 결합 — 결제는 배송을 모르고 아웃박스에 사실만 기록, 배송은 컨슈머로 구독.
- 정합성 정리(장바구니·토큰)는 가벼운 인메모리 이벤트로 처리해 Outbox 오버헤드를 피함(유실 허용 트레이드오프 명시).

**부정적 / 트레이드오프**
- **폴링 지연**: 릴레이 주기(`groove.outbox.relay.interval`, 기본 PT2S)만큼 배송 생성이 지연된다 — 실시간성 요구 낮아 수용.
- **컨슈머 멱등 필수**: at-least-once 라 중복 디스패치 가능 → 모든 핸들러가 멱등이어야 함(`OrderPaidOutboxHandler` 의 중복 흡수가 예시).
- **확장 한계**: 단일 DB 폴링 구조는 멀티 인스턴스/초고처리량에서 병목 → 그 시점에 브로커(Option D) + Outbox 조합으로 확장하고 본 ADR 을 보강.

---

## References

- 코드: [`OutboxEventPublisher.java`](../../backend/src/main/java/com/groove/common/outbox/OutboxEventPublisher.java), [`OutboxRelayScheduler.java`](../../backend/src/main/java/com/groove/common/outbox/OutboxRelayScheduler.java), [`OrderPaidOutboxHandler.java`](../../backend/src/main/java/com/groove/shipping/application/OrderPaidOutboxHandler.java), [`CartCleanupOnMemberWithdrawnListener.java`](../../backend/src/main/java/com/groove/cart/application/CartCleanupOnMemberWithdrawnListener.java)
- [Transactional Outbox Pattern (microservices.io)](https://microservices.io/patterns/data/transactional-outbox.html)
- [Spring `@TransactionalEventListener`](https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html)
