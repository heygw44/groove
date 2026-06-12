# 재고 동시성 — 주문 오버셀(lost-update)을 비관적 락으로 제거

> 이슈 [#205](https://github.com/heygw44/groove/issues/205) · 마일스톤 M10(W10 CS 개선 1차) · 도메인 order
> Before 베이스라인: [#196](https://github.com/heygw44/groove/issues/196) · [`docs/measurement/baseline.md`](../measurement/baseline.md) §1.1·§3 · [`docs/troubleshooting/overselling-baseline.md`](../troubleshooting/overselling-baseline.md)(#46)
> 자매 사례(원자적 조건부 UPDATE): [`docs/troubleshooting/coupon-issuance-concurrency.md`](../troubleshooting/coupon-issuance-concurrency.md)(#93)

## 1. 문제 정의

주문 생성 `POST /orders`(`OrderService.place`)의 재고 차감은 **락 없는 read-modify-write** 였다. 동일 한정반에
동시 주문이 몰리면 두 트랜잭션이 같은 stock 스냅샷을 읽고 각자 차감해 last-writer-wins 로 덮어쓰는
**lost-update(오버셀)** 가 발생한다. #196 베이스라인이 P0 정합성 결함으로 박제했다 — flash-sale(재고 100):

| PEAK_VUS | order_created(201) | finalStock | consumed | lost-update | order p95 | 5xx |
|---|---|---|---|---|---|---|
| 100 | **221** | 0 | 100 | **121** | 264 ms | 9.9% |
| 500 | **222** | 0 | 100 | **122** | 704 ms | 6.2% |
| 1000 | **211** | 0 | 100 | **111** | 1.25 s | 5.8% |

재고 100 에 211~222 건이 생성(≈2.2배 과판매), lost-update 111~122 건. 스파이크 시 5xx 5.8~9.9% 는 단일 행
경합의 부수효과(락 타임아웃/데드락)다.

## 2. 원인

`OrderService.place` 트랜잭션 안에서:

1. `loadPurchasable(albumId)` — `albumRepository.findById()` 로 stock SELECT (**락 없음**)
2. `decreaseStock(album, qty)` — 인메모리로 `album.getStock() < qty` 검사 후 `album.adjustStock(-qty)`
3. 트랜잭션 commit 시점에 Hibernate dirty-check 가 `UPDATE album SET stock=?` flush

SELECT(1)과 UPDATE(3) 사이에 락이 없어, 같은 stock 을 읽은 두 트랜잭션이 각자 차감분만 반영하면 한쪽 차감이
유실된다. `Album.adjustStock` 의 `next < 0` 가드는 **호출 트랜잭션의 인메모리 스냅샷** 기준일 뿐이라 동시성을
막지 못한다(메커니즘 상세: [overselling-baseline.md](../troubleshooting/overselling-baseline.md) §4).

```java
// 개선 전 — OrderService.loadPurchasable
Album album = albumRepository.findById(albumId).orElseThrow(AlbumNotFoundException::new);
// ... decreaseStock(album, qty) → album.adjustStock(-qty) → commit 시 dirty-check UPDATE
```

W6 단계에서 이 결함을 **의도적으로 보존**(#43)하고 #196 에서 수치를 박제했다.

## 3. 개선

재고 차감 직전 **비관적 락**(`SELECT ... FOR UPDATE`)으로 album 행 락을 선점해 read-modify-write 구간을
직렬화한다. 쿠폰 도메인의 `CouponRepository.findByIdForUpdate`(#93)와 동일 패턴이다.

```java
// AlbumRepository.java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT a FROM Album a WHERE a.id = :id")
Optional<Album> findByIdForUpdate(@Param("id") Long id);
```

```java
// OrderService.loadPurchasable — 락 경로는 findByIdForUpdate 로 행 락 선점
Album album = albumRepository.findByIdForUpdate(albumId).orElseThrow(AlbumNotFoundException::new);
```

**근거 (Spring Data JPA 공식 패턴 · context7로 확인)**
- 쿼리 메서드에 `@Lock(LockModeType.PESSIMISTIC_WRITE)` 를 붙이면 트리거되는 SELECT 가 `FOR UPDATE` 로
  실행돼 행 락을 잡는다(`spring-data-jpa` 레퍼런스 `jpa/locking.adoc`). 락은 트랜잭션 종료 시 해제된다.
- 락은 재고가 있는 album 행만 잡으면 되므로 artist/genre/label 페치 없이 최소 쿼리로 둔다 — `place` 는 이
  연관들을 응답에 쓰지 않는다(N+1 fetch 는 목록 경로 한정, [n-plus-one.md](./n-plus-one.md)).
- 환경: Spring Boot 4.0 / Java 21 / MySQL 8.4(InnoDB, REPEATABLE READ).

**데드락 회피**: 다중 album 주문은 라인을 `albumId` 오름차순으로 정렬한 뒤 락을 획득한다 — 서로 다른 순서로
여러 행을 `FOR UPDATE` 로 잡아 발생하는 deadlock 을 차단한다(flash-sale 단일 album 엔 무영향, OrderItem 순서는 기능 무관).

**baseline 보존**: 락 미적용 경로는 테스트 전용 `OrderService.placeWithoutLock` 로 보존해(쿠폰의
`issueWithoutLock` 대칭) Before 를 언제든 재현할 수 있게 한다.

## 4. Before / After 측정

### 4.1 인프로세스 (JUnit, `OversellingBaselineTest`)

Testcontainers MySQL 8.4, 재고 100 / 동시 200(스레드 64). `place`(비관적) vs `placeWithoutLock`(baseline) 한
클래스 비교 — 쿠폰 `CouponIssuanceConcurrencyTest` 와 동일 하니스(TPS·p95 수집).

| 지표 | Before (baseline, 락 없음) | After (#205, 비관적 락) |
|---|---|---|
| success (주문 성공) | < 100 (일부 deadlock 롤백) | **100** (= 재고) |
| finalStock | ≥ 0 (lost-update) | **0** (음수 진입 없음) |
| persistedOrders vs actualDecrement | persisted > 차감 (**lost-update**) | **persisted == 차감 (lost-update 0)** |
| other (CannotAcquireLock 등) | 다수 (단일 행 경합 thrash) | **0** (FOR UPDATE 직렬화) |
| 인프로세스 TPS / p95 | — (대부분 락 실패) | ~258 req/s · ~475 ms |

> baseline 실측(#46): `persistedOrders 34 > actualDecrement 15` → lost-update 19 건
> ([overselling-baseline.md](../troubleshooting/overselling-baseline.md) §3.1). After:
> `[#205 비관적] success=100, insufficient=100, other=0, finalStock=0, actualDecrement=100, persistedOrders=100 | elapsedMs=776, tps=257.7, p95Ms=475`.
>
> 단일 재고 경계(#209): 재고 1 / 동시 100 → 정확히 1 성공·오버셀의 극한도 동일 락으로 수렴.
> `[#205 단일재고] success=1, insufficient=99, other=0, finalStock=0, actualDecrement=1, persistedOrders=1 | elapsedMs=183, tps=546.4, p95Ms=155`
> (회귀 가드 `concurrentOrders_singleStockRarity_exactlyOneSucceeds`).

### 4.2 k6 HTTP (`loadtest/flash-sale.js`)

로컬 1머신(Apple Silicon, Docker MySQL 8.4). 동일 스크립트로 PEAK_VUS 100/500/1000 재측정. 절대 수치는
머신 종속이며 **정합성(오버셀 0)** 이 핵심이다.

| PEAK_VUS | order_created(201) | finalStock | consumed | lost-update | 오버셀 | order p95 | soldout(409) / failed(5xx) |
|---|---|---|---|---|---|---|---|
| 100 | **100** | 0 | 100 | **0** | ❌ 없음 | 587 ms¹ | 9,153 / **0 (0%)** |
| 500 | **100** | 0 | 100 | **0** | ❌ 없음 | 345 ms | 10,416 / **0 (0%)** |
| 1000 | **100** | 0 | 100 | **0** | ❌ 없음 | 342 ms | 10,501 / **0 (0%)** |

¹ 100 VU 는 부팅 직후 첫 런(JIT 워밍업)이라 성공 주문 p95 가 높다. 500/1000(워밍 후)은 ~342–345 ms.

**Before → After 요약**

| 지표 (flash-sale, 재고 100) | Before (#196) | After (#205) |
|---|---|---|
| order_created (201) | 211~222 (재고 ~2.2배) | **정확히 100** |
| lost-update | 111~122 | **0** |
| finalStock | 0 (과판매) | 0 (정합) |
| 5xx 비율 (스파이크) | 5.8~9.9% | **0%** |
| order p95 (1000 VU) | 1.25 s | **342 ms** |

오버셀이 사라지고(created 211~222 → 100, lost-update → 0), 단일 행 경합의 5xx(데드락/락 타임아웃)도 동반
소멸(5.8~9.9% → 0%)했다. 비관적 락이 deadlock 폭주 대신 깔끔한 직렬화로 경합을 흡수한 결과다. DB 교차검증:
런당 `order_item` 정확히 100 영속, 최종 stock 0.

## 5. 검증

```bash
# OrbStack/Docker(Testcontainers) 기동 상태에서 (DOCKER_HOST=unix://$HOME/.orbstack/run/docker.sock)
cd backend
./gradlew test --tests "com.groove.order.concurrency.OversellingBaselineTest"
```

`OversellingBaselineTest` 를 오버셀 회귀 가드로 둔다:
- `concurrentOrders_withPessimisticLock_noOversell`(**active**) — `place` 호출, `success == 100` **and**
  `persistedOrders == actualDecrement`(lost-update 0) **and** `finalStock == 0` **and** `other == 0`.
  누군가 `findByIdForUpdate` 락을 제거하면 즉시 실패한다.
- `concurrentOrders_withoutLock_produceOversell`(**`@Disabled`**) — `placeWithoutLock` baseline 시연용,
  일반 CI 빌드에서는 SKIP.

회귀 점검: `com.groove.order.*` + `CouponOrderIntegrationTest` 전체 통과(주문 생성/취소/쿠폰 적용 무회귀).
부하 회귀: `k6 run -e TARGET_ALBUM_ID=<id> -e PEAK_VUS=1000 loadtest/flash-sale.js` → `오버셀 없음`.

## 6. 주의 / 메모

- **락 보유시간 ↔ 처리량 trade-off**: 비관적 락은 성공 주문을 직렬화하므로 경합 행의 성공 경로 지연이
  오른다(인프로세스 p95 ~475 ms). 다만 한정반 1행 경합은 본질적으로 직렬이라(재고 100 이 상한) 이 비용은
  불가피하며, deadlock 폭주(5xx) 제거가 순이득이다. 비경합 주문(서로 다른 album)은 영향이 없다.
- **데드락 정렬**: 다중 album 주문의 락 획득 순서를 `albumId` 오름차순으로 고정해 교차 락 데드락을 예방한다.
- **자매 사례 대비**: 쿠폰(#93)은 글로벌 카운터 1개라 **원자적 조건부 UPDATE**(`issued_count+1 WHERE issued_count<total`)로
  락 보유를 최소화했다. 주문 재고도 동일 기법(`UPDATE album SET stock=stock-:q WHERE id=:id AND stock>=:q`)이
  가능하지만, 본 이슈는 도메인 가드(`adjustStock`)·취소 복원 경로와의 대칭을 유지하려 **비관적 락**을 택했다.
- **알려진 한계 — 잠금은 place↔place 한정**: 본 이슈는 재고 **차감**(order place) 경로에만 비관적 락을 적용했다.
  재고를 **되돌리는** 경로(`OrderService.cancel` 취소 · `AdminOrderService.refund` 환불 · `PaymentCallbackService`
  결제실패 보상 · `AlbumService.adjustStock` admin PATCH)는 여전히 락 없는 last-write-wins 라, place 와 이 경로들
  사이에는 `album.stock` lost-update 창이 남는다(`Album.adjustStock` 음수 가드로 안전성 위반·음수 재고는 없음).
  본 회귀 테스트(`OversellingBaselineTest`)는 place↔place 만 검증하므로 이 한계를 덮지 못한다 — 카탈로그 전반의
  대칭 잠금은 후속 과제로 둔다.
- **낙관적 락(`@Version`) / Redis 분산락 비교**는 범위를 넘어 **#W11-4** 로 이연한다(이슈 #205 작업 내용 c).
