# Overselling Baseline — 락 없는 재고 차감의 동시성 결함 (#46)

> **상태**: Before (W6 시점, 락 미적용 baseline)
> **다음 작업**: W10-3 비관적 락 적용 후 같은 시나리오 재실행 → After 캡처로 갱신
> **관련 이슈**: [#43 (의도적 락 미적용)](../../README.md#issue-43), [#46 (본 baseline 보존)](../../README.md#issue-46), W10-3 (비관적 락 적용)

---

## 1. 문제 정의

`OrderService.place(memberId, request)` 는 트랜잭션 내부에서 다음 순서로 동작한다:

1. `loadPurchasable(albumId)` — Album 엔티티 로딩 + SELLING 검증
2. `decreaseStock(album, qty)` — 인메모리 stock 검사 + `album.adjustStock(-qty)` 호출
3. JPA dirty checking → 트랜잭션 commit 시점에 `UPDATE album SET stock = ?` flush

이 구간에는 **DB 레벨 락도, 애플리케이션 레벨 동기화도 없다**. MySQL 8.x 의 기본 격리 수준 `REPEATABLE READ` 에서도 두 트랜잭션이 동일 row 의 stock 을 동시에 읽고, 각자 자신의 스냅샷을 기반으로 차감한 값을 last-writer-wins 로 덮어쓰면 lost-update 가 발생한다 — 그 결과가 **오버셀**이다.

해당 구조는 W6 단계에서 **의도적**으로 단순하게 두었다 (#43 참고). 본 문서는 W10-3 락 도입 전후 비교를 위한 Before 자료를 보존한다.

## 2. 재현 절차

### 2.1 환경

- JDK: 21+
- 빌드: Gradle (`./gradlew`)
- DB: Testcontainers MySQL `mysql:8.4` (격리 수준 기본값 `REPEATABLE READ`)
- 테스트 프로파일: `test`
- 도커 데몬 실행 중일 것 (Testcontainers 부팅 조건)

### 2.2 실행

본 baseline 테스트는 클래스 레벨 `@Disabled` 로 일반 빌드에서 제외되어 있다. 시연 시 일시적으로 어노테이션을 주석 처리한 뒤 단일 클래스만 지정해 실행한다:

```bash
# 1) 임시로 @Disabled 비활성화 (클래스 헤더 주석 처리)
#    @Disabled("W10-3 ...") → // @Disabled("W10-3 ...")

# 2) 단일 클래스 실행 (info 로그 캡처)
./gradlew test --tests "com.groove.order.concurrency.OversellingBaselineTest" -i

# 3) 시연 끝난 뒤 @Disabled 복구
```

> 일반 CI 빌드는 `@Disabled` 로 자동 SKIP 되므로 본 테스트가 빌드 수명에 영향을 주지 않는다.

### 2.3 시나리오 파라미터

| 항목 | 값 | 설명 |
|---|---|---|
| 초기 재고 | 100 | `INITIAL_STOCK` |
| 동시 요청 수 | 200 | `CONCURRENT_REQUESTS` |
| 스레드 풀 크기 | 64 | `THREAD_POOL_SIZE` |
| 라인당 수량 | 1 | `OrderItemRequest.quantity` |
| 진입점 | `OrderService.place()` 직접 호출 | HTTP 계층을 거치지 않아 race 만 노출 |
| 게이트 | `CountDownLatch(1)` ready + `CountDownLatch(200)` done | 동시 출발 보장 |

### 2.4 통과 조건 (= 오버셀 증거)

다음 중 **하나라도** 성립하면 baseline 으로 유효하다:

1. `successCount > INITIAL_STOCK` — 재고를 초과한 주문이 영속화됨 (이상적 오버셀 시나리오)
2. `finalStock < 0` — 마지막 write 가 음수 재고로 진입 (도메인 가드 우회)
3. `persistedOrders > actualDecrement` — **영속된 주문 수가 실제 재고 차감량을 초과** (락 없는 lost-update 의 직접 증거)

MySQL InnoDB 의 row-level lock + deadlock detection 으로 인해 본 시나리오에서는 (1)/(2) 보다 **(3) 이 두드러진다**:
deadlock 으로 일부 트랜잭션이 자동 롤백되지만, 살아남은 트랜잭션들 사이에선 lost-update 가 누적되어 "약속한 재고 이상으로 주문이 영속" 되는 결함이 그대로 노출된다.

## 3. 실측 결과 캡처

> 본 절의 수치는 W6 단계 (락 미적용) 에서 동일 시나리오를 두 번 실행한 실측치다. OS 스케줄링·JIT·MySQL deadlock 감지 타이밍에 따라 매 실행 결과는 약간씩 달라지지만, **lost-update 의 발생 자체는 일관되게 재현**된다.

### 3.1 콘솔 로그 (실측 — 2회 실행, 2026-05-09)

**Run 1**

```
[#46 오버셀 baseline] initialStock=100, concurrentRequests=200, threadPool=64, elapsedMs=666
[#46 오버셀 baseline] success=27, insufficient=0, other=173, finalStock=87, actualDecrement=13, persistedOrders=27
```

→ lost-update 14건 (persistedOrders 27 > actualDecrement 13)

**Run 2**

```
[#46 오버셀 baseline] initialStock=100, concurrentRequests=200, threadPool=64, elapsedMs=532
[#46 오버셀 baseline] success=34, insufficient=0, other=166, finalStock=85, actualDecrement=15, persistedOrders=34
```

→ lost-update 19건 (persistedOrders 34 > actualDecrement 15)

두 실행 모두에서:
- `insufficient=0` — 도메인 가드(`InsufficientStockException`) 는 동시성을 막지 못함
- `other` 의 대부분은 `org.springframework.dao.CannotAcquireLockException`
  (MySQL: `Deadlock found when trying to get lock; try restarting transaction`)
- `successCount` 는 100 미만이지만 영속된 주문 수가 실제 차감량을 명확히 초과 → 오버셀 본질이 정확히 노출됨

### 3.2 DB 검증 SQL (정리 전 상태 확인 시)

테스트 종료 후 `@AfterEach` 로 자동 정리되므로, 정리 직전의 상태를 확인하려면 `cleanupAll()` 호출 라인을 일시 주석 처리한 뒤 다음 쿼리를 실행한다:

```sql
SELECT id, title, stock FROM album WHERE title = 'Oversell Baseline';
SELECT COUNT(*) AS persisted_orders FROM orders;
SELECT COUNT(*) AS order_lines     FROM order_item;
```

기대값 (Run 2 기준):

```
stock = 85
persisted_orders = 34
order_lines = 34
```

### 3.3 스크린샷 (선택)

시연 시 위 콘솔 출력을 그대로 캡처하거나 `build/reports/tests/test/index.html` 의 테스트 상세 페이지를 첨부한다:

- `docs/troubleshooting/assets/overselling-baseline-console.png` — 콘솔 로그 캡처 (선택)
- `docs/troubleshooting/assets/overselling-baseline-sql.png` — DB 결과 캡처 (선택)

> assets 디렉토리는 시연 시점에만 생성한다 — 본 commit 단계에서는 텍스트 로그(§3.1)로 충분.

## 4. 메커니즘 메모

`Album.adjustStock(int delta)` 는 도메인 메서드 레벨에서 `next < 0` 을 거절한다. 그러나 이 가드는 호출 트랜잭션의 **인메모리 스냅샷** 기준일 뿐, 다른 트랜잭션이 같은 시점에 본 stock 값과는 독립적이다.

순수 lost-update 시나리오 (이론):

- TX1 load: stock=1
- TX2 load: stock=1 (동일 스냅샷)
- TX1 check `1 < 1`? false → `adjustStock(-1)` → in-memory stock=0
- TX2 check `1 < 1`? false → `adjustStock(-1)` → in-memory stock=0
- TX1 commit → DB stock=0, OrderItem 1건 영속화
- TX2 commit → DB stock=0 (lost-update), OrderItem 1건 영속화 ← **오버셀**

### 실제 MySQL InnoDB + REPEATABLE_READ 에서 관측되는 양상

200 트랜잭션이 같은 album row 의 dirty-checking UPDATE 를 동시에 flush 하면 **InnoDB row lock 대기**가 발생한다. 동시에 album/orders/order_item 여러 테이블에 잠금이 분산되어 잡히면 **deadlock detection** 이 일부 트랜잭션을 자동 롤백시킨다 (`Deadlock found when trying to get lock`).

그 결과:

- 다수의 트랜잭션이 deadlock 으로 실패하고 (`other` 카운터로 누적)
- 살아남은 트랜잭션들은 row lock 대기를 거쳐 **직렬화** 된 것처럼 보이지만,
- `loadPurchasable` (SELECT) 시점은 lock 이전이므로 같은 stock 값을 본 두 트랜잭션 사이에서는 **lost-update 가 그대로 발생**한다.

따라서 시나리오의 시그널은 `successCount > 100` 이 아니라
**`persistedOrders > actualDecrement`** (영속 주문 수가 실제 차감량을 초과) 형태로 나타난다.

W10-3 의 비관적 락(`SELECT ... FOR UPDATE`) 은 **SELECT 시점에 row lock 을 획득**해 다른 TX 의 SELECT 를 대기시키므로, 위 두 결함(deadlock 폭주 + lost-update) 을 모두 제거한다.

## 5. W10-3 After 가이드 (예고)

W10-3 에서는 다음 옵션 중 하나를 적용한다:

- **PESSIMISTIC_WRITE 락**: 재고 차감 직전 `SELECT ... FOR UPDATE`
- **OPTIMISTIC 락**: `@Version` + 충돌 시 재시도

After 자료는 본 문서의 §3 캡처 슬롯을 동일 포맷으로 갱신하고, §6 에 Before/After diff 표를 추가한다.

## 6. Before/After 비교 (W10-3 이후 채움)

| 지표 | Before (W6, 본 문서) | After (W10-3) |
|---|---|---|
| success > stock | 발생 | 미발생 (예상) |
| finalStock < 0 | 발생 가능 | 항상 ≥ 0 (예상) |
| persistedOrders 합 | > 100 | ≤ 100 |
| 평균 처리 시간 (elapsedMs) | TODO | TODO |

---

## 부록: 실행이 막히는 경우

- **컨테이너 부팅 실패**: 도커 데몬 상태 확인 (`docker info`)
- **테스트가 PASS 인데 오버셀이 안 나타남**: 스레드 수/요청 수를 늘리거나 `THREAD_POOL_SIZE` 를 200 으로 끌어올려 동시 실행 강도를 키운다. JIT/스케줄링이 race window 를 좁히는 경우 종종 있음.
- **`@Disabled` 가 안 풀림**: `./gradlew clean test` 로 캐시 무효화 후 재시도.
