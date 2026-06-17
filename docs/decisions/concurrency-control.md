# ADR: 동시성 제어 전략 (상위)

| 항목 | 값 |
|---|---|
| 상태 | Accepted |
| 날짜 | 2026-06-17 |
| 연관 이슈 | #257 (W12-2 ADR 정리) |
| 작성자 | ParkGunWoo |
| 관련 문서 | [coupon-concurrency.md](./coupon-concurrency.md), [troubleshooting/overselling-baseline.md](../troubleshooting/overselling-baseline.md), [improvements/concurrency.md](../improvements/concurrency.md) |

---

## Context

커머스 백엔드의 핵심 정합성 위험은 **동시 요청이 같은 행을 갱신하는 경합**이다. 본 프로젝트는 세 곳에서 이 문제가 나타난다.

- **선착순 쿠폰 발급** — 단일 카운터(`coupon.issued_count`)에 다수가 쇄도(핫 로우).
- **재고 차감** — 주문 생성 트랜잭션 안에서 `album.stock` 을 검사·차감(lost-update 시 오버셀).
- **결제 콜백** — 웹훅과 폴링이 같은 결제에 동시 도착(중복 적용·중복 보상 위험).

제약/선례:
- 인프라에 **Redis·메시지 브로커가 없다**(rate limit=Bucket4j in-memory, cache=Caffeine). [coupon-concurrency.md](./coupon-concurrency.md) 에서 이미 "신규 인프라 없이 DB 로 해결" 방침을 세웠다.
- 단일 MySQL 인스턴스 + 단일 앱 인스턴스 운영을 전제한다.
- 재고 오버셀은 [overselling-baseline.md](../troubleshooting/overselling-baseline.md) 로 **Before(락 없음) 를 박제**하고 비관적 락으로 개선하는 서사가 이미 깔려 있다.

본 ADR 은 개별 도메인 결정을 통합해 **"상황별로 어떤 DB 동시성 메커니즘을 왜 골랐는가"의 상위 원칙**을 기록한다. 쿠폰 발급의 단계적 의사결정(베이스라인→비관적 락→원자적 UPDATE)은 [coupon-concurrency.md](./coupon-concurrency.md) 에 위임한다.

---

## Decision

**신규 인프라(분산락) 없이, 임계영역의 성격에 따라 DB 메커니즘을 구분 적용한다.**

| 경합 지점 | 메커니즘 | 코드 | 선택 이유 |
|---|---|---|---|
| 쿠폰 발급 (핫 카운터) | **원자적 조건부 UPDATE** | `CouponRepository.incrementIssuedCount` | 소진 검사 + 증가를 단일 문장으로 → 락 보유 구간 최소, 최고 처리량 |
| 쿠폰 사용 (적용) | **비관적 락** `FOR UPDATE` | `MemberCouponRepository.findByIdForUpdate` | 적용은 주문당 1개·복합 검증 → 행 락으로 직렬화 충분 |
| 재고 차감 | **비관적 락** `FOR UPDATE` + id 오름차순 | `AlbumRepository.findByIdForUpdate`, `OrderService` | 검사·차감이 한 트랜잭션 내 복합 로직 → 락 직렬화, 다중 행은 정렬로 데드락 회피 |
| 결제 콜백 | **비관적 락** `FOR UPDATE` | `PaymentRepository.findByPgTransactionIdForUpdate` | 웹훅/폴링 동시 도착 직렬화, 패자는 종착 상태 읽어 흡수 |
| 재고 복원 (보상) | **원자적 가산 UPDATE** | `AlbumRepository.restoreStock` | 비동기 보상 경로 → 검사 없이 `stock = stock + delta`, 락 불필요 |
| 중복 방지 보조선 | **DB UNIQUE + @Idempotent** | `uk_member_coupon`, `pg_transaction_id` | 애플리케이션 락이 새도 DB 제약으로 최종 방어 |

원칙 요약:
- **핫 단일 카운터** → 조건과 변경을 한 문장에 담는 **원자적 조건부 UPDATE**(affected rows 로 성공/소진 판정).

  ```java
  // CouponRepository — 소진 검사와 증가를 한 UPDATE 로
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("UPDATE Coupon c SET c.issuedCount = c.issuedCount + 1 "
       + "WHERE c.id = :id AND c.status = ...ACTIVE "
       + "AND (c.totalQuantity IS NULL OR c.issuedCount < c.totalQuantity)")
  int incrementIssuedCount(@Param("id") Long id);   // 1=성공, 0=소진/비ACTIVE
  ```

