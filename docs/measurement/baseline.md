# 상품 목록·검색 Baseline — N+1 + 슬로우 쿼리(풀 스캔) (#195)

> **상태**: Before (W9 측정 시점, 페치조인·인덱스 미적용 baseline)
> **다음 작업**: W10-1(페치조인/EntityGraph)·W10-2(인덱스 추가) 적용 후 동일 시나리오 재실행 → §3 캡처 슬롯을 After 로 갱신, §6 비교표 채움
> **관련 이슈**: [#195 — 본 baseline 측정](https://github.com/heygw44/groove/issues/195), W10-1/W10-2(개선 — 마일스톤 진입 시 별도 이슈 발급)

이 문서는 상품 목록/검색 경로의 두 결함을 **수치로 박제**한다 — (1) `AlbumService.search()` 의 N+1 SELECT, (2) `title LIKE '%k%'` 검색의 풀 스캔. 둘 다 **의도 보존** 상태이며(개선은 W10), 본 이슈는 측정만 한다.

---

## 1. 측정 대상 정의

### 1.1 N+1 — 목록 응답의 lazy proxy 연쇄

`AlbumService.search(condition, pageable)` 는 다음 순서로 동작한다:

1. `albumRepository.findAll(spec, pageable)` — Specification 동적 조합으로 album 페이지 1건 조회
2. `ratingsByAlbumId(ids)` — 페이지의 album id 묶음으로 평점 **1회 집계**(`review ... WHERE album_id IN (...)`) → N+1 아님
3. `page.map(album -> AlbumSummaryResponse.from(album, rating))` — DTO 변환

문제는 3단계다. `AlbumSummaryResponse.from()` 이 `album.getArtist()`·`getGenre()`·`getLabel()` 을 호출하는데, 세 연관은 모두 `@ManyToOne(fetch = LAZY)` 이고 목록 쿼리에 페치 조인이 없다. 따라서 **행마다 artist/genre/label 각각의 SELECT 가 추가로 발행**된다 — 본 쿼리 1 + (artist + genre + label) per-row 의 N+1.

해당 구조는 W5 단계에서 **의도적**으로 페치 조인 없이 두었다(시연 자료). `AlbumQueryN1Test` 가 "1쿼리가 아니다"를 게이트로 보존하며, 누군가 페치 조인을 추가하면 그 테스트가 즉시 실패한다.

### 1.2 슬로우 쿼리 — title LIKE 풀 스캔

검색 키워드는 `AlbumSpecs.keyword()` 가 `LOWER(album.title) LIKE %k%` OR `LOWER(artist.name) LIKE %k%` (LEFT JOIN, case-insensitive) 로 변환한다. `album.title` 에는 인덱스가 없다 — `V6__init_album.sql` 헤더가 `idx_album_title` 등을 **[W10] 의도적 누락**으로 명시한다(PR 리뷰 시 누락이 아니라 의도, 헤더가 근거).

```
-- [W10] **의도적 누락** — W10 슬로우 쿼리 측정 / 인덱스 튜닝 시연용 (ERD §4.6).
--   - idx_album_title  (title)  — FULLTEXT 검토 후보
```

선행 와일드카드(`%love%`)는 일반 B-Tree 인덱스로도 해소 불가하고, `LOWER()` 함수 래핑까지 겹쳐 인덱스 사용 가능성을 원천 차단한다. 결과는 album 테이블 **풀 스캔**이다.

## 2. 재현 절차

### 2.1 환경

| 항목 | 값 | 설명 |
|---|---|---|
| JDK | 21+ | |
| 빌드 | Gradle (`./backend/gradlew`) | |
| N+1 측정 DB | Testcontainers `mysql:8.4` | `AlbumQueryN1Test`, 5건 시드 |
| EXPLAIN 측정 DB | docker-compose `mysql:8.4` (`groove-mysql-1`) | `scripts/seed.sh` album 50,000건 |
| Statistics | `hibernate.generate_statistics=true` | 쿼리 수 카운팅 |
| 도커 데몬 | 실행 중일 것 | Testcontainers·compose 부팅 조건 |

> **함정(OrbStack)**: Testcontainers 가 기본 소켓 `/var/run/docker.sock` 을 못 찾으면 컨텍스트 로드가 실패한다(`DockerClientProviderStrategy`). OrbStack 사용 시 `export DOCKER_HOST=unix://$HOME/.orbstack/run/docker.sock` (+ 필요 시 `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`) 를 지정한다.

### 2.2 N+1 측정 실행 (Hibernate Statistics)

```bash
./backend/gradlew -p backend test \
  --tests "com.groove.catalog.album.application.AlbumQueryN1Test" -i
```

`AlbumQueryN1Test.search_recordsExactQueryCounts_forBaseline()` 가 `[#195 N+1 baseline]` 태그로 4개 지표를 콘솔에 박제한다(아래 §3.1). 같은 클래스의 `search_triggersN1Selects_byDesign()` 은 "쿼리 수 > 5" 가드를 유지한다.

### 2.3 EXPLAIN 측정 실행 (50,000건 풀 스캔)

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

## 3. 실측 결과 캡처

### 3.1 N+1 수치 (Testcontainers, 5건 SELLING, 2026-06-10)

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

### 3.2 EXPLAIN 풀 스캔 캡처 (docker-compose, album 50,000건)

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

### 3.3 SQL 교차검증 — 스캔량 ↔ 결과량

```
+-------------+        +------------+
| total_album |        | match_love |
+-------------+        +------------+
|       50000 |        |        780 |
+-------------+        +------------+
```

`%love%` 매칭은 **780건 / 50,000건 = 1.56%** 에 불과한데도 매 검색이 **5만 행 전체를 스캔**한다(선택도 대비 낭비). title 인덱스(또는 FULLTEXT)가 들어가면 스캔량이 결과량 수준으로 떨어질 여지가 크다.

## 4. 메커니즘 메모

**N+1**: 목록 쿼리 자체는 album 본 row 만 가져온다. lazy 연관(artist/genre/label)은 프록시로 남아 있다가 DTO 변환(`AlbumSummaryResponse.from`)이 게터를 호출하는 **그 순간** 각각 단건 SELECT 로 풀린다. 평점만 예외인데, `ratingsByAlbumId()` 가 페이지 id 전체를 `IN (...)` 한 번으로 모아 집계하기 때문이다 — 동일한 batch 기법(또는 페치 조인/EntityGraph)을 연관에도 적용하면 N+1 이 사라진다.

**풀 스캔**: `LOWER(title) LIKE '%love%'` 는 두 겹으로 인덱스를 무력화한다 — (a) 선행 와일드카드 `%...` 는 B-Tree 의 정렬 전제를 깨고, (b) `LOWER()` 함수 래핑은 원본 컬럼 인덱스를 못 타게 한다. 게다가 `idx_album_title` 자체가 없으므로(V6 의도적 누락), 옵티마이저에겐 `type=ALL` 외 선택지가 없다.

## 5. W10 After 가이드 (예고)

- **W10-1 (N+1)**: 목록 쿼리에 `@EntityGraph` 또는 fetch join 으로 artist/genre/label 을 한 번에 끌어온다 → `entityFetchCount` 가 0 에 수렴하고 `prepareStatementCount` 가 5만에 가까운 행수와 무관하게 상수로 떨어진다. `AlbumQueryN1Test` 의 가드(`> 5`)가 깨지는 시점이 곧 개선 완료 신호.
- **W10-2 (풀 스캔)**: `idx_album_title`(또는 `FULLTEXT(title)`) + 필요 시 `idx_album_search(genre_id, status, price)` 추가 → `type=ALL` → `range`/`ref`/`fulltext`, `rows_examined` 가 결과량 수준으로 감소.

After 자료는 §3 캡처 슬롯을 동일 포맷으로 갱신하고 §6 비교표를 채운다. HTTP 레벨 지표는 `loadtest/search.js`(#192) 의 p95 와 교차 참조한다(본 문서는 쿼리 수·EXPLAIN, loadtest 는 TPS·지연 — 역할 분담).

## 6. Before/After 비교 (W10 이후 채움)

| 지표 | Before (W9, 본 문서) | After (W10) |
|---|---|---|
| `search()` `prepareStatementCount` (5행) | 17 | 대폭 감소(예상, 상수화) |
| `entityFetchCount` (= N) | 15 | 0 (예상) |
| `title LIKE` EXPLAIN `type` | ALL | range/ref/fulltext (예상) |
| `rows_examined` (50k 기준) | 49803 | ≈ 결과량 수준 (TODO) |
| `query_cost` (단순 LIKE / search) | 5084.55 / 6827.65 | TODO |
