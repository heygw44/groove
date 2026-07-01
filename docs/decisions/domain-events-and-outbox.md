# ADR: 비동기 처리 — 도메인 이벤트 + Outbox (vs 메시지 큐)

| 항목 | 값 |
|---|---|
| 상태 | Accepted |
| 날짜 | 2026-06-17 |
| 연관 이슈 | #257 |
| 작성자 | ParkGunWoo |
| 관련 문서 | [ARCHITECTURE.md](../ARCHITECTURE.md), [ERD.md §outbox_event](../ERD.md) |

---

## Context

도메인 사이에 부수효과를 비동기로 전파해야 하는 흐름이 두 종류 있는데, 성격이 꽤 다르다.

하나는 **도메인 내부의 정합성 정리**다. 회원이 탈퇴하면 그 회원의 장바구니와 리프레시 토큰을 비워야 한다. 그런데 이 정리를 탈퇴 트랜잭션에 강하게 묶어 버리면, 정리가 실패할 때 멀쩡한 탈퇴까지 롤백되는 엉뚱한 상황이 생긴다.

다른 하나는 **도메인 간의 신뢰성 있는 전달**이다. 결제가 `PAID` 되면 배송을 생성해야 한다. 결제 트랜잭션 안에서 배송 도메인을 직접 부르면 둘이 단단히 묶여 버리고, 반대로 트랜잭션 밖에서 부르면 "결제는 커밋됐는데 배송 트리거는 날아간" 유실이 생길 수 있다. 이게 흔히 말하는 이중 쓰기(dual-write) 문제다.

제약은 단순하다. Kafka·RabbitMQ·SQS 같은 외부 브로커가 없고(단일 인스턴스, 단일 MySQL), 그래서 "상태 변경과 이벤트 발행을 어떻게 원자적으로 묶을 것인가"가 이 결정의 핵심이 된다.

---

## Decision

외부 브로커 없이, 성격이 다른 두 흐름을 각각에 맞는 방식으로 처리한다.

### 1) 도메인 내부 정합성 → 인메모리 이벤트 (`@TransactionalEventListener(AFTER_COMMIT)`)

탈퇴 트랜잭션이 **커밋된 다음에** 리스너가 자기만의 `REQUIRES_NEW` 트랜잭션에서 정리한다. 정리가 실패해도 탈퇴는 이미 끝난 일이므로, 예외는 로그로 흡수한다.

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

같은 이벤트를 `auth` 의 리프레시 토큰 정리 리스너도 따로 구독한다. 관심사마다 독립된 리스너로 나뉘어 있어 서로 영향을 주지 않는다.

### 2) 도메인 간 전달 → Outbox 패턴

상태 변경과 이벤트 기록을 **같은 트랜잭션에 함께 커밋**하고, 발행 자체는 별도 스케줄러가 미발행 행을 폴링해서 처리한다(at-least-once).

```java
// payment/application/PaymentCallbackService.java — PAID 처리 시
order.changeStatus(OrderStatus.PAID, null, now);
outboxEventPublisher.publish(OrderPaidEvent.OUTBOX_AGGREGATE_TYPE, order.getId(),
        OrderPaidEvent.OUTBOX_EVENT_TYPE,
        new OrderPaidEvent(order.getId(), order.getOrderNumber(), order.getMemberId(), payment.getId()));
```

발행 진입점은 활성 트랜잭션 밖에서 호출되면 아예 막아 버린다. 상태 변경과 한 묶음으로 커밋되도록 강제하는 장치다.

```java
// common/outbox/OutboxEventPublisher.java
if (!TransactionSynchronizationManager.isActualTransactionActive()) {
    throw new IllegalStateException("아웃박스 발행은 활성 트랜잭션 안에서 호출해야 합니다 (상태 변경과 원자 커밋)");
}
repository.save(OutboxEvent.of(aggregateType, aggregateId, eventType, json));
```

이후 `OutboxRelayScheduler` 가 `published_at IS NULL` 인 행을 id 순으로 묶어 조회하고, 건별로 `OutboxEventHandler` 에 넘긴 뒤 성공하면 `markPublished` 한다(독립 트랜잭션). 한 건이 실패하면 그 건만 격리해 다음 주기에 다시 시도하므로, **컨슈머는 반드시 멱등해야 한다.** 실제로 `OrderPaidOutboxHandler` 는 배송이 이미 있으면 `DataIntegrityViolationException` 을 삼키고 넘어간다.

