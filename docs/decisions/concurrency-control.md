# ADR: 동시성 제어 전략 (상위)

| 항목 | 값 |
|---|---|
| 상태 | Accepted |
| 날짜 | 2026-06-17 |
| 연관 이슈 | #257 (ADR 정리) |
| 작성자 | ParkGunWoo |
| 관련 문서 | [coupon-concurrency.md](./coupon-concurrency.md) |

---

## Context

커머스 백엔드에서 정합성을 가장 위협하는 건 여러 요청이 같은 행을 동시에 갱신하는 상황이다. 이 프로젝트에서는 세 군데서 그 문제가 나타난다.

- 선착순 쿠폰 발급: 단일 카운터(`coupon.issued_count`)에 요청이 한꺼번에 몰린다. 전형적인 핫 로우다.
- 재고 차감: 주문 생성 트랜잭션 안에서 `album.stock` 을 읽고 검사한 뒤 차감하는데, 그 사이 다른 요청이 끼어들면 lost-update 로 오버셀이 난다.
- 결제 콜백: 웹훅과 폴링이 같은 결제에 동시에 도착해, 자칫 결과를 두 번 적용하거나 보상을 두 번 돌릴 수 있다.

전제도 분명하다. 인프라에 Redis 도 메시지 브로커도 없다(rate limit 은 Bucket4j 인메모리, 캐시는 Caffeine). [coupon-concurrency.md](./coupon-concurrency.md) 에서 이미 "새 인프라 없이 DB 로 푼다"는 방침을 세웠고, 운영은 단일 MySQL + 단일 앱 인스턴스를 가정한다.

이 문서는 도메인마다 흩어진 결정을 한데 모아 어떤 경합에 어떤 DB 메커니즘을 왜 골랐는지의 상위 원칙을 남긴다. 쿠폰 발급의 세부 과정은 [coupon-concurrency.md](./coupon-concurrency.md) 에 맡긴다.

---

## Decision

분산락 같은 새 인프라 없이, 임계영역의 성격에 맞춰 DB 메커니즘을 골라 쓴다.

| 경합 지점 | 메커니즘 | 코드 | 선택 이유 |
|---|---|---|---|
| 쿠폰 발급 (핫 카운터) | **원자적 조건부 UPDATE** | `CouponRepository.incrementIssuedCount` | 소진 검사 + 증가를 한 문장에 → 락 보유 구간 최소, 처리량 최상 |
| 쿠폰 사용 (적용) | **비관적 락** `FOR UPDATE` | `MemberCouponRepository.findByIdForUpdate` | 주문당 1개·복합 검증 → 행 락 직렬화로 충분 |
| 재고 차감 | **비관적 락** `FOR UPDATE` + id 오름차순 | `AlbumRepository.findByIdForUpdate`, `OrderService` | 검사·차감이 한 트랜잭션 내 복합 로직, 다중 행은 정렬로 데드락 회피 |
| 결제 콜백 | **비관적 락** `FOR UPDATE` | `PaymentRepository.findByPgTransactionIdForUpdate` | 웹훅/폴링 동시 도착 직렬화, 패자는 종착 상태 읽어 흡수 |
| 재고 복원 (보상) | **원자적 가산 UPDATE** | `AlbumRepository.restoreStock` | 비동기 보상이라 검사 불필요, `stock = stock + delta`, 락 없음 |
| 중복 방지 보조선 | **DB UNIQUE + @Idempotent** | `uk_member_coupon`, `pg_transaction_id` | 앱 락이 새도 DB 제약이 최종 방어 |

원칙을 정리하면 이렇다. 핫한 단일 카운터는 조건과 변경을 한 문장에 담는 **원자적 조건부 UPDATE** 로 처리하고, affected rows 로 성공인지 소진인지를 판정한다.

```java
// CouponRepository — 소진 검사와 증가를 한 UPDATE 로
@Modifying(clearAutomatically = true, flushAutomatically = true)
@Query("UPDATE Coupon c SET c.issuedCount = c.issuedCount + 1 "
     + "WHERE c.id = :id AND c.status = ...ACTIVE "
     + "AND (c.totalQuantity IS NULL OR c.issuedCount < c.totalQuantity)")
int incrementIssuedCount(@Param("id") Long id);   // 1=성공, 0=소진/비ACTIVE
```

반면 한 트랜잭션 안에서 검사와 여러 변경이 얽히는 경우엔 **비관적 락**(`SELECT … FOR UPDATE`)으로 직렬화한다. 행이 여러 개면 `OrderService` 가 `albumId` 오름차순으로 락을 잡아 데드락을 구조적으로 막는다. 보상 경로처럼 검사가 필요 없는 자리는 굳이 락을 걸지 않고 **원자적 가산 UPDATE** 로 끝낸다(`StockRestorer` 가 `restoreStock` 을 호출). 결제 콜백의 동시 도착도 행 락으로 직렬화하되, 락을 놓친 쪽은 해제 후 종착 상태를 다시 읽어 `IllegalStateException` 을 던지는 대신 `alreadyProcessed` 로 조용히 흡수한다. 그래서 중복 적용도, 중복 보상도 0 이다.

