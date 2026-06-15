# Stock Restoration Concurrency — 재고 복원 경로 lost-update와 원자적 가산 UPDATE

> 이슈 [#234](https://github.com/heygw44/groove/issues/234) · 마일스톤 M16(Flow Hardening) · 도메인 catalog·order
> 개선 요약: [`docs/improvements/concurrency.md`](../improvements/concurrency.md) §7
> 자매 사례: place 비관락 [`overselling-baseline.md`](./overselling-baseline.md)(#205) · 쿠폰 원자적 조건부 UPDATE [`coupon-issuance-concurrency.md`](./coupon-issuance-concurrency.md)(#90)

## 1. 문제 정의

주문 생성 `OrderService.place` 의 재고 **차감**은 비관락(`SELECT … FOR UPDATE`, #205)으로 직렬화됐지만,
재고를 **되돌리는** 경로는 모두 락 없는 read-modify-write 였다:

```java
// 개선 전 — 다섯 경로 공통 패턴
for (OrderItem item : order.getItems()) {
    item.getAlbum().adjustStock(item.getQuantity()); // SELECT 스냅샷 → in-memory +qty → 커밋 시 dirty-check UPDATE
}
```

| # | 경로 | 메서드 | delta |
|---|------|--------|-------|
| 1 | 주문 취소 | `OrderService.cancel` | +qty |
| 2 | 관리자 환불 | `AdminOrderService.refund` | +qty |
| 3 | 결제 실패 보상 | `PaymentCallbackService.applyResult`(FAILED) | +qty |
| 4 | 반품 재입고 | `ClaimService.completeRefund` | +qty |
| 5 | admin 단건 조정 | `AlbumService.adjustStock` | ±delta |

`place`(FOR UPDATE)와 복원 경로가 같은 `album.stock` 을 두고 동시에 read-modify-write 하면, 한쪽이 읽은 stale
스냅샷에 자기 증감만 반영해 절대값으로 덮어쓰면서 다른 쪽의 변경이 유실된다(**lost-update**). DB CHECK
(`ck_album_stock_non_negative`) + `Album.adjustStock` 음수 가드로 oversell·음수 재고(안전성 위반)는 0이지만,
place↔복원 간 정합성 창은 남는다. `ARCHITECTURE.md §12 #1` 에 알려진 한계로 박제돼 있었다.

## 2. 재현 절차

`StockRestoreConcurrencyTest`(Testcontainers MySQL 8.4) — 재고 200, 선행 PENDING 주문 50건 시드 후
동시 `place` 50(−1)·`cancel` 50(+1) 인터리브(스레드 64, `ConcurrencyHarness`).

```bash
cd backend
# baseline(@Disabled) 일시 활성화 → 락 없는 RMW 복원 재현
./gradlew test --tests "com.groove.order.concurrency.StockRestoreConcurrencyTest"
```

### 통과 조건 (= lost-update 증거)

baseline `concurrentPlaceAndRmwRestore_baseline_producesLostUpdate` 은 락 없는 RMW(placeWithoutLock + in-memory
복원)로 다음 중 하나가 성립하면 결함으로 판정한다:

```
finalStock != (initialStock − placeSuccess + restoreSuccess)   # lost-update
  OR  other > 0                                                 # 단일 행 경합 롤백(CannotAcquireLock)
```

## 3. 실측 결과 — 인프로세스 (`StockRestoreConcurrencyTest`, 2026-06-15)

| 지표 | Before (RMW 복원, baseline) | After (#234, 원자적 가산 UPDATE) |
|---|---|---|
| place 성공 / 복원 성공 | 12 / 49 | **50 / 50** |
| other (CannotAcquireLock 롤백) | **39** | **0** |
| finalStock vs expected | 207 vs 237 → **lost-update 30** | **150 == 150 (lost-update 0)** |
| 음수 재고 | 없음 | 없음 |

```
[#234 baseline]  placeSuccess=12, restoreSuccess=49, other=39, initialStock=200, finalStock=207, expected=237  → lost-update 30 + 롤백 39
[#234 원자적]    placeSuccess=50, cancelSuccess=50, other=0, stockAfterSeed=150, finalStock=150, expected=150  | elapsedMs=693, tps=144.3, p95Ms=449
```

음수 가드 회귀(AC "음수 재고 가드 회귀 없음") — 재고 1 에 동시 admin `adjustStock(-1)` 2건:

```
[#234 음수가드]  success=1, rejected=1, other=0, finalStock=0
```

FOR-UPDATE 직렬화로 첫 트랜잭션이 1→0, 둘째는 0 을 읽고 `Album.adjustStock` 음수 가드(`IllegalStockAdjustmentException`)로 거절 → 음수 미진입.

## 4. 메커니즘 — 왜 원자적 가산 UPDATE가 lost-update를 없애나

```java
// AlbumRepository.java (#234)
@Modifying(flushAutomatically = true) // clearAutomatically=false (기본)
@Query("UPDATE Album a SET a.stock = a.stock + :delta WHERE a.id = :id")
int restoreStock(@Param("id") Long id, @Param("delta") int delta);
```

1. **상대 증분 + 행 X-락**: InnoDB 가 매칭 행에 배타 락을 걸고 `stock = stock + :delta` 를 적용한다. 절대값이
   아닌 증분이라, 동시 `place`(`SELECT … FOR UPDATE`)·동시 복원이 같은 행 락을 두고 직렬화되며 어느 writer 도
   다른 writer 의 값을 덮어쓰지 않는다. 재시도가 필요 없다(낙관적 락과 대비).
2. **`clearAutomatically` 는 OFF**: 네 복원 경로 모두 `restoreStock` **이후**에도 같은 트랜잭션에서 관리
   엔티티를 변경한다 — `CouponApplicationService.restoreForOrder`(MemberCoupon), `completeRefund` 의
   `order.markReturned`/`claim.markRefunded`. 벌크 UPDATE 후 컨텍스트를 `clear()` 하면 이 엔티티들이 detach 되어
   변경이 커밋 시 dirty-check 되지 않고 **유실**된다. 그래서 clear 를 끈다(=`updateArtistNameByArtistId` 의 OFF
   사유와 대칭).
3. **stale in-memory stock 은 무해**: clear 를 안 하므로 그래프로 로드된 Album 의 in-memory `stock` 은 stale 로
   남지만, 복원 경로는 이후 stock 을 재조회/직렬화하지 않는다(DTO 매퍼 `OrderItemResponse`·`OrderResponse`·
   `ClaimResponse`·`PaymentCallbackResult` 모두 stock 미노출). 핵심 전제: 루프에서 `adjustStock`(in-memory) 호출을
   **제거**해 Album 이 dirty 가 아니어야 dirty-check 가 stale 절대값으로 증분을 덮어쓰지 않는다.
4. **`flushAutomatically` 는 ON**: 보류 dirty 상태(order/payment/claim 상태 전이, 다른 테이블)를 먼저 flush 해
   쓰기 순서를 결정적으로 둔다(#90 쿠폰 선례와 동일, 비용 0).

`completeRefund` 는 claim 을 `findByIdForUpdate`(엔티티 그래프 없음)로 로드하지만, `ClaimItem.orderItem.album` 이
`@ManyToOne(LAZY)` 라 `.getAlbum().getId()` 는 프록시에서 **SELECT 없이 FK 만 반환**한다 — album 본문을 초기화하지
않으므로 오히려 lazy 로드가 줄어든다.

## 5. 트레이드오프 — 비관 vs 낙관 vs 원자적

세 전략의 비교 표와 채택 근거(복원 1~4 = 원자적, admin 조정 = 비관락, 낙관락 미채택 사유)는
[`concurrency.md §7.2`](../improvements/concurrency.md) 참조.

## 6. k6 HTTP 부하 (`loadtest/stock-restore.js`)

place(−1)·cancel(+1) 를 `shared-iterations` 로 같은 앨범에 동시 인터리브하고, handleSummary 가
`expected = stock_after_seed − place_created + cancel_ok` 와 `final_stock` 을 비교해 lost-update 를 자동 판정한다
(After: `lost-update 0`, Before: `lost-update N`). 실행 절차는 [`loadtest/README.md`](../../loadtest/README.md)
"재고 복원 lost-update 부하" 절. 결정론적 증명은 §3 의 JUnit 가드가, HTTP 재현은 본 k6 가 담당한다.

## 7. 회귀 가드

- `StockRestoreConcurrencyTest.concurrentPlaceAndCancel_atomicRestore_noLostUpdate`(**active**) — lost-update 0.
- `StockRestoreConcurrencyTest.concurrentAdminAdjust_singleStock_negativeGuard`(**active**) — 음수 가드.
- `StockRestoreConcurrencyTest.concurrentPlaceAndRmwRestore_baseline_producesLostUpdate`(**`@Disabled`**) — baseline 시연.
- 서비스 단위테스트(`OrderServiceTest`·`AdminOrderServiceTest`·`PaymentCallbackServiceTest`·`ClaimServiceTest`)
  는 복원 단언을 `verify(albumRepository).restoreStock(...)` 상호작용으로 전환 — 복원이 RMW 로 되돌아가면 실패.
