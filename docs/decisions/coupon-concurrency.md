# ADR: 선착순 쿠폰 발급 동시성 제어 전략

| 항목 | 값 |
|---|---|
| 상태 | Accepted (구현 전 결정) |
| 날짜 | 2026-05-26 |
| 연관 이슈 | 쿠폰 확장 P2 (선착순 발급) — 도입 시 이슈 번호 기재 |
| 후속 작업 | 쿠폰 시스템 P2 구현, P5 k6 측정 |
| 관련 문서 | [plans/coupon-system.md](../plans/coupon-system.md), [ERD.md §4.15](../ERD.md), [troubleshooting/coupon-issuance-concurrency.md](../troubleshooting/coupon-issuance-concurrency.md), [troubleshooting/overselling-baseline.md](../troubleshooting/overselling-baseline.md) |

---

## Context

선착순 한정수량 쿠폰은 단일 행(`coupon.issued_count`)을 여러 요청이 동시에 증가시키는, 동시성 경합의 교과서 같은 문제다. 정확성 목표는 "정확히 `total_quantity` 만 발급"(초과발급 금지)이고, 비기능 목표는 스파이크 트래픽에서의 처리량을 시연하는 것이다.

여기엔 몇 가지 제약과 선례가 깔려 있다. 우선 현재 인프라에 Redis 도 메시지 브로커도 없다(rate limit 은 Bucket4j 인메모리, 캐시는 Caffeine). 환불 경로(`PaymentRepository`)가 이미 `@Lock(PESSIMISTIC_WRITE)` 를 쓰고 있어 DB 락 패턴의 선례는 있다. 재고 차감은 일부러 락 없이 두어 [오버셀 baseline](../troubleshooting/overselling-baseline.md) 을 박제했고, W10 에서 비관적 락으로 개선하는 Before/After 서사가 이미 프로젝트에 깔려 있다. PRD §11 의 DoD #4(k6 부하테스트)와 #5(Before/After 개선 사례)도 아직 미충족이다.

그래서 이 결정은 단순히 "어떻게 정확히 발급하나"가 아니다. **학습·시연 가치를 살리면서, 새 인프라 없이 정확성과 처리량을 어떻게 동시에 달성하나**가 진짜 질문이다.

## Decision

DB 안에서 단계적(progressive)으로 끌고 가는 전략을 택한다. 베이스라인(락 없음)에서 시작해 비관적 락을 거쳐 **원자적 조건부 UPDATE** 를 최종안으로 삼는다. 새 인프라는 도입하지 않고, Redis 기반은 "극한 트래픽을 위한 미래안"으로 이 문서에 기록만 해 둔다.

최종 발급 경로는 이렇다.

```sql
UPDATE coupon
   SET issued_count = issued_count + 1
 WHERE id = :couponId
   AND (total_quantity IS NULL OR issued_count < total_quantity);
-- affected rows == 1 → 슬롯 확보 성공, member_coupon INSERT
-- affected rows == 0 → 소진(COUPON_SOLD_OUT 409)
```

회원당 1 장 제한은 `UNIQUE(coupon_id, member_id)` 로 DB 가 보증하므로, 동시·중복 요청이 와도 한 장을 넘지 않는다. 발급 엔드포인트는 기존 `@Idempotent` 로 재시도까지 멱등하게 처리한다.

## Considered Options

### Option A — 베이스라인 (락 없음) ❌ (Before 시연 전용)

`read issued_count → check < total → insert → increment` 를 보호 없이 수행한다. lost-update 로 초과발급이 난다. 운영에 쓰려는 게 아니라, `@Disabled` 동시성 테스트로 "Before" 를 박제하는 용도다.

| 항목 | 내용 |
|---|---|
| 방식 | `read → check → insert → increment` |
| 정확성 | **초과발급 발생** (lost-update) |
| 용도 | Before 박제용 (운영 채택 아님) |

### Option B — 비관적 락 (`SELECT ... FOR UPDATE`) ⚠️ (중간 단계)

`@Lock(PESSIMISTIC_WRITE)` 로 coupon 행을 잠그고 검사·증가한다. 직렬화되니 정확하고, `PaymentRepository` 환불 패턴과 같아 재사용도 쉽다. 다만 행 락을 로직 구간 내내 쥐고 있어 발급이 사실상 직렬화되고, 그만큼 처리량에 상한이 생긴다.

| 항목 | 내용 |
|---|---|
| 방식 | coupon 행 잠금 후 검사·증가 |
| 정확성 | 정확 (직렬화) |
| 처리량 | 락 보유 구간이 길어 처리량 상한 |
| 선례 | `PaymentRepository` 환불 패턴과 동일 |

