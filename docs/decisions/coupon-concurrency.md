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

선착순 한정수량 쿠폰은 **단일 행(`coupon.issued_count`)** 을 다수 요청이 동시에 증가시키는 전형적 경합 문제다. 정확성 목표는 "정확히 `total_quantity` 만 발급"(초과발급 금지)이고, 비기능 목표는 "스파이크 트래픽에서의 처리량 시연"이다.

제약/선례:
- 현재 인프라에 **Redis·메시지 브로커가 없다** (rate limit=Bucket4j in-memory, cache=Caffeine).
- 환불 경로가 이미 `@Lock(PESSIMISTIC_WRITE)` 를 사용한다 (`PaymentRepository`) — DB 락 패턴 선례 존재.
- 재고 차감은 의도적으로 락 없이 두어 [오버셀 baseline](../troubleshooting/overselling-baseline.md) 을 박제하고, W10 에서 비관적 락으로 개선하는 **Before/After 서사**가 이미 프로젝트에 깔려 있다.
- PRD §11 DoD #4(k6 부하테스트)·#5(Before/After 개선 사례)가 미충족 상태다.

따라서 결정은 단순히 "어떻게 정확히 발급하나"가 아니라 **"학습·시연 가치를 살리면서, 신규 인프라 없이 정확성과 처리량을 어떻게 달성하나"** 다.

## Decision

**DB 단계적(progressive) 전략을 채택한다**: 베이스라인(락 없음) → 비관적 락 → **원자적 조건부 UPDATE(최종)**. 신규 인프라는 도입하지 않는다. Redis 기반은 "극한 트래픽 미래안"으로 ADR 에 기록만 한다.

최종 발급 경로:

```sql
UPDATE coupon
   SET issued_count = issued_count + 1
 WHERE id = :couponId
   AND (total_quantity IS NULL OR issued_count < total_quantity);
-- affected rows == 1 → 슬롯 확보 성공, member_coupon INSERT
-- affected rows == 0 → 소진(COUPON_SOLD_OUT 409)
```

- 회원당 1장은 `UNIQUE(coupon_id, member_id)` 로 DB 보증 (동시/중복 요청도 1장).
- 발급 엔드포인트는 기존 `@Idempotent` 로 재시도 멱등 처리.

## Considered Options

### Option A — 베이스라인 (락 없음) ❌ (Before 시연 전용)

| 항목 | 내용 |
|---|---|
| 방식 | `read issued_count → check < total → insert → increment` |
| 정확성 | **초과발급 발생** (lost-update) |
| 용도 | "Before" 박제용으로만 보존 (`@Disabled` 동시성 테스트). 운영 채택 아님 |

### Option B — 비관적 락 (`SELECT ... FOR UPDATE`) ⚠️ (중간 단계)

| 항목 | 내용 |
|---|---|
| 방식 | `@Lock(PESSIMISTIC_WRITE)` 로 coupon 행 잠금 후 검사·증가 |
| 정확성 | 정확 (직렬화) |
| 처리량 | 행 락을 로직 구간 내내 보유 → 발급이 사실상 직렬화, 처리량 상한 |
| 선례 | `PaymentRepository` 환불 패턴과 동일 — 재사용 용이 |

### Option C — 원자적 조건부 UPDATE ✅ (최종 채택)

| 항목 | 내용 |
|---|---|
| 방식 | 조건과 증가를 단일 `UPDATE ... WHERE issued_count < total_quantity` 로 처리, affected rows 로 판정 |
| 정확성 | 정확 (DB 가 행 단위 원자성 보장) |
| 처리량 | 행 락 보유 구간이 단일 문장으로 짧음 → 비관적 락보다 높은 TPS |
| 인프라 | 신규 도입 없음 |
| 한계 | 단일 행 경합(핫 로우)은 남음 — 극한 트래픽의 천장 |

### Option D — Redis 카운터 (`RAtomicLong`/Lua) + 비동기 영속화 ❌ (미래안)

| 항목 | 내용 |
|---|---|
| 방식 | Redis 원자적 DECR 로 슬롯 선점 → 큐/비동기로 DB `member_coupon` 영속화 |
| 처리량 | 최고 (DB 핫 로우 우회) |
| 비용 | **Redis + 비동기 파이프라인 신규 도입**, 최종 일관성·정합성 보강(Outbox/재처리) 복잡도 |
| 결론 | 단일 인스턴스·시연 규모에 과투자. 멀티 인스턴스/극한 스파이크 도달 시 재검토 |

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
- 신규 인프라 0 으로 정확성 + 준수한 처리량 달성.
- 베이스라인→비관적 락→원자적 UPDATE 3단계가 그대로 **k6 Before/After 측정 자료**(DoD #4·#5)가 된다 — [재고 오버셀 baseline](../troubleshooting/overselling-baseline.md) 과 쌍을 이루는 두 번째 동시성 개선 사례.
- `member_coupon` UNIQUE + `@Idempotent` 로 중복발급 방어선이 이중.

**부정적 / 트레이드오프**
- **핫 로우 천장**: 단일 `issued_count` 행 경합은 원자적 UPDATE 로도 남는다. 단일 인스턴스·시연 규모에선 충분하나, 멀티 인스턴스·초대형 스파이크에서는 Redis 카운터(Option D)로의 전환이 필요 — 그 시점에 본 ADR 을 Superseded 처리.
- **전액 할인(payable=0)**: 결제 도메인은 `amount > 0` 계약(`Payment.initiate`/`PaymentRequest`/DB CHECK)을 유지한다. 따라서 v1 은 할인액이 주문 총액 미만이 되도록 운영하고, payable=0 자동결제(PG 우회 자동 PAID)는 결제 도메인 침습이 커 **후속 과제로 분리**한다. `orders.discount_amount ≤ total_amount` CHECK 로 음수 payable 은 원천 차단.

## References

- [Spring Data JPA — Locking](https://docs.spring.io/spring-data/jpa/reference/jpa/locking.html) (`@Lock`, `LockModeType`)
- [Spring Data JPA — Modifying Queries](https://docs.spring.io/spring-data/jpa/reference/jpa/query-methods.html) (`@Modifying @Query` 원자적 UPDATE)
- [Redisson — RAtomicLong / RLock](https://github.com/redisson/redisson/wiki) (미래안 D 참고)
- 연관: [plans/coupon-system.md](../plans/coupon-system.md), [overselling-baseline.md](../troubleshooting/overselling-baseline.md)
