# 주문/리뷰 목록 filesort 제거 — 정렬까지 커버하는 복합 인덱스 (Flyway V22)

> 이슈 [#225](https://github.com/heygw44/groove/issues/225) · 마일스톤 M12(W12 문서화) · 도메인 order·review
> Before 베이스라인 맥락: [#196](https://github.com/heygw44/groove/issues/196) · [`docs/measurement/baseline.md`](../measurement/baseline.md) §6
> 선행 사례: [검색 풀스캔 제거(V21)](./search-index.md)

## 1. 문제 정의

V8(orders)·V13(review) 헤더가 `[W10] 슬로우 쿼리 측정 후 추가` 로 약속한 복합 인덱스 3종이 어느 마이그레이션에도 추가되지 않은 채 남아 있었다. 세 핫 경로(회원 주문목록 / 상태별 내 주문 / 앨범 리뷰목록)는 모두 `ORDER BY created_at DESC` 정렬을 `Pageable`(컨트롤러가 `createdAt` 화이트리스트)에 의존하는데, 기존 인덱스가 정렬을 커버하지 못해 **filesort**(또는 status 경로는 **풀스캔**)를 유발했다.

`orders` 5만 / `review` 5만 시드(`loadtest/seed-order-review-loadtest.sql`)에서 박제한 Before:

| 핫 경로 (repository) | type | key | Extra | query_cost |
|---|---|---|---|---|
| `findByMemberId` (`OrderRepository:54`) | `ref` | member_id FK 단일 | **Using filesort** | 218.75 |
| 관리자 status 목록 (`status=DELIVERED`) | **`ALL`** | NULL (인덱스 부재) | Using where; **Using filesort** | 5105.85 |
| `findByMemberIdAndStatus` (`:56`) | `ref` | member_id FK 단일 | Using where; **Using filesort** | 218.75 |
| `findByAlbumId` (`ReviewRepository:31`) | `ref` | album_id FK 단일 | **Using filesort** | 35.00 |

`member_id`/`album_id` 단일 경로가 `ALL` 이 아니라 `ref` 였던 건 FK가 자동 생성한 인덱스(`orders.member_id`, `review.album_id`) 덕분이다 — **필터는 받쳐졌지만 정렬은 받치지 못했다.** status 경로만 인덱스 자체가 없어 풀스캔이었다.

## 2. 원인

세 쿼리 모두 derived query(`@Query` 없음)라 `WHERE <필터>` 뒤에 `ORDER BY created_at DESC LIMIT n` 이 붙는다. B-Tree 단일 인덱스 `(member_id)` 는 `member_id=?` 동등 조회만 정렬해 줄 뿐, 그 결과 안의 `created_at` 순서는 보장하지 않는다. 따라서 옵티마이저는 매칭 행을 모두 읽어 **정렬 버퍼에서 filesort** 한 뒤 `LIMIT` 을 적용한다.

```java
// 예: findByMemberId(memberId, PageRequest.of(0, 20, Sort.by(DESC, "createdAt")))
//   → SELECT * FROM orders WHERE member_id = ? ORDER BY created_at DESC LIMIT 20
//   (member_id 인덱스로 ref 진입은 하지만 created_at 정렬은 인덱스 밖 → filesort)
```

- **member/album 경로**: 매칭 수가 적어도(회원당 ≈625, 앨범당 ≈100) `LIMIT 20` 을 위해 전부 읽고 정렬한다 — O(n log n) 정렬 + 정렬 버퍼.
- **status 경로**: 인덱스 자체가 없어 `type=ALL` 풀스캔(rows≈49,536) + filesort 로, 비용이 한 자릿수 배 더 크다.

## 3. 개선

각 필터 컬럼을 **선두로, `created_at` 을 후행으로** 둔 복합 인덱스를 추가한다. 선두 컬럼 동등 조회로 진입한 뒤 인덱스가 이미 `created_at` 순으로 정렬돼 있으므로, 옵티마이저는 **인덱스를 역방향으로 스캔(backward index scan)** 하며 `LIMIT n` 에서 조기 종료한다 — 정렬 버퍼도, 풀스캔도 사라진다.

**`V22__add_order_review_list_indexes.sql`**

```sql
ALTER TABLE orders ADD INDEX idx_orders_member_created (member_id, created_at), ALGORITHM=INPLACE, LOCK=NONE;
ALTER TABLE orders ADD INDEX idx_orders_status_created (status, created_at),     ALGORITHM=INPLACE, LOCK=NONE;
ALTER TABLE review ADD INDEX idx_review_album_created  (album_id, created_at),   ALGORITHM=INPLACE, LOCK=NONE;
```

**인덱스 개수는 거의 늘지 않는다 — 복합 인덱스가 FK 자동 인덱스를 흡수한다.** `(member_id, created_at)` 는 선두가 `member_id` 라 `fk_orders_member` 의 FK 요건을 그대로 충족한다. 그래서 V22 적용 후 `orders` 의 단일 `member_id` 인덱스는 사라지고(복합으로 대체), `review.album_id` 도 마찬가지다(실측: 복합 인덱스를 지우려 하면 `Cannot drop index ... needed in a foreign key constraint` 로 거부됨 → 복합이 곧 FK 인덱스). 결과적으로:

- `orders`: 순증 **+1**(신규 `idx_orders_status_created`). member 경로는 FK 인덱스를 복합으로 *제자리 업그레이드*.
- `review`: 순증 **0**. album 경로는 FK 인덱스를 복합으로 대체 → **정렬 커버를 공짜로** 얻는다.

**근거 (context7 — MySQL 8.0 Reference Manual 확인)**: 다중 컬럼(복합) 인덱스는 *선두(leftmost) 컬럼 prefix* 에 대한 조회를 가속하며(`mysql-indexes`, `multiple-column-indexes`), `ORDER BY` 가 인덱스 컬럼 순서와 일치하면 옵티마이저가 **forward/backward index scan 으로 filesort 를 회피**한다(`descending-indexes`: `ORDER BY c1 DESC, c2 DESC` 가 인덱스로 처리됨). 온라인 DDL `ALGORITHM=INPLACE, LOCK=NONE` 은 보조 인덱스 추가에 적용된다(V11/V16/V21 컨벤션).

## 4. Before / After 측정

측정 환경: 로컬 docker-compose `mysql:8.4`. 데이터 = `scripts/seed.sh`(album 50,000 · member 81) + `loadtest/seed-order-review-loadtest.sql`(orders 50,000 · review 50,000, 주문 1건당 리뷰 1건). `member_id=1`(625건)·`album_id=1`(100건)·`status='DELIVERED'`(30,000건) 기준. Before 는 V22 의 3 인덱스를 제거하고 FK용 단일 컬럼 인덱스를 복원한 pre-V22 토폴로지에서, After 는 V22 적용(Flyway 실제 경로) 토폴로지에서 측정했다.

### 4.1 EXPLAIN (쿼리 레벨 — 환경 독립적, 결정적 증거)

각 쿼리는 `... ORDER BY created_at DESC LIMIT 20`.

**Q1. 회원 주문 목록** `WHERE member_id=1`

| 지표 | Before | After (V22) |
|---|---|---|
| `type` | `ref` | `ref` |
| `key` | member_id FK 단일 | **`idx_orders_member_created`** |
| `Extra` | **Using filesort** | **Backward index scan** (filesort 없음) |
| `query_cost` | 218.75 | 218.75 |

**Q2. 관리자 상태별 목록** `WHERE status='DELIVERED'`

| 지표 | Before | After (V22) |
|---|---|---|
| `type` | **`ALL`** (풀스캔) | **`ref`** |
| `key` | NULL | **`idx_orders_status_created`** |
| `rows` (스캔 추정) | **49,536** | 24,768 |
| `Extra` | Using where; **Using filesort** | **Backward index scan** |
| `query_cost` | **5105.85** | **2933.55** (≈43%↓) |

**Q3. 상태별 내 주문** `WHERE member_id=1 AND status='PAID'`

| 지표 | Before | After (V22) |
|---|---|---|
| `type` | `ref` | `ref` (key=`idx_orders_member_created`, status 는 residual) |
| `Extra` | Using where; **Using filesort** | Using where; **Backward index scan** |
| `query_cost` | 218.75 | 218.75 |

**Q4. 앨범 리뷰 목록** `WHERE album_id=1`

| 지표 | Before | After (V22) |
|---|---|---|
| `type` | `ref` | `ref` |
| `key` | album_id FK 단일 | **`idx_review_album_created`** |
| `Extra` | **Using filesort** | **Backward index scan** (filesort 없음) |
| `query_cost` | 35.00 | 35.00 |

```
-- After Q2 (FORMAT=JSON 발췌): 풀스캔 + filesort 동시 제거
"key": "idx_orders_status_created", "access_type": "ref",
"backward_index_scan": true, "using_filesort": false, "query_cost": "2933.55"
-- Before Q2: "access_type": "ALL", "using_filesort": true, "query_cost": "5105.85"
```

**해석**:
- **filesort 4/4 제거**: 네 쿼리 모두 `Using filesort` → `Backward index scan`(`using_filesort: true → false`). member/album 경로는 `query_cost` 숫자가 같은데, MySQL 의 `query_cost` 는 ref 진입 비용 위주라 정렬 비용을 따로 더하지 않기 때문이다. 결정적 증거는 `using_filesort` 플래그 전환과 **인덱스 순서 + `LIMIT 20` 조기 종료**(매칭 625/100건을 다 읽고 정렬하던 것 → 인덱스 앞 20건만 읽고 멈춤)다.
- **status 경로는 cost 도 개선**: `type=ALL` 풀스캔이 `ref` 로 바뀌며 `query_cost` 5105.85 → 2933.55, 정렬도 제거 — 유일하게 순증된 인덱스가 가장 큰 이득을 돌려준다.

## 5. 검증

```bash
# 슬라이스 (Docker/Testcontainers 기동 상태)
cd backend && ./gradlew test --tests "*OrderRepositoryTest" --tests "*ReviewRepositoryTest"
```

- `OrderRepositoryTest.listIndexes_areAdded` / `ReviewRepositoryTest.albumReviewIndex_isAdded` — V22 인덱스 3종 존재를 `information_schema.STATISTICS` 로 코드에 고정(회귀 시 즉시 실패 — `AlbumRepositoryTest.searchIndexes_areAdded` 와 동일 의도).
- `OrderRepositoryTest.findByMemberId_returnsMyOrdersSortedByCreatedAtDesc` / `findByMemberIdAndStatus_filtersByStatus` — 정렬·필터 정확성.
- `ReviewRepositoryTest.findByAlbumId_...withMemberFetched` — 정렬 + `@EntityGraph(member)` 동반 페치.

EXPLAIN 재현 절차는 `docs/measurement/baseline.md` §6(쿼리·시드·Before 토폴로지 복원 명령) 참조.

## 6. 주의 / 메모

- **스케줄러 배치는 범위 밖**: `idx_orders_status_created (status, created_at)` 는 status 접두만 제공한다. 배송 reconciliation(`findByStatusAndPaidAtBefore…OrderByPaidAtAsc`)·PII 익명화 스캔(`…UpdatedAtBefore…OrderByUpdatedAtAsc`)은 status 동등 + `paid_at`/`updated_at` 범위·정렬이라 이 인덱스가 **부분만** 커버한다. 저빈도 + `Limit` 바운드 배치라 이번 측정·인덱스 범위에서 제외했다(별도 인덱스는 비용 대비 효익이 낮음).
- **`query_cost` 숫자의 함정**: member/album 경로처럼 이미 `ref` 였던 쿼리는 `query_cost` 가 그대로다. 정렬 제거의 증거는 cost 가 아니라 `using_filesort` 플래그와 `LIMIT` 조기 종료라는 점을 명시한다.
- **측정 환경 에뮬레이션**: docker-compose `mysql:8.4` 는 OrbStack 에서 `platform: linux/amd64`(x86 에뮬레이션)로 떠 CPU 바운드다. 절대 시간(p95 등)은 머신 종속이라 비교하지 않고, **환경 독립적인 EXPLAIN(type·key·filesort 유무)** 만 결정적 증거로 삼는다(`search-index.md` §4.2 와 동일 입장).
- **체크섬**: 해소 사실 기록을 위해 V8/V13 의 `[W10]` 주석 1줄을 "V22 에서 보완" 으로 갱신했다(본문 SQL 미변경). Flyway 체크섬은 주석까지 포함하므로, V8/V13 을 이미 적용한 persistent DB 는 부팅 검증이 실패한다 — CI(fresh Testcontainers)·시드 측정(fresh compose 볼륨)은 무영향이고, 스테일 로컬 볼륨은 `flyway repair` 또는 `docker compose down -v` 후 재기동하면 된다.