### Option C — 원자적 조건부 UPDATE ✅ (최종 채택)

조건과 증가를 단일 `UPDATE ... WHERE issued_count < total_quantity` 로 처리하고 affected rows 로 성공/소진을 판정한다. DB 가 행 단위 원자성을 보장하니 정확하고, 락 보유 구간이 한 문장으로 짧아 비관적 락보다 처리량이 높다. 새 인프라도 필요 없다. 남는 한계는 단일 행 경합(핫 로우)인데, 이게 극한 트래픽에서의 천장이 된다.

| 항목 | 내용 |
|---|---|
| 방식 | 조건+증가를 단일 UPDATE, affected rows 로 판정 |
| 정확성 | 정확 (DB 행 단위 원자성) |
| 처리량 | 락 구간이 단문이라 비관적 락보다 높은 TPS |
| 한계 | 단일 행 경합(핫 로우)은 남음 |

### Option D — Redis 카운터 (`RAtomicLong`/Lua) + 비동기 영속화 ❌ (미래안)

Redis 의 원자적 DECR 로 슬롯을 선점하고, 큐나 비동기로 `member_coupon` 을 DB 에 영속화하는 방식이다. DB 핫 로우를 우회하니 처리량은 최고다. 그러나 Redis 와 비동기 파이프라인을 새로 들여야 하고, 최종 일관성·정합성 보강(Outbox·재처리)의 복잡도가 따라온다. 단일 인스턴스·시연 규모에는 과투자라, 멀티 인스턴스나 극한 스파이크에 도달하면 그때 다시 본다.

| 항목 | 내용 |
|---|---|
| 방식 | Redis DECR 로 슬롯 선점 → 비동기 DB 영속화 |
| 처리량 | 최고 (DB 핫 로우 우회) |
| 비용 | Redis + 비동기 파이프라인 신규 도입, 정합성 보강 복잡도 |

## 비교 요약

| 기준 | A 베이스라인 | B 비관적 락 | **C 원자적 UPDATE** | D Redis |
|---|---|---|---|---|
| 정확성(초과발급 방지) | ✗ | ✓ | **✓** | ✓ |
| 처리량 | — | 낮음 | **중상** | 높음 |
| 신규 인프라 | 없음 | 없음 | **없음** | Redis+큐 |
| 기존 패턴 재사용 | — | PaymentRepository | **PaymentRepository(비교)** | — |
| 시연/학습 가치 | Before | 중간 단계 | **최종(After)** | 미래안 |

## Consequences

**긍정적**
- 새 인프라 없이 정확성과 준수한 처리량을 함께 얻는다.
- 베이스라인→비관적 락→원자적 UPDATE 의 3 단계가 그대로 k6 Before/After 측정 자료(DoD #4·#5)가 된다 — [재고 오버셀 baseline](../troubleshooting/overselling-baseline.md) 과 짝을 이루는 두 번째 동시성 개선 사례다.
- `member_coupon` UNIQUE 와 `@Idempotent` 로 중복발급 방어선이 이중으로 깔린다.

**부정적 / 트레이드오프**
- **핫 로우 천장**: 단일 `issued_count` 행 경합은 원자적 UPDATE 로도 남는다. 단일 인스턴스·시연 규모에선 충분하지만, 멀티 인스턴스·초대형 스파이크에서는 Redis 카운터(Option D)로 전환해야 하고, 그 시점에 이 ADR 을 Superseded 처리한다.
- **전액 할인(payable=0)**: 결제 도메인은 `amount > 0` 계약(`Payment.initiate`/`PaymentRequest`/DB CHECK)을 유지한다. 그래서 v1 은 할인액이 주문 총액보다 작도록 운영하고, payable=0 자동결제(PG 우회 자동 PAID)는 결제 도메인 침습이 커 후속 과제로 분리한다. `orders.discount_amount ≤ total_amount` CHECK 로 음수 payable 은 원천 차단한다.

## References

- [Spring Data JPA — Locking](https://docs.spring.io/spring-data/jpa/reference/jpa/locking.html) (`@Lock`, `LockModeType`)
- [Spring Data JPA — Modifying Queries](https://docs.spring.io/spring-data/jpa/reference/jpa/query-methods.html) (`@Modifying @Query` 원자적 UPDATE)
- [Redisson — RAtomicLong / RLock](https://github.com/redisson/redisson/wiki) (미래안 D 참고)
- 연관: [plans/coupon-system.md](../plans/coupon-system.md), [overselling-baseline.md](../troubleshooting/overselling-baseline.md)
