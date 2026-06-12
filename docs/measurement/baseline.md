# 베이스라인 측정 결과 — G3 게이트 정산 (#196)

> **목적**: W9(측정) 마일스톤에서 박제한 모든 시나리오의 Before 베이스라인을 **한 문서로 통합**하고, 발견 문제를 **데이터 근거 우선순위**로 못박아 **G3 게이트(MILESTONE §2)** 를 통과시킨다. 개선 작업은 하지 않는다(측정 정산만).
> **관련 이슈**: [#192 search](https://github.com/heygw44/groove/issues/192) · [#193 order/payment](https://github.com/heygw44/groove/issues/193) · [#194 flash-sale](https://github.com/heygw44/groove/issues/194) · [#195 N+1·슬로우쿼리](https://github.com/heygw44/groove/issues/195) · [#196 본 정산](https://github.com/heygw44/groove/issues/196)
> **다음 작업**: W10~W11 개선(§4) — GitHub 이슈 발급은 후속 #W9-6 소관.

**측정 환경**: 로컬 1머신(Apple Silicon, Docker MySQL 8.4), 앨범 50,000건 시드(#140). 절대 수치는 머신 종속이며 **추세·정확성**이 핵심이다. HTTP 레벨 수치는 `loadtest/*-summary.json`(#192~#194), 쿼리 레벨 수치는 Testcontainers + Hibernate Statistics(#195)에서 캡처했다.

---

## 1. 통합 베이스라인 표 (HTTP 레벨, 5종)

각 k6 시나리오의 TPS·지연 분포·에러율. 지연은 성공 응답만 집계(order 201 / payment 202). 출처: `loadtest/README.md` 실측표 + `loadtest/*-summary.json`.

| 시나리오 | TPS(req/s) | p50 | p95 | p99 | error rate | 핵심 |
|---|---|---|---|---|---|---|
| **search** (50VU 60s) | ~60 | 674 ms | **930 ms** | 1.00 s | 0% | p95 > SLO 800 ms — N+1·풀스캔 Before |
| **order** (50VU 60s) | 873 | 49 ms | 65 ms | 83 ms | 0% | created 44,723 / rejected 12,844(409·422 정상) |
| **payment** (30VU 60s, Mock지연0) | 405 | 55 ms | 101 ms | 131 ms | 0% | **payment_duplicated=0** (멱등 정상) |
| **flash-sale** (1000VU spike) | — | — | 1.25 s | — | 5.8%(5xx) | **오버셀** — created 211 vs stock 100 |
| **coupon** (250VU spike, 원자적) | ~127 | — | 469~503 ms | — | 0% | 발급 100·초과발급 0 (이미 해결 ✓) |

> search/order/payment 은 SLO·멱등 게이트가 건전(error 0%)하지만 search 만 의도 보존된 N+1·풀스캔으로 p95 가 SLO를 넘는다. flash-sale 은 처리량이 아니라 **정합성(오버셀)** 이 관전 포인트라 TPS/percentile 대신 created↔stock 정합을 본다.

### 1.1 flash-sale 부하 3단계 (오버셀 재현, Before)

재고 100인 단일 한정반에 워밍업 없이 곧장 PEAK VU 스파이크. 출처: `loadtest/flash-sale-summary.json` + README.

| PEAK_VUS | order_created(201) | finalStock | consumed | lost-update | 오버셀 | order p95 | soldout(409) / failed(5xx) |
|---|---|---|---|---|---|---|---|
| 100 | **221** | 0 | 100 | **121** | ✅ created>100 | 264 ms | 8,380 / 959(9.9%) |
| 500 | **222** | 0 | 100 | **122** | ✅ created>100 | 704 ms | 10,726 / 727(6.2%) |
| 1000 | **211** | 0 | 100 | **111** | ✅ created>100 | 1.25 s | 10,834 / 685(5.8%) |

세 레벨 모두 201 성공이 211~222건 = 재고의 ~2.2배 과판매, 최종 재고 0인데 영속 주문 211~222건 → **lost-update 111~122건** 자동 판정. → §3-1.

### 1.2 coupon 동시성 (보너스 — 이미 Before/After 완료, #93)

쿠폰 선착순 발급은 W9 4종 시나리오 밖이지만, 동시성 시연이 한 표에 모이도록 참조로 병기한다. 프로덕션 경로는 **원자적 조건부 UPDATE**(위 표) — Before(락 없음)는 lost-update 24건이었으나 After(원자적 UPDATE)에서 **초과발급 0**으로 수렴했다. 상세: [coupon-issuance-concurrency.md](../troubleshooting/coupon-issuance-concurrency.md).

---

## 2. N+1 · 슬로우 쿼리 수치 (쿼리 레벨)

HTTP 표와 단위가 달라 분리한다(쿼리 수·EXPLAIN). 상세 재현·메커니즘은 §5. 출처: `AlbumQueryN1Test`(#195) + EXPLAIN 캡처.

| 항목 | 측정값 | 의미 |
|---|---|---|
| N+1 — `prepareStatementCount` (5행) | **17** | = 본쿼리 1 + 평점집계 1 + lazy resolve 15. 행 P개로 일반화 시 **1 + 1 + 3P** 선형 증가 |
| N+1 — `entityFetchCount` (= N) | **15** | 5행 × artist/genre/label 3연관의 단건 SELECT |
| 슬로우쿼리 — EXPLAIN `type` | **ALL** (풀 스캔) | `idx_album_title` 부재 + 선행 와일드카드 + `LOWER()` 래핑 |
| 슬로우쿼리 — `rows` examined | **≈49,803** | 50,000건 전체 스캔 |
| 슬로우쿼리 — `query_cost` | 5084.55 / 6827.65 | 단순 `title LIKE` / 실제 search 쿼리 |
| 슬로우쿼리 — 선택도 | 780 / 50,000 = **1.56%** | 1.56% 결과를 위해 전체 스캔(낭비) |

---

## 3. 발견 문제 + 우선순위 (데이터 근거, 4건)

| # | 문제 | 심각도 | 데이터 근거 | 개선 |
|---|---|---|---|---|
| 1 | **오버셀(lost-update)** | **P0 · 정합성** | flash-sale created 211 > stock 100, lost-update 111, finalStock 0 (§1.1) | W10-3 비관적 락 ✅ created→100·lost-update→0 ([#205](../improvements/concurrency.md)) |
| 2 | **N+1 SELECT** | P1 · 성능 | 5행에 17쿼리(entityFetchCount 15), 행수 비례 1+1+3P 증가 (§2) | W10-1 페치조인/`@EntityGraph` |
| 3 | **슬로우쿼리 풀스캔** | P1 · 성능 | search p95 930 ms > SLO 800 ms, EXPLAIN type=ALL 49,803행 (§1·§2) | W10-2 검색 인덱스 |
| 4 | **단일행 경합 5xx** | P2 · 부수효과(1과 연계) | 스파이크 시 5xx 5.8~9.9%(락 타임아웃/데드락) (§1.1) | W10-3 락 도입 시 동반 완화 ✅ 5xx→0% ([#205](../improvements/concurrency.md)) |

**우선순위 논거 — 정합성(P0) > 성능(P1) > 부수효과(P2)**:
- **#1 오버셀**은 재고·금전 데이터 무결성 위반이라 최우선이다. 성능은 느려도 정답을 주지만, 오버셀은 틀린 결과를 영속화한다.
- **#2 N+1·#3 풀스캔**은 search p95(930 ms)가 SLO(800 ms)를 깨는 직접 원인인 성능 결함이다. 둘 다 catalog 도메인이라 W10-1→W10-2로 연속 처리.
- **#4 5xx**는 독립 문제가 아니라 락 없는 단일행 경합의 증상이므로, #1 락 도입 시 동반 해소될 종속 항목으로 둔다.

---

## 4. W10~W11 개선 작업 계획 (확정)

MILESTONE W10/W11 항목을 위 baseline 수치에 매핑해 확정한다(여기서 GitHub 이슈 발급은 안 함 — #W9-6 소관). 각 항목에 **Before 수치 → After 목표**를 명시해 데이터 뒷받침을 가시화한다.

**W10 — CS 개선 1차** (임팩트 큰 3종, Before/After 시연):
- **W10-1** N+1 해결(페치조인/`@EntityGraph`, catalog) — `entityFetchCount 15 → 0`, `prepareStatementCount` 행수 무관 상수화. `AlbumQueryN1Test` 가드(`>5`)가 깨지는 시점이 완료 신호. → `docs/improvements/n-plus-one.md`
- **W10-2** 검색 인덱스(`V6__add_search_indexes.sql`, catalog) — EXPLAIN `type=ALL → range/ref/fulltext`, `rows_examined 49,803 → 결과량 수준`, search p95 `930 ms → < 800 ms`. → `docs/improvements/search-index.md`
- **W10-3** 비관적 락(`SELECT ... FOR UPDATE`, order) — flash-sale `created 211/lost-update 111 → created ≤ 100/lost-update 0`, 5xx 동반 감소. flash-sale.js 재측정(TPS·error·p95). → `docs/improvements/concurrency.md`
- **W10-4** README "성능 개선 사례" 섹션 — 위 3종 Before/After 표·그래프.

**W11 — CS 개선 2차** (정합성 마무리 + 선택 개선):
- **W11-1** 결제 멱등성 통합테스트 — 동시 동일 Idempotency-Key → 단일 결제, 웹훅 중복 → 상태 전이 1회. (payment_duplicated=0 베이스라인을 테스트로 고정)
- **W11-2** 코드 클린업 — TODO/FIXME 정리, 미사용 코드/의존성 제거.
- **W11-3~5**(선택): 단일재고(stock=1) 희귀반 시연 / Redis 분산락 vs 비관적 락 비교 / Virtual Threads 활성화·측정.

---

## 5. 심층: 상품 목록·검색 N+1 + 슬로우 쿼리 (#195)

> **상태**: Before (W9 측정 시점, 페치조인·인덱스 미적용 baseline). W10-1/W10-2 적용 후 동일 시나리오 재실행 → §5.3 캡처 슬롯을 After 로 갱신, §5.6 비교표 채움.

이 절은 상품 목록/검색 경로의 두 결함을 **수치로 박제**한다 — (1) `AlbumService.search()` 의 N+1 SELECT, (2) `title LIKE '%k%'` 검색의 풀 스캔. 둘 다 **의도 보존** 상태이며(개선은 W10), #195는 측정만 한다.

### 5.1 측정 대상 정의

#### N+1 — 목록 응답의 lazy proxy 연쇄

`AlbumService.search(condition, pageable)` 는 다음 순서로 동작한다:

1. `albumRepository.findAll(spec, pageable)` — Specification 동적 조합으로 album 페이지 1건 조회
2. `ratingsByAlbumId(ids)` — 페이지의 album id 묶음으로 평점 **1회 집계**(`review ... WHERE album_id IN (...)`) → N+1 아님
3. `page.map(album -> AlbumSummaryResponse.from(album, rating))` — DTO 변환

문제는 3단계다. `AlbumSummaryResponse.from()` 이 `album.getArtist()`·`getGenre()`·`getLabel()` 을 호출하는데, 세 연관은 모두 `@ManyToOne(fetch = LAZY)` 이고 목록 쿼리에 페치 조인이 없다. 따라서 **행마다 artist/genre/label 각각의 SELECT 가 추가로 발행**된다 — 본 쿼리 1 + (artist + genre + label) per-row 의 N+1.

해당 구조는 W5 단계에서 **의도적**으로 페치 조인 없이 두었다(시연 자료). `AlbumQueryN1Test` 가 "1쿼리가 아니다"를 게이트로 보존하며, 누군가 페치 조인을 추가하면 그 테스트가 즉시 실패한다.

#### 슬로우 쿼리 — title LIKE 풀 스캔

검색 키워드는 `AlbumSpecs.keyword()` 가 `LOWER(album.title) LIKE %k%` OR `LOWER(artist.name) LIKE %k%` (LEFT JOIN, case-insensitive) 로 변환한다. `album.title` 에는 인덱스가 없다 — `V6__init_album.sql` 헤더가 `idx_album_title` 등을 **[W10] 의도적 누락**으로 명시한다(PR 리뷰 시 누락이 아니라 의도, 헤더가 근거).

```
-- [W10] **의도적 누락** — W10 슬로우 쿼리 측정 / 인덱스 튜닝 시연용 (ERD §4.6).
--   - idx_album_title  (title)  — FULLTEXT 검토 후보
```

선행 와일드카드(`%love%`)는 일반 B-Tree 인덱스로도 해소 불가하고, `LOWER()` 함수 래핑까지 겹쳐 인덱스 사용 가능성을 원천 차단한다. 결과는 album 테이블 **풀 스캔**이다.

### 5.2 재현 절차

#### 환경

| 항목 | 값 | 설명 |
|---|---|---|
| JDK | 21+ | |
| 빌드 | Gradle (`./backend/gradlew`) | |
| N+1 측정 DB | Testcontainers `mysql:8.4` | `AlbumQueryN1Test`, 5건 시드 |
| EXPLAIN 측정 DB | docker-compose `mysql:8.4` (`groove-mysql-1`) | `scripts/seed.sh` album 50,000건 |
| Statistics | `hibernate.generate_statistics=true` | 쿼리 수 카운팅 |
| 도커 데몬 | 실행 중일 것 | Testcontainers·compose 부팅 조건 |

> **함정(OrbStack)**: Testcontainers 가 기본 소켓 `/var/run/docker.sock` 을 못 찾으면 컨텍스트 로드가 실패한다(`DockerClientProviderStrategy`). OrbStack 사용 시 `export DOCKER_HOST=unix://$HOME/.orbstack/run/docker.sock` (+ 필요 시 `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`) 를 지정한다.

#### N+1 측정 실행 (Hibernate Statistics)

```bash
./backend/gradlew -p backend test \
  --tests "com.groove.catalog.album.application.AlbumQueryN1Test" -i
```

`AlbumQueryN1Test.search_recordsExactQueryCounts_forBaseline()` 가 `[#195 N+1 baseline]` 태그로 4개 지표를 콘솔에 박제한다(아래 §5.3). 같은 클래스의 `search_triggersN1Selects_byDesign()` 은 "쿼리 수 > 5" 가드를 유지한다.

#### EXPLAIN 측정 실행 (50,000건 풀 스캔)

```bash
# 1) mysql 컨테이너 기동(스키마는 앱 1회 부팅 시 Flyway 가 생성) → album 50,000건 적재
docker compose up -d mysql
DB_PASSWORD=*** ./scripts/seed.sh --docker --yes

# 2) EXPLAIN 캡처 — title LIKE 풀스캔 / 실제 search 쿼리 / count / 교차검증
mq() { docker compose exec -T -e MYSQL_PWD="$DB_PASSWORD" mysql mysql -ugroove groove --table -e "$1"; }
mq "EXPLAIN SELECT * FROM album WHERE title LIKE '%love%';"
mq "EXPLAIN SELECT a.* FROM album a LEFT JOIN artist ar ON a.artist_id=ar.id
    WHERE a.status='SELLING' AND (LOWER(a.title) LIKE '%love%' OR LOWER(ar.name) LIKE '%love%');"
mq "SELECT COUNT(*) FROM album;"
mq "SELECT COUNT(*) FROM album WHERE LOWER(title) LIKE '%love%';"
```

> 시드 생성기가 제목 ~15% 에 `Love`/`Night` 등 공통 토큰을 주입하므로 `%love%` 가 매칭된다.

### 5.3 실측 결과 캡처

#### N+1 수치 (Testcontainers, 5건 SELLING, 2026-06-10)

```
[#195 N+1 baseline] rows=5, prepareStatementCount=17, entityFetchCount=15, queryExecutionCount=2, entityLoadCount=20
```

| 지표 | 값 | 의미 |
|---|---|---|
| `prepareStatementCount` | **17** | 발행된 JDBC PreparedStatement 총수 = 실제 SQL 쿼리 수 |
| `entityFetchCount` | **15** | lazy proxy resolve 로 발생한 추가 fetch = **N** (5행 × artist/genre/label 3연관) |
| `queryExecutionCount` | 2 | album 본 쿼리 1 + 평점 집계 1 (단일 페이지라 count 쿼리 스킵) |
| `entityLoadCount` | 20 | 로드된 엔티티 총수 (album 5 + artist 5 + genre 5 + label 5) |

**분해**: `prepareStatementCount 17` = album 본 쿼리 **1** + 평점 집계(IN절) **1** + lazy resolve **15**. 즉 행 5건을 직렬화하기 위해 **15회의 단건 SELECT** 가 추가로 발생한다(`entityFetchCount=15` 가 N 을 직접 노출). 행 수 P 페이지로 일반화하면 **1 + 1 + 3P** 로 선형 증가한다.

발행된 SQL 발췌(`show_sql`):

```
select ... from album a1_0 where a1_0.status=? limit ?,?                         -- 본 쿼리 1
select r1_0.album_id, avg(r1_0.rating), count(r1_0.id) from review r1_0
       where r1_0.album_id in (?,?,?,?,?) group by r1_0.album_id                  -- 평점 집계 1 (N+1 아님)
select ... from artist a1_0 where a1_0.id=?                                       -- ┐
select ... from genre  g1_0 where g1_0.id=?                                       -- ├ 행마다 3회 × 5행 = 15
select ... from label  l1_0 where l1_0.id=?                                       -- ┘
```

#### EXPLAIN 풀 스캔 캡처 (docker-compose, album 50,000건)

**(1) 단순 `title LIKE '%love%'`** — `idx_album_title` 부재 직접 증명:

```
+----+-------------+-------+------+---------------+------+---------+------+-------+----------+-------------+
| id | select_type | table | type | possible_keys | key  | key_len | ref  | rows  | filtered | Extra       |
+----+-------------+-------+------+---------------+------+---------+------+-------+----------+-------------+
|  1 | SIMPLE      | album | ALL  | NULL          | NULL | NULL    | NULL | 49803 |    11.11 | Using where |
+----+-------------+-------+------+---------------+------+---------+------+-------+----------+-------------+
```

`type=ALL`(풀 스캔), `possible_keys=NULL`(쓸 인덱스 없음), `rows≈49803`, `Using where`. `EXPLAIN FORMAT=JSON` 발췌: `query_cost=5084.55`, `access_type=ALL`, `rows_examined_per_scan=49803`.

**(2) 실제 search 쿼리** (`LOWER LIKE` OR + LEFT JOIN artist + status 필터):

```
+----+-------------+-------+--------+---------------+---------+---------+--------------------+-------+----------+-------------+
| id | select_type | table | type   | possible_keys | key     | key_len | ref                | rows  | filtered | Extra       |
+----+-------------+-------+--------+---------------+---------+---------+--------------------+-------+----------+-------------+
|  1 | SIMPLE      | a     | ALL    | NULL          | NULL    | NULL    | NULL               | 49803 |    10.00 | Using where |
|  1 | SIMPLE      | ar    | eq_ref | PRIMARY       | PRIMARY | 8       | groove.a.artist_id |     1 |   100.00 | Using where |
+----+-------------+-------+--------+---------------+---------+---------+--------------------+-------+----------+-------------+
```

album(`a`) 은 여전히 `type=ALL`(풀 스캔, `rows≈49803`) — `status` 컬럼도 인덱스가 없고 `LOWER()` 래핑으로 인덱스 사용 불가. artist(`ar`) 는 PK `eq_ref` 조인이라 비용이 낮다. `EXPLAIN FORMAT=JSON`: `query_cost=6827.65`, album `access_type=ALL`(49803), artist `eq_ref`(1). 페이징 count 쿼리도 동일하게 album 풀 스캔.

#### SQL 교차검증 — 스캔량 ↔ 결과량

```
+-------------+        +------------+
| total_album |        | match_love |
+-------------+        +------------+
|       50000 |        |        780 |
+-------------+        +------------+
```

`%love%` 매칭은 **780건 / 50,000건 = 1.56%** 에 불과한데도 매 검색이 **5만 행 전체를 스캔**한다(선택도 대비 낭비). title 인덱스(또는 FULLTEXT)가 들어가면 스캔량이 결과량 수준으로 떨어질 여지가 크다.

### 5.4 메커니즘 메모

**N+1**: 목록 쿼리 자체는 album 본 row 만 가져온다. lazy 연관(artist/genre/label)은 프록시로 남아 있다가 DTO 변환(`AlbumSummaryResponse.from`)이 게터를 호출하는 **그 순간** 각각 단건 SELECT 로 풀린다. 평점만 예외인데, `ratingsByAlbumId()` 가 페이지 id 전체를 `IN (...)` 한 번으로 모아 집계하기 때문이다 — 동일한 batch 기법(또는 페치 조인/EntityGraph)을 연관에도 적용하면 N+1 이 사라진다.

**풀 스캔**: `LOWER(title) LIKE '%love%'` 는 두 겹으로 인덱스를 무력화한다 — (a) 선행 와일드카드 `%...` 는 B-Tree 의 정렬 전제를 깨고, (b) `LOWER()` 함수 래핑은 원본 컬럼 인덱스를 못 타게 한다. 게다가 `idx_album_title` 자체가 없으므로(V6 의도적 누락), 옵티마이저에겐 `type=ALL` 외 선택지가 없다.

### 5.5 W10 After 가이드 (예고)

- **W10-1 (N+1)**: 목록 쿼리에 `@EntityGraph` 또는 fetch join 으로 artist/genre/label 을 한 번에 끌어온다 → `entityFetchCount` 가 0 에 수렴하고 `prepareStatementCount` 가 5만에 가까운 행수와 무관하게 상수로 떨어진다. `AlbumQueryN1Test` 의 가드(`> 5`)가 깨지는 시점이 곧 개선 완료 신호.
- **W10-2 (풀 스캔)**: `idx_album_title`(또는 `FULLTEXT(title)`) + 필요 시 `idx_album_search(genre_id, status, price)` 추가 → `type=ALL` → `range`/`ref`/`fulltext`, `rows_examined` 가 결과량 수준으로 감소.

After 자료는 §5.3 캡처 슬롯을 동일 포맷으로 갱신하고 §5.6 비교표를 채운다. HTTP 레벨 지표는 `loadtest/search.js`(#192) 의 p95 와 교차 참조한다(본 절은 쿼리 수·EXPLAIN, loadtest 는 TPS·지연 — 역할 분담).

### 5.6 Before/After 비교 (W10 이후 채움)

| 지표 | Before (W9, 본 문서) | After (W10) |
|---|---|---|
| `search()` `prepareStatementCount` (5행) | 17 | **2** (본쿼리 1 + 평점집계 1, 행수 무관 상수 — #203) |
| `entityFetchCount` (= N) | 15 | **0** (artist/genre/label `@EntityGraph` 동반 페치 — #203) |
| 키워드 EXPLAIN `type` | ALL | **fulltext** (key=`ft_album_keyword`, #204) |
| `rows_examined` (50k 기준) | 49803 | **1** (옵티마이저 추정 · 실제 매칭 878=1.76%, #204) |
| `query_cost` (단순 LIKE / 실제 search) | 5084.55 / 6827.65 | **search 0.35** (#204) |

N+1 행은 #203(`@EntityGraph`) 완료로 채웠다 — 상세 [`docs/improvements/n-plus-one.md`](../improvements/n-plus-one.md). 슬로우쿼리/인덱스 행은 **#204(검색 인덱스, V21) 완료**로 채웠다 — 키워드 `LIKE '%k%'` 풀스캔을 비정규화 `artist_name` + 단일 테이블 `FULLTEXT(title, artist_name)` 로 전환해 `type=ALL → fulltext`(query_cost 6827.65 → 0.35). 상세·k6 결과·환경 주의는 [`docs/improvements/search-index.md`](../improvements/search-index.md).

---

## 6. 주문/리뷰 목록 복합 인덱스 (#225, V22)

V8/V13 의 `[W10]` 의도적 누락 인덱스 3종을 V22 에서 도입한 Before/After 의 raw 캡처. 서사·해석은 [`docs/improvements/index-coverage.md`](../improvements/index-coverage.md), 본 절은 수치·재현 절차.

### 6.1 측정 데이터

`scripts/seed.sh`(album 50,000 · member 81) 위에 측정 전용 `loadtest/seed-order-review-loadtest.sql` 로 orders 50,000 + review 50,000(주문 1건당 리뷰 1건)을 합성한다. 분포 기준은 실제 시드 행에서 읽는다(하드코딩 X): `member_id` 는 USER 회원(기본 80명)에 라운드로빈(회원당 ≈625) · `status` DELIVERED 60%/PAID 20%/PENDING 10%/CANCELLED 10% · `album_id` 는 앞 500개 앨범에 라운드로빈(앨범당 ≈100). 기준 값 `member_id=1`(625) · `status='DELIVERED'`(30,000) · `album_id=1`(100).

### 6.2 EXPLAIN 캡처 (각 쿼리 `... ORDER BY created_at DESC LIMIT 20`)

| # | 쿼리 WHERE | Before type/key/Extra/cost | After (V22) type/key/Extra/cost |
|---|---|---|---|
| Q1 | `orders member_id=1` | `ref` / member_id FK / **filesort** / 218.75 | `ref` / `idx_orders_member_created` / **backward index scan** / 218.75 |
| Q2 | `orders status='DELIVERED'` | **`ALL`** / NULL / Using where; **filesort** / **5105.85** (rows 49,536) | `ref` / `idx_orders_status_created` / **backward index scan** / **2933.55** (rows 24,768) |
| Q3 | `orders member_id=1 AND status='PAID'` | `ref` / member_id FK / Using where; **filesort** / 218.75 | `ref` / `idx_orders_member_created` / Using where; **backward index scan** / 218.75 |
| Q4 | `review album_id=1` | `ref` / album_id FK / **filesort** / 35.00 | `ref` / `idx_review_album_created` / **backward index scan** / 35.00 |

요지: **filesort 4/4 제거**(`using_filesort: true→false`), status 경로는 풀스캔(`ALL`)→`ref` + cost 5105.85→2933.55. member/album 경로의 cost 동일값은 정렬 비용이 `query_cost` 에 포함되지 않기 때문 — 결정적 증거는 filesort 플래그 + `LIMIT` 조기 종료. V22 복합 인덱스는 `member_id`/`album_id` FK 자동 인덱스를 흡수한다(복합 DROP 시 `needed in a foreign key constraint` 거부로 확인).

### 6.3 재현 절차

```bash
# 1) mysql 기동(fresh) — 스키마는 앱 1회 부팅 시 Flyway V1..V22 가 생성. compose 의 mysql 은 3306 미발행이라
#    host gradle bootRun 으로 마이그레이션을 적용하려면 포트 발행 override 가 필요(또는 docker compose up app).
printf 'services:\n  mysql:\n    ports: ["3306:3306"]\n' > /tmp/groove-port-override.yml
docker compose down -v
docker compose -f docker-compose.yml -f /tmp/groove-port-override.yml up -d --wait mysql
./backend/gradlew -p backend bootRun &   # Flyway V1..V22 적용 후(Started GrooveApplication) 종료

# 2) 데이터 적재
DB_PASSWORD=changeme ./scripts/seed.sh --docker --yes
docker compose exec -T -e MYSQL_PWD=changeme mysql mysql -ugroove groove < loadtest/seed-order-review-loadtest.sql

mq() { docker compose exec -T -e MYSQL_PWD=changeme mysql mysql -ugroove groove -e "$1"; }
mq "ANALYZE TABLE orders, review;"

# 3) Before 토폴로지 복원(pre-V22): FK용 단일컬럼 인덱스 추가 후 V22 복합 제거
mq "ALTER TABLE orders ADD INDEX idx_tmp_orders_member (member_id);"
mq "ALTER TABLE orders DROP INDEX idx_orders_member_created, DROP INDEX idx_orders_status_created;"
mq "ALTER TABLE review ADD INDEX idx_tmp_review_album (album_id);"
mq "ALTER TABLE review DROP INDEX idx_review_album_created;"
mq "EXPLAIN SELECT * FROM orders WHERE member_id=1 ORDER BY created_at DESC LIMIT 20\G"
mq "EXPLAIN SELECT * FROM orders WHERE status='DELIVERED' ORDER BY created_at DESC LIMIT 20\G"
mq "EXPLAIN SELECT * FROM orders WHERE member_id=1 AND status='PAID' ORDER BY created_at DESC LIMIT 20\G"
mq "EXPLAIN SELECT * FROM review WHERE album_id=1 ORDER BY created_at DESC LIMIT 20\G"
# (query_cost 는 동일 쿼리에 EXPLAIN FORMAT=JSON)

# 4) After 토폴로지(V22) 복원: 복합 재추가 후 임시 단일컬럼 제거 → EXPLAIN 재캡처
mq "ALTER TABLE orders ADD INDEX idx_orders_member_created (member_id, created_at), ADD INDEX idx_orders_status_created (status, created_at);"
mq "ALTER TABLE orders DROP INDEX idx_tmp_orders_member;"
mq "ALTER TABLE review ADD INDEX idx_review_album_created (album_id, created_at);"
mq "ALTER TABLE review DROP INDEX idx_tmp_review_album;"

# 5) DoD: 앱 정상 부팅(1단계 'Successfully applied 22 migrations ... now at version v22') + 인덱스 3종 확인
mq "SHOW INDEX FROM orders;"; mq "SHOW INDEX FROM review;"
```

> ⚠️ `docker compose down -v` 는 로컬 측정 볼륨을 파괴한다(seed 로 재현 가능). 측정은 깨끗한 baseline 을 위해 fresh 볼륨을 권장한다(V8/V13 은 수정하지 않으므로 체크섬 이슈는 없다).

---

## 참조

- **부하 스크립트·실행 절차·결과 해석**: [`loadtest/README.md`](../../loadtest/README.md) (search/order/payment/flash-sale/coupon)
- **결과 JSON**: `loadtest/search-summary.json` · `order-summary.json` · `payment-summary.json` · `flash-sale-summary.json` · `summary.json`(coupon)
- **오버셀 단위테스트 Before**: [`docs/troubleshooting/overselling-baseline.md`](../troubleshooting/overselling-baseline.md) (#46, `OversellingBaselineTest`)
- **쿠폰 동시성 Before/After**: [`docs/troubleshooting/coupon-issuance-concurrency.md`](../troubleshooting/coupon-issuance-concurrency.md) (#93)
- **W10/W11 개선 항목 원문**: [`docs/MILESTONE.md`](../MILESTONE.md) §W10·§W11