---

## Considered Options

### Option A — 락 없음 ❌ (도입 전 측정용)

`read → check → write` 를 아무 보호 없이 한다. lost-update 로 오버셀·초과발급이 그대로 난다. 운영에 쓸 수는 없고, 락 도입 전후 비교를 위한 측정 기준으로만 둔다.

### Option B — 낙관적 락(`@Version`) ⚠️

버전 컬럼으로 충돌을 감지하고 재시도하는 방식이라, 경합이 드문 곳에는 잘 맞는다. 그런데 선착순이나 핫 로우에서는 충돌과 재시도가 폭증해 처리량이 급락하고, 재시도 로직 자체가 코드를 복잡하게 만든다.

### Option C — 비관적 락 일괄 적용 ⚠️

모든 경합 지점을 `FOR UPDATE` 하나로 통일하는 안이다. 정확성은 직렬화로 보장되지만, 핫 카운터인 쿠폰 발급까지 로직 구간 내내 행 락을 쥐고 있게 돼 처리량 상한이 생긴다. 원자적 UPDATE 와 비교하면 손해다.

### Option D — 메커니즘 혼용 ✅ (채택)

핫 카운터는 원자적 UPDATE, 복합 로직은 비관적 락, 보상은 원자적 가산, 그 위에 UNIQUE 제약을 보조선으로 깐다. 정확성은 DB 가 보증하고, 지점마다 락 보유 구간을 최소화하니 처리량도 자리에 맞게 나온다. 새 인프라는 전혀 필요 없다.

### Option E — Redis 분산락 ❌ (미래안)

Redisson 의 `RLock`/`RAtomicLong` 으로 슬롯을 선점하는 방식이다. 멀티 인스턴스로 가거나 DB 핫 로우가 천장에 닿는 극한 스파이크라면 답이 될 수 있다. 다만 Redis 도입과 함께 만료·재처리 같은 정합성 보강 복잡도가 따라온다. 단일 인스턴스 규모에는 과투자라, 그 단계에 도달하면 다시 검토한다.

---

## 비교 요약

| 기준 | A 락없음 | B 낙관적 | C 비관적 일괄 | **D 혼용** | E Redis |
|---|---|---|---|---|---|
| 정확성 | ✗ | ✓ | ✓ | **✓** | ✓ |
| 핫 카운터 처리량 | — | 낮음 | 중 | **상** | 최상 |
| 구현 단순성 | 높음 | 낮음 | 높음 | **중** | 낮음 |
| 신규 인프라 | 없음 | 없음 | 없음 | **없음** | Redis |

---

## Consequences

**긍정적**

새 인프라 없이 쿠폰·재고·결제 세 경합 지점 모두에서 정확성을 확보하면서, 지점마다 알맞은 처리량을 낸다. 검증도 테스트로 뒷받침된다. `CouponIssuanceConcurrencyTest` 는 한정 100 장에 동시 300 요청을 던져 정확히 100 장만 나가는지 확인하고, `PaymentCallbackConcurrencyTest` 는 동시 콜백에서 APPLIED 가 1 건이고 나머지는 ALREADY_PROCESSED 이며 재고 복원이 딱 한 번 일어나는지 본다. 다중 행 락을 id 오름차순으로 잡는 규칙 덕분에 주문·복원의 데드락은 애초에 생기지 않는다.

**부정적 / 트레이드오프**

남는 한계는 핫 로우의 천장이다. 쿠폰 발급의 단일 `issued_count` 행 경합은 원자적 UPDATE 로도 완전히 사라지지 않는다. 멀티 인스턴스나 초대형 스파이크에 도달하면 Redis 카운터(Option E)로 옮기고 이 ADR 을 Superseded 처리하게 될 것이다. 비관적 락을 쓰는 재고·결제 콜백은 락 구간만큼 동시성이 제한되지만, 단일 인스턴스 규모에서는 받아들일 만하다. 한 가지 비용은 메커니즘이 지점마다 다르다는 인지 부담인데, 새 코드를 짤 때 "여기엔 뭘 써야 하나"를 매번 판단해야 한다. 그 판단 기준이 바로 위의 표다.

---

## References

- 쿠폰 발급 세부 결정: [coupon-concurrency.md](./coupon-concurrency.md)
- 코드: [`CouponRepository.java`](../../backend/src/main/java/com/groove/coupon/domain/CouponRepository.java), [`AlbumRepository.java`](../../backend/src/main/java/com/groove/catalog/album/domain/AlbumRepository.java), [`OrderService.java`](../../backend/src/main/java/com/groove/order/application/OrderService.java), [`PaymentCallbackService.java`](../../backend/src/main/java/com/groove/payment/application/PaymentCallbackService.java)
- [Spring Data JPA — Locking](https://docs.spring.io/spring-data/jpa/reference/jpa/locking.html), [Modifying Queries](https://docs.spring.io/spring-data/jpa/reference/jpa/query-methods.html)
