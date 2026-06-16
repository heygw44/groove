# keyset 페이징 커버링 인덱스 — price/releaseYear·status 필터 정렬 경로 보강 (Flyway V25)

> 이슈 [#244](https://github.com/heygw44/groove/issues/244) · 마일스톤 M16 · 도메인 catalog·order
> 본체: [#235](https://github.com/heygw44/groove/issues/235)(커서 keyset 페이징) · 도출: 리뷰 PR [#243](https://github.com/heygw44/groove/pull/243)
> 선행 사례: [주문/리뷰 목록 filesort 제거(V22)](./index-coverage.md) · [검색 풀스캔 제거(V21)](./search-index.md)

## 1. 문제 정의

#235 keyset(커서) 페이징의 핵심은 깊은 페이지에서 정렬 인덱스를 타고 **깊이와 무관한 상수 비용**으로 전진하는 것이다. InnoDB 보조 인덱스가 PK(`id`)를 자동 부착하므로 `id` tiebreaker는 별도 컬럼 없이 커버되고, 기본 정렬 경로는 이미 인덱스를 탄다:

- album 기본/`createdAt`: `idx_album_status_created(status, created_at)` → `(status, created_at, id)` 커버 ✅
- 회원 주문(상태 미지정): `idx_orders_member_created(member_id, created_at)` → `(member_id, created_at, id)` 커버 ✅

하지만 코드 리뷰(#243)에서 **인덱스가 없어 filesort/잉여 스캔이 끼는 두 정렬 경로**가 확인됐다. 정렬 화이트리스트(`AlbumQueryController.ALLOWED_SORT_PROPERTIES = {id, createdAt, price, releaseYear}`)가 `price`/`releaseYear`를 실제 허용하므로 둘 다 호출 가능한 경로다.

| 경로 | 쿼리 형태 | Before 증상 |
|---|---|---|
| album `price` 정렬 스크롤 | `WHERE status='SELLING' ORDER BY price` | 커버 인덱스 부재 → **filesort** |
| album `release_year` 정렬 스크롤 | `WHERE status='SELLING' ORDER BY release_year` | `idx_album_year(release_year)`로 정렬은 받치나 `status`가 **인덱스 밖 residual 필터** → 비-SELLING 행까지 읽고 버림 |
| 회원 주문 `status` 필터 스크롤 | `WHERE member_id=? AND status=? ORDER BY created_at` | `idx_orders_member_created`로 정렬은 받치나 `status`가 **residual 필터** → 매칭 안 되는 행까지 읽음(deep page에서 잉여 스캔) |

`idx_album_search(genre_id, status, price)`는 `genre_id` 선두라 `genreId` 필터 없이는 못 탄다. 공개 스크롤은 `genreId` 없이 **단일 `status` 동등 필터만** 건다(`toPublicCondition`이 미지정 시 SELLING으로 강제, `rejectHiddenStatusFromPublic`이 HIDDEN만 차단 — 즉 기본 SELLING·SOLD_OUT 허용). 따라서 이 인덱스로는 `price` 정렬을 커버할 수 없고, 선두가 `status`인 `(status, price)`가 필요하다(어떤 단일 status 값이든 커버하므로 SOLD_OUT 스크롤도 동일하게 받쳐준다).

## 2. 원인

공개 album 스크롤은 **단일 `status` 동등 필터가 고정**(기본 SELLING, SOLD_OUT 허용, HIDDEN 차단)이고, keyset은 `ORDER BY <정렬키>, id`(KeysetSort가 id tiebreaker를 주정렬 방향에 맞춰 부착) + 커서 술어가 붙는다. 선두가 `status`인 복합 인덱스가 없으면:

- `price` 정렬: `status=SELLING`에 매칭되는 ~46,000행을 모두 읽어 **정렬 버퍼에서 filesort** 후 `LIMIT`을 적용한다 — keyset의 상수 비용 이점이 페이지마다 무너진다.
- `release_year` 정렬: 옵티마이저가 `idx_album_year`를 골라 filesort는 피하지만, `status`가 인덱스 밖이라 **`Using where` residual 필터**로 비-SELLING 행을 읽고 버린다.

회원 주문 `status` 필터도 같은 구조다 — `(member_id, created_at)`는 `member_id` 동등 + `created_at` 정렬만 받치고, `status`는 residual이라 해당 회원의 전체 주문을 정렬 순으로 훑으며 status 불일치 행을 건너뛴다.

## 3. 개선

각 경로에 **동등 필터 컬럼을 선두로, 정렬 컬럼을 후행으로** 둔 복합 인덱스를 추가한다. 선두 동등 조회로 진입한 뒤 인덱스가 이미 정렬 순이므로 옵티마이저가 forward/backward index scan으로 `LIMIT`에서 조기 종료한다 — filesort도, residual 스캔도 사라진다.

**`V25__keyset_covering_indexes.sql`**

```sql
ALTER TABLE album  ADD INDEX idx_album_status_price (status, price),        ALGORITHM=INPLACE, LOCK=NONE;
ALTER TABLE album  ADD INDEX idx_album_status_year  (status, release_year), ALGORITHM=INPLACE, LOCK=NONE;
ALTER TABLE orders ADD INDEX idx_orders_member_status_created (member_id, status, created_at), ALGORITHM=INPLACE, LOCK=NONE;
```

끝에 명시적 `id`를 두지 않는다 — **InnoDB 보조 인덱스가 PK를 자동 부착**하므로 `(status, price)`는 사실상 `(status, price, id)`로 `id` tiebreaker까지 커버한다. 정렬 방향도 무관하다: KeysetSort가 `id`를 주정렬 방향에 맞춰 붙여 정렬 튜플이 항상 단일 방향(예: `price DESC, id DESC`)이라, 오름차 인덱스 1개로 forward/backward index scan이 ASC/DESC를 모두 커버한다(별도 descending 인덱스 불필요).

**근거 (context7 — MySQL 8.0 Reference Manual 확인)**: `WHERE key_part1 = constant ORDER BY key_part2`는 `(key_part1, key_part2)` 인덱스로 정렬을 만족시켜 filesort를 회피한다(`order-by-optimization`: "Use Index when Key Part is Constant for ORDER BY"). InnoDB 보조 인덱스 레코드는 PK 컬럼을 포함하므로(`innodb-index-types`: "each secondary index record includes the primary key columns") 끝에 `id`를 명시하지 않아도 tiebreaker가 커버된다. 온라인 DDL `ALGORITHM=INPLACE, LOCK=NONE`은 보조 인덱스 추가에 적용된다(V11/V16/V21/V22 컨벤션).

## 4. Before / After 측정

측정 환경: 로컬 docker-compose `mysql:8.4`(OrbStack `linux/amd64` 에뮬레이션). 데이터 = `scripts/seed.sh`(album 50,000 · member 81) + `loadtest/seed-order-review-loadtest.sql`(orders 50,000, status 분포 DELIVERED 60%/PAID 20%/PENDING 10%/CANCELLED 10%). album `status` 분포: SELLING 45,989 · SOLD_OUT 2,547 · HIDDEN 1,464. 회원 주문은 `member_id=1`(625건) 기준. Before는 V25 인덱스 3종을 DROP한 토폴로지에서, After는 V25 적용 토폴로지에서 측정했다.

### 4.1 EXPLAIN (쿼리 레벨 — 환경 독립적, 결정적 증거)

각 쿼리는 keyset 첫 페이지 형태 `... ORDER BY <키> DESC, id DESC LIMIT 20`.

**Q1. album `price` 정렬** `WHERE status='SELLING' ORDER BY price DESC, id DESC`

| 지표 | Before | After (V25) |
|---|---|---|
| `type` / `key` | `ref` / `idx_album_status_created` | `ref` / **`idx_album_status_price`** |
| `Extra` | **Using filesort** | **Backward index scan** (filesort 없음) |
| `using_filesort`(JSON) | `true` | **`false`** |

**Q2. album `release_year` 정렬** `WHERE status='SELLING' ORDER BY release_year DESC, id DESC`

| 지표 | Before | After (V25) |
|---|---|---|
| `type` / `key` | `index` / `idx_album_year` | `ref` / **`idx_album_status_year`** |
| `Extra` | **Using where**; Backward index scan (status는 residual) | **Backward index scan** (residual 없음) |

> Q2는 Before에도 filesort는 없다(옵티마이저가 `idx_album_year` 역scan 선택). 개선의 본질은 **`status` residual 필터 제거** — Before는 `release_year` 순으로 인덱스를 훑으며 비-SELLING 행을 읽고 버리는데, deep page일수록 이 잉여 읽기가 누적된다. After는 `status=SELLING`을 인덱스 접근키로 써(ref) SELLING 행만 `release_year` 순으로 짚는다. 효과는 첫 페이지 EXPLAIN보다 **deep page k6(§4.2)에서 또렷**하다.

**Q3. 회원 주문 `status` 필터** `WHERE member_id=1 AND status='PAID' ORDER BY created_at DESC, id DESC`

| 지표 | Before | After (V25) |
|---|---|---|
| `type` / `key` | `ref` / `idx_orders_member_created` | `ref` / **`idx_orders_member_status_created`** |
| `ref` (인덱스 접근키) | `const` (member_id만) | **`const,const`** (member_id + status) |
| `rows` (스캔 추정) | **625** (filtered 38.32%) | **1** |
| `Extra` | **Using where**; Backward index scan (status residual) | **Backward index scan** (residual 없음) |

```
-- After Q3 (FORMAT=JSON 발췌): status 가 residual → 인덱스 접근키로 승격, 잉여 스캔 제거
"key": "idx_orders_member_status_created", "access_type": "ref",
"rows_examined_per_scan": 1, "using_filesort": false, "backward_index_scan": true
-- Before Q3: "key": "idx_orders_member_created", "rows_examined_per_scan": 625(filtered 38%), status 는 Using where
```

**해석**: Q1은 **filesort 제거**(`using_filesort: true → false`), Q2·Q3는 **`status` residual 필터 제거**(인덱스 접근키로 승격, 비매칭 행 읽기 소멸). Q3의 `rows` 추정이 625 → 1로 떨어진 것이 잉여 스캔 제거의 직접 증거다.

### 4.2 k6 deep-page (HTTP end-to-end — 보조 지표)

`loadtest/deep-page.js`로 같은 깊이(≈10,000행, offset page 500)를 offset(`/albums?page=N`)·keyset(`/albums/scroll?cursor=…`)로 번갈아 측정. 30 VU·60s, 정렬키별 `-e SORT=…`. 모든 run `http_req_failed 0%`·checks 100%.

| 정렬키 | 토폴로지 | offset p95 | keyset p95 | keyset 개선 |
|---|---|---|---|---|
| `createdAt` | (V25 무관, 기준선) | 256.6 ms | 153.4 ms | −40.2% |
| `price` | **Before** (인덱스 없음) | 1013.1 ms | **910.8 ms** | −10.1% |
| `price` | **After** (V25) | 292.1 ms | **173.2 ms** | **−40.7%** |
| `releaseYear` | **Before** | 841.1 ms | 454.5 ms | −46.0% |
| `releaseYear` | **After** (V25) | 472.9 ms | **279.2 ms** | −40.9% |

**해석**:
- **`price`가 헤드라인**: 인덱스 없을 때 keyset은 페이지마다 filesort라 깊은 페이지 이점이 −10.1%로 붕괴했는데(keyset p95 910.8 ms), V25로 **keyset p95 910.8 → 173.2 ms(5.3×)**가 되며 `createdAt`과 같은 −40%대 이점을 회복했다.
- **`releaseYear`**: Before도 `idx_album_year` 역scan이라 keyset 우위는 있었지만(−46%), residual 필터 탓에 절대 지연이 컸다. V25로 **keyset p95 454.5 → 279.2 ms** 절대 지연이 줄었다(offset도 841 → 473 ms 동반 개선).
- 절대 수치는 amd64 에뮬레이션 머신 종속이라 **§4.1 EXPLAIN을 1차 증거**로, 본 표는 추세 보조로 읽는다(`index-coverage.md` §6·`search-index.md` §4.2와 동일 입장).

## 5. 트레이드오프 (인덱스 추가 vs 정렬 화이트리스트 축소)

추가 인덱스는 쓰기/저장 비용을 늘린다. 본 변경의 토폴로지 영향:

- **album** `idx_album_status_price`·`idx_album_status_year` = **순증 +2** (FK 인덱스를 흡수하지 않는 순수 추가 — album FK는 artist/genre/label이라 겹치지 않는다). album은 catalog라 쓰기가 드물어(주로 관리자 등록·수정) 추가 B-Tree 유지 비용이 낮다. 저장은 `(status varchar20 + price/release_year + PK id 8B)` × 행수 규모.
- **orders** `idx_orders_member_status_created` = **순증 +1**. `idx_orders_member_created`는 무필터 회원 주문 경로(`member_id` ORDER BY `created_at`)의 정렬 커버에 **여전히 필요**하므로 유지 → 둘 다 존재한다. orders는 **주문마다 INSERT**가 발생해 추가 B-Tree 유지 비용이 album보다 크다. 다만 인덱스 폭이 좁고(`member_id 8B + status varchar30 + created_at 8B`) 주문 쓰기 자체가 결제·재고 등 더 무거운 작업에 묶여 있어 상대 비용은 작다고 판단했다.

**대안 — 정렬 화이트리스트 축소**: `AlbumQueryController.ALLOWED_SORT_PROPERTIES`에서 `price`/`releaseYear`를 빼 scroll에서 제외하면 인덱스 없이도 filesort 경로를 막을 수 있다. 그러나 가격·발매연도 정렬은 음반 카탈로그의 **정당한 UX 기능**(이미 노출됨)이라 기능을 죽이는 선택이 부적절하다고 보고, 저빈도 쓰기 테이블에 한해 **인덱스 추가**를 채택했다.

## 6. 검증

```bash
# 슬라이스 (Docker/Testcontainers 기동 상태) — V25 인덱스 존재 가드
cd backend && ./gradlew test --tests "*AlbumRepositoryTest" --tests "*OrderRepositoryTest"
```

- `AlbumRepositoryTest.searchIndexes_areAdded` — `idx_album_status_price`·`idx_album_status_year` 존재를 `information_schema.STATISTICS`로 코드에 고정(회귀 시 즉시 실패 — V21/V22 가드와 동일 의도).
- `OrderRepositoryTest.listIndexes_areAdded` — `idx_orders_member_status_created` 추가 고정.
- album `price` keyset 정확성은 기존 `AlbumScrollPagingTest`(동일 price 5건 size=2 walk → 누락·중복 없음, 정렬 불일치 커서 400)가, 회원 주문 status 필터 정확성은 `OrderRepositoryTest`의 status 분기 테스트가 이미 커버한다.

EXPLAIN 재현: 본 문서 §4 환경(seed.sh + seed-order-review-loadtest.sql) 적재 후 `EXPLAIN [FORMAT=JSON] SELECT … ORDER BY … LIMIT 20`. k6: `k6 run -e DEEP_ROW=10000 -e SORT=price,desc loadtest/deep-page.js`(정렬키별 `SORT` 교체).

## 7. 주의 / 메모

- **Q2/Q3는 filesort가 아니라 residual 필터가 핵심**: Before에도 `idx_album_year`/`idx_orders_member_created` 역scan으로 정렬은 커버돼 filesort가 없다. 개선의 본질은 `status`를 residual에서 **인덱스 접근키로 승격**해 비매칭 행 읽기를 없앤 것이며, 그 이득은 첫 페이지보다 **deep page에서 누적**된다(§4.2 k6). 유일한 명시적 filesort 제거는 Q1(`price`)이다.
- **`idx_orders_member_created`는 유지**: `(member_id, status, created_at)`는 status가 중간이라 무필터(`member_id` ORDER BY `created_at`) 경로의 `created_at` 정렬을 커버하지 못한다(status 미지정 시 created_at이 정렬 prefix가 안 됨). 그래서 V22 인덱스를 지우지 않고 둘 다 둔다.
- **측정 환경 에뮬레이션**: `mysql:8.4`가 OrbStack amd64(x86 에뮬레이션)로 떠 CPU 바운드다. 절대 시간(p95)은 머신 종속이라 비교의 1차 근거로 쓰지 않고, **환경 독립적인 EXPLAIN(filesort 유무·access_type·residual 유무)**을 결정 증거로 삼는다.
- **체크섬 보존**: V21/V22는 이미 적용된 마이그레이션이라 본문·주석을 수정하지 않는다(Flyway 체크섬은 주석 포함 → 수정 시 validate 실패). 새 인덱스는 V25에만 추가한다.
- **@DataJpaTest 함정**: V20이 member Java 마이그레이션이라 슬라이스가 못 resolve할 수 있으나, `application-test.yaml`에 이미 `flyway.out-of-order: true` + `ignore-migration-patterns: "*:missing"`이 있어 V21~V25 SQL이 정상 동작한다 — V25에 추가 설정은 불필요하다.
