# 검색 풀스캔 제거 — 키워드 FULLTEXT + 필터/정렬 복합 인덱스 (Flyway V21)

> 이슈 [#204](https://github.com/heygw44/groove/issues/204) · 마일스톤 M10(W10 CS 개선 1차) · 도메인 catalog
> Before 베이스라인: [#196](https://github.com/heygw44/groove/issues/196) · [`docs/measurement/baseline.md`](../measurement/baseline.md) §1·§2·§5
> 선행: [#203 N+1 제거](./n-plus-one.md)

## 1. 문제 정의

공개 검색 `GET /albums`(`AlbumService.search` → `AlbumSpecs.keyword`)의 키워드 경로가 album 50,000건을 **매번 풀스캔**한다. #196에서 박제한 Before:

| 지표 | Before (W9) |
|---|---|
| EXPLAIN `type` | `ALL` (풀 스캔) |
| `rows` examined (50k) | ≈49,803 |
| `query_cost` (단순 LIKE / 실제 search) | 5084.55 / 6827.65 |
| 선택도(`%love%`) | 780 / 50,000 = 1.56% |
| **search p95 (k6)** | **930 ms (> SLO 800 ms)** |

검색 트래픽은 k6 `search.js`가 6분기(keyword/genre/price/year/format/limited)로 분산한다. 키워드 검색이 ≈1/6(16.7%)을 차지하는데, p95(상위 5%)는 가장 느린 요청이 지배하므로 **키워드 풀스캔을 인덱스로 전환하지 않으면 p95 SLO를 깰 수 없다** — 필터 경로만 빨라져도 키워드 풀스캔이 p95 꼬리를 점유한다.

## 2. 원인

키워드 쿼리는 `WHERE status='SELLING' AND (LOWER(title) LIKE '%k%' OR LOWER(artist.name) LIKE '%k%')`로 변환되어 **세 겹으로 인덱스를 무력화**한다.

```java
// (Before) AlbumSpecs.keyword — 인덱스 사용 불가 3종 세트
String pattern = "%" + escaped + "%";                              // (a) 선행 와일드카드
Predicate titleMatch  = cb.like(cb.lower(root.get("title")), ...); // (b) LOWER() 함수 래핑
Predicate artistMatch = cb.like(cb.lower(artist.get("name")), ...);// (c) title·name 이 다른 테이블 → OR
return cb.or(titleMatch, artistMatch);
```

- **(a) 선행 와일드카드** `%k%` — B-Tree 의 정렬 전제를 깨 범위 검색 불가.
- **(b) `LOWER()` 래핑** — 함수 적용 컬럼은 원본 인덱스를 못 탐(collation 이 이미 `utf8mb4_unicode_ci` 라 사실 불필요한 중복).
- **(c) cross-table OR** — `title`(album)과 `name`(artist)이 다른 테이블이라, FULLTEXT/B-Tree 를 붙여도 `MATCH(title) OR MATCH(name)` 은 옵티마이저가 단일 인덱스로 구동하지 못하고 풀스캔으로 회귀한다.
- 게다가 `idx_album_title` 자체가 부재(V6 [W10] 의도적 누락) → `type=ALL` 외 선택지 없음.

## 3. 개선

키워드 부분일치를 인덱스로 해소할 수 있는 건 MySQL 에서 **FULLTEXT** 뿐이다. 단 (c) cross-table OR 문제 때문에 두 테이블에 각각 FULLTEXT 를 붙여 OR 하면 여전히 풀스캔이다. 그래서 **artist 이름을 `album.artist_name` 으로 비정규화**해 단일 테이블 `FULLTEXT(title, artist_name)` 한 방으로 구동한다 — Elasticsearch 의 *flattened 검색 문서* 패턴을 RDB 안에서 재현한 것.

**(1) 비정규화 + 인덱스 (`V21__add_search_indexes.sql`)**

```sql
ALTER TABLE album ADD COLUMN artist_name VARCHAR(200) NOT NULL DEFAULT '' AFTER title;
UPDATE album a JOIN artist ar ON a.artist_id = ar.id SET a.artist_name = ar.name;
ALTER TABLE album ADD FULLTEXT INDEX ft_album_keyword (title, artist_name) WITH PARSER ngram;
-- 필터/정렬 경로(트래픽 5/6) — ERD §4.6 후보 + 기본정렬 보조
ALTER TABLE album ADD INDEX idx_album_status_created (status, created_at), ALGORITHM=INPLACE, LOCK=NONE;
ALTER TABLE album ADD INDEX idx_album_search (genre_id, status, price),   ALGORITHM=INPLACE, LOCK=NONE;
ALTER TABLE album ADD INDEX idx_album_year (release_year),                ALGORITHM=INPLACE, LOCK=NONE;
ALTER TABLE album ADD INDEX idx_album_limited (is_limited, status),       ALGORITHM=INPLACE, LOCK=NONE;
```

- **ngram 파서**(token=2): 한글/CJK·부분일치를 지원해 기존 substring LIKE 의미에 가장 근접. `BOOLEAN MODE`를 써서 NATURAL LANGUAGE 의 50% 임계 규칙을 피한다.
- **첫 FULLTEXT 제약**: InnoDB 테이블의 첫 FULLTEXT 인덱스는 숨은 `FTS_DOC_ID` 컬럼 생성을 위해 테이블 리빌드 + SHARED 락이 필요하다 — 다른 인덱스(V11/V16)의 `LOCK=NONE` 온라인 DDL 을 쓸 수 없다(MySQL 제약).

**(2) Hibernate 커스텀 함수 등록** — Criteria 에서 `MATCH ... AGAINST` 를 호출하기 위해 패턴 함수를 등록한다. `META-INF/services/org.hibernate.boot.model.FunctionContributor` 로 자동 발견.

```java
// FulltextFunctionContributor — fts_match(c1, c2, q) → MATCH(c1,c2) AGAINST(q IN BOOLEAN MODE) : Double
fc.getFunctionRegistry().registerPattern(
    "fts_match", "match (?1, ?2) against (?3 in boolean mode)",
    fc.getTypeConfiguration().getBasicTypeRegistry().resolve(StandardBasicTypes.DOUBLE));
```

**(3) 키워드 Spec 재작성** — LIKE → FULLTEXT. cross-table 조인이 사라져 쿼리도 단순해진다(`@EntityGraph` 동반 페치는 #203 그대로).

```java
// (After) AlbumSpecs.keyword
String phrase = toFulltextPhrase(keyword); // boolean-mode 연산자 제거 후 따옴표 구문(phrase)
return (root, query, cb) -> cb.greaterThan(
        cb.function("fts_match", Double.class,
                root.get("title"), root.get("artistName"), cb.literal(phrase)), 0.0);
```

**(4) `artist_name` 동기화** — `Album.create/update` 가 artist 로부터 파생; artist 이름 변경 시 `ArtistService.update` 가 `AlbumRepository.updateArtistNameByArtistId` 로 벌크 갱신(이름이 실제 바뀐 경우만).

**근거 (context7 확인)**: Hibernate 7.2 의 커스텀 SQL 함수는 `FunctionContributor.registerPattern(name, pattern, BasicType)` 으로 등록(`MetadataBuilderContributor.applySqlFunction` 은 `StandardSQLFunction` 용이라 MATCH 패턴엔 부적합). MySQL 8 InnoDB FULLTEXT 는 `ALTER TABLE ... ADD FULLTEXT ... WITH PARSER ngram` + `MATCH() AGAINST(... IN BOOLEAN MODE)`.

## 4. Before / After 측정

측정 환경: 로컬 docker-compose `mysql:8.4`(album 50,000건 시드 #140), `MATCH` 키워드는 `love`. Before 는 #196(`baseline.md` §5.3), After 는 본 작업에서 V21 적용 후 재측정.

### 4.1 EXPLAIN (쿼리 레벨 — 환경 독립적, 결정적 증거)

| 지표 | Before (W9, #196) | After (#204, V21) |
|---|---|---|
| 키워드 EXPLAIN `type` | `ALL` (풀 스캔) | **`fulltext`** (key=`ft_album_keyword`) |
| 실제 search 쿼리 `query_cost` | 6827.65 | **0.35** |
| 단순 `title LIKE` `query_cost` | 5084.55 | (FULLTEXT 전환으로 해당 경로 폐기) |
| `rows_examined` (50k) | ≈49,803 | **1** (옵티마이저 추정; 실제 매칭 878 = 1.76%) |
| cross-table OR | album 풀 스캔 + artist eq_ref | 비정규화로 **단일 테이블 단일 MATCH** |

```
-- After: 실제 search 쿼리 (status 필터 + 키워드)
EXPLAIN SELECT a.* FROM album a WHERE a.status='SELLING'
        AND MATCH(a.title,a.artist_name) AGAINST('"love"' IN BOOLEAN MODE);
-- type=fulltext, key=ft_album_keyword, rows=1, query_cost=0.35  (Before: type=ALL, rows≈49803, cost=6827.65)
```

**비키워드 필터/정렬 경로(트래픽 5/6)도 풀 스캔 제거**: 기본 목록(`status='SELLING' ORDER BY created_at DESC`)·장르 필터·가격 범위는 `idx_album_status_created` 의 **backward index scan**(filesort 없음, LIMIT 조기 종료)으로 전환됐다. `price`/`releaseYear` 정렬만 status ref 후 filesort(23k → LIMIT 20)가 남는다. 즉 `type=ALL` 경로가 검색 전반에서 사라졌다.

### 4.2 k6 (HTTP 레벨 — `loadtest/search.js`, 50 VU)

| 지표 | Before (W9, #196) | After (#204) |
|---|---|---|
| TPS | ~60 req/s | **~140 req/s** |
| error rate / checks | 0% / — | **0% / 100%** (8,436 검증 통과) |
| search p95 | 930 ms | ~1.0 s (아래 주의) |

- **0 error · checks 100%** 는 Hibernate 커스텀 `fts_match` 함수 + FULLTEXT 경로가 실 서버 부하에서 정상 동작함을 엔드투엔드로 확인한다(키워드 1/6 포함 전 경로 200).
- **TPS ~60 → ~140 (≈2.3배)**: 동일 50 VU 에서 처리량이 늘었다 — 쿼리 단가 하락(§4.1)의 직접 반영.
- **⚠️ p95 주의**: 본 세션의 측정 환경은 OrbStack 에서 mysql 이 `platform: linux/amd64` 로 **x86 에뮬레이션** 실행되어(컨테이너 `uname -m` = x86_64) MySQL 실행이 CPU 바운드로 포화됐다. 그 결과 인덱스가 적중하는 20행 쿼리도 median ≈600 ms 로, 50 VU 포화 하의 p95 가 에뮬레이션 CPU 천장에 묶인다(콜드/웜 재측정 모두 ≈1.0 s 동일). 절대 p95 는 baseline 머신 조건과 비교 불가하며(baseline 도 "절대 수치는 머신 종속" 명시), **풀 스캔 제거의 신뢰 가능한 증거는 §4.1 EXPLAIN(query_cost 6827.65 → 0.35, rows 49803 → 1)** 이다. p95 < 800 ms SLO 의 최종 확인은 네이티브(비에뮬레이션) baseline 환경에서 재실행한다.

## 5. 검증

```bash
# 단위/통합 (Docker/Testcontainers 기동 상태)
cd backend && ./gradlew test --tests "com.groove.catalog.album.*" --tests "com.groove.catalog.artist.*"
```

- `AlbumRepositoryTest.searchIndexes_areAdded` — V21 인덱스(`ft_album_keyword`·`idx_album_search` 등) 존재를 코드에 고정(#203 N+1 가드 전환과 동일 의도 — 회귀 시 즉시 실패).
- `AlbumRepositoryTest.updateArtistNameByArtistId_syncsDenormalizedColumn` — 비정규화 동기화.
- `AlbumQueryControllerTest` — 키워드 FULLTEXT 매칭(`stones` → title+artist_name 2건), boolean 연산자 sanitize.
- `AlbumQueryN1Test` — 키워드 FULLTEXT 경로에서도 N+1 부재(`entityFetchCount=0`, 쿼리 2개) 유지.

EXPLAIN / k6 재현 절차는 `docs/measurement/baseline.md` §5.2 와 동일(쿼리만 `MATCH ... AGAINST` 로 교체).

## 6. 주의 / 메모

- **의미 차이(substring → 단어/ngram)**: ngram(token=2) phrase 매칭이라 1글자 키워드는 매칭되지 않고, 토큰 경계가 순수 substring 과 100% 일치하지는 않는다. boolean-mode 연산자(`+ - > < ( ) ~ * " @`)는 sanitize 로 제거한다.
- **실무라면 ES**: 프로덕션급 한국어 상품 검색은 보통 Elasticsearch + Nori(형태소 분석) 로 RDB 밖으로 분리하고 동의어·오타교정·자동완성·랭킹을 얹는다. 본 과제는 *RDB 슬로우쿼리를 인덱스로 Before/After 개선하는 역량 시연*이라 FULLTEXT 로 한정했다. 비정규화 `artist_name` 은 ES 색인 문서의 flattening 을 RDB 안에서 흉내 낸 것.
- **비정규화 동기화 비용**: artist 이름 변경 시 해당 artist 의 album 을 벌크 UPDATE 한다(대량 변경 시 비용 인지). API 응답엔 `artist_name` 을 노출하지 않는다(검색 전용).
- **마이그레이션 버전**: 이슈 제목의 "V6"은 이미 album 생성에 사용됨. 또 member 의 Java 마이그레이션 `V20__email_hash_hmac_backfill` 이 버전 20을 선점하므로 본 작업은 **V21** 로 배정했다.
- **테스트 인프라**: static 싱글턴 Testcontainers 를 @DataJpaTest 슬라이스와 @SpringBootTest 가 공유하는데, 위 Java V20 은 슬라이스에서 resolve 되지 않아 V21 과의 버전 순서가 어긋난다. 테스트 프로파일에서만 `spring.flyway.out-of-order=true` + `ignore-migration-patterns="*:missing"` 으로 완화한다(프로덕션은 전체 빈이 모든 마이그레이션을 resolve 하므로 무관).