- **트랜잭션 내 복합 로직(검사+여러 변경)** → **비관적 락**(`SELECT … FOR UPDATE`). 다중 행은 **id 오름차순으로 락을 잡아 데드락을 회피**한다(`OrderService` 가 `albumId` 오름차순 정렬).
- **비동기 보상 경로** → 검사가 필요 없으므로 **원자적 가산 UPDATE** 로 락 없이 처리(`StockRestorer` 가 `restoreStock` 을 호출).
- **결제 콜백의 동시 도착**은 행 락으로 직렬화하되, 패자는 락 해제 후 종착 상태를 읽어 `IllegalStateException` 대신 `alreadyProcessed` 로 흡수한다(중복 적용·중복 보상 0).

---

## Considered Options

### Option A — 락 없음 ❌ (Before 시연 전용)

| 항목 | 내용 |
|---|---|
| 방식 | `read → check → write` 무방비 |
| 정확성 | **lost-update → 오버셀/초과발급** |
| 용도 | [overselling-baseline.md](../troubleshooting/overselling-baseline.md) Before 박제용 |

### Option B — 낙관적 락(`@Version`) ⚠️

| 항목 | 내용 |
|---|---|
| 방식 | 버전 컬럼으로 충돌 감지 후 재시도 |
| 적합 | 경합이 드문 경우 |
| 단점 | 선착순/핫 로우에선 **충돌·재시도 폭증** → 처리량 급락, 재시도 코드 복잡 |

### Option C — 비관적 락 일괄 적용 ⚠️

| 항목 | 내용 |
|---|---|
| 방식 | 모든 경합 지점을 `FOR UPDATE` 로 통일 |
| 정확성 | 정확(직렬화) |
| 단점 | 핫 카운터(쿠폰 발급)까지 행 락을 로직 구간 내내 보유 → **처리량 상한**, 원자적 UPDATE 대비 손해 |

### Option D — 메커니즘 혼용 ✅ (채택)

| 항목 | 내용 |
|---|---|
| 방식 | 핫 카운터=원자적 UPDATE, 복합 로직=비관적 락, 보상=원자적 가산, 보조선=UNIQUE |
| 정확성 | 정확 (DB 보증) |
| 처리량 | 지점별 최적 (락 보유 구간 최소화) |
| 인프라 | 신규 도입 0 |

### Option E — Redis 분산락 ❌ (미래안)

| 항목 | 내용 |
|---|---|
| 방식 | Redisson `RLock`/`RAtomicLong` 로 슬롯 선점 |
| 적합 | **멀티 인스턴스** 또는 DB 핫 로우가 천장에 닿는 극한 스파이크 |
| 비용 | Redis 신규 도입 + 정합성 보강(만료·재처리) 복잡도 |
| 결론 | 단일 인스턴스·시연 규모엔 과투자. 도달 시 재검토 |

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
- 신규 인프라 0 으로 세 경합 지점(쿠폰·재고·결제) 모두 정확성 확보 + 지점별 최적 처리량.
- 검증된 테스트가 동반된다: `CouponIssuanceConcurrencyTest`(한정 100/동시 300 → 정확히 100), `PaymentCallbackConcurrencyTest`(동시 콜백 → APPLIED 1·나머지 ALREADY_PROCESSED·재고 복원 1회).
- 다중 행 락의 **id 오름차순 정렬**로 주문/복원의 데드락을 구조적으로 차단한다.

**부정적 / 트레이드오프**
- **핫 로우 천장**: 쿠폰 발급의 단일 `issued_count` 행 경합은 원자적 UPDATE 로도 남는다 → 멀티 인스턴스·초대형 스파이크 도달 시 Redis 카운터(Option E)로 전환하고 본 ADR 을 Superseded 처리.
- **비관적 락의 직렬화 비용**: 재고·결제 콜백은 행 락 구간만큼 동시성이 제한된다 — 단일 인스턴스 규모에선 수용 가능.
- **메커니즘 혼용의 인지 비용**: 지점마다 다른 전략이라 신규 코드 추가 시 "어떤 메커니즘을 쓸지" 판단이 필요 → 본 ADR 의 표가 그 판단 기준이다.

---

## References

- 쿠폰 발급 세부 결정: [coupon-concurrency.md](./coupon-concurrency.md)
- 코드: [`CouponRepository.java`](../../backend/src/main/java/com/groove/coupon/domain/CouponRepository.java), [`AlbumRepository.java`](../../backend/src/main/java/com/groove/catalog/album/domain/AlbumRepository.java), [`OrderService.java`](../../backend/src/main/java/com/groove/order/application/OrderService.java), [`PaymentCallbackService.java`](../../backend/src/main/java/com/groove/payment/application/PaymentCallbackService.java)
- Before/After: [overselling-baseline.md](../troubleshooting/overselling-baseline.md), [improvements/concurrency.md](../improvements/concurrency.md)
- [Spring Data JPA — Locking](https://docs.spring.io/spring-data/jpa/reference/jpa/locking.html), [Modifying Queries](https://docs.spring.io/spring-data/jpa/reference/jpa/query-methods.html)