---

## Considered Options

### Option A — 직접 동기 메서드 호출 ❌

결제 서비스가 같은 트랜잭션에서 배송 서비스를 그냥 호출하는 방식이다. 두 도메인이 강하게 묶이고, 배송 쪽 실패가 결제를 롤백시키며, 트랜잭션이 점점 비대해진다.

### Option B — `@Async` 메서드 ❌

부수효과를 `@Async` 로 떼어 비동기 실행하는 안이다. 문제는 트랜잭션 경계와 무관하게 즉시 돌기 때문에 커밋 전에 발행돼 유실될 수 있고, 재시도나 내구성 보장도 없다. 그래서 프로젝트에서는 아예 쓰지 않는다(`@Async`·`@EnableAsync` 0 건).

### Option C — 인메모리 이벤트만 (Outbox 없음) ⚠️

모든 전파를 `@TransactionalEventListener` 로 처리하는 방식이다. 장바구니·토큰 정리처럼 유실을 감수할 수 있는 자리에는 잘 맞는다. 하지만 AFTER_COMMIT 직후 프로세스가 죽으면 이벤트가 사라지므로, 배송 생성처럼 절대 빠뜨리면 안 되는 흐름에는 부족하다.

### Option D — Kafka/RabbitMQ 외부 브로커 ❌

브로커에 이벤트를 발행하고 컨슈머 그룹이 처리하는 정석이다. 멀티 인스턴스·고처리량·내구 큐가 필요하면 강력하다. 다만 운영·모니터링 부담이 있는 인프라를 새로 들여야 하고, 브로커와 DB 사이의 이중 쓰기 문제는 그대로 남아 결국 Outbox 가 또 필요해진다. 단일 인스턴스에는 과한 선택이다.

### Option E — 인메모리 이벤트 + Outbox ✅ (채택)

유실을 감수할 수 있는 정리는 가벼운 인메모리 이벤트로, 유실되면 안 되는 전달은 DB 기반 at-least-once Outbox 로 나눠 처리한다. 상태 변경과 이벤트가 한 트랜잭션에 커밋되니 이중 쓰기 문제가 풀리고, 새 인프라도 들이지 않는다. 대가는 폴링 지연과 컨슈머 멱등 요구다.

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

이중 쓰기 문제가 깔끔하게 사라진다. 주문 상태 변경과 `OrderPaidEvent` 가 한 트랜잭션에 커밋되므로, "결제는 됐는데 배송 트리거가 유실되는" 상황이 구조적으로 불가능하다. 결제는 배송의 존재를 모른 채 아웃박스에 사실만 기록하고 배송은 컨슈머로 그걸 받아 가니, 두 도메인이 단방향으로 느슨하게 이어진다. 장바구니·토큰 정리처럼 유실을 감수할 수 있는 일은 굳이 Outbox 의 무게를 지지 않고 가벼운 인메모리 이벤트로 처리했다.

**부정적 / 트레이드오프**

폴링 구조라 릴레이 주기(`groove.outbox.relay.interval`, 기본 PT2S)만큼 배송 생성이 늦어진다. 실시간성이 중요한 흐름이 아니라 감수했다. at-least-once 인 만큼 같은 이벤트가 두 번 디스패치될 수 있어, 모든 핸들러가 멱등해야 한다는 제약이 따른다(`OrderPaidOutboxHandler` 의 중복 흡수가 그 예다). 그리고 단일 DB 를 폴링하는 구조는 멀티 인스턴스나 초고처리량에서 병목이 될 수 있는데, 그 단계가 오면 브로커(Option D)와 Outbox 를 함께 쓰는 쪽으로 확장하면 된다.

---

## References

- 코드: [`OutboxEventPublisher.java`](../../backend/src/main/java/com/groove/common/outbox/OutboxEventPublisher.java), [`OutboxRelayScheduler.java`](../../backend/src/main/java/com/groove/common/outbox/OutboxRelayScheduler.java), [`OrderPaidOutboxHandler.java`](../../backend/src/main/java/com/groove/shipping/application/OrderPaidOutboxHandler.java), [`CartCleanupOnMemberWithdrawnListener.java`](../../backend/src/main/java/com/groove/cart/application/CartCleanupOnMemberWithdrawnListener.java)
- [Transactional Outbox Pattern (microservices.io)](https://microservices.io/patterns/data/transactional-outbox.html)
- [Spring `@TransactionalEventListener`](https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html)
