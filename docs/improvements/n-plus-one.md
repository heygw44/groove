# N+1 SELECT 제거 — 상품 목록 검색 (`@EntityGraph` 페치 조인)

> 이슈 [#203](https://github.com/heygw44/groove/issues/203) · 마일스톤 M10(W10 CS 개선 1차) · 도메인 catalog
> Before 베이스라인: [#196](https://github.com/heygw44/groove/issues/196) · [`docs/measurement/baseline.md`](../measurement/baseline.md) §2·§5.3·§5.6

## 1. 문제 정의

공개 상품 목록·검색 `GET /albums`(`AlbumService.search`)는 페이지의 앨범마다 artist/genre/label을 **행 단위 단건 SELECT**로 끌어왔다. 5행 조회에 쿼리 **17개**가 발생하고, 행 수 P에 비례해 `1 + 1 + 3P`로 선형 증가한다. 페이지가 커질수록 DB 라운드트립이 비례 증가하는 전형적 N+1이다.

- 본 쿼리 **1** (album `limit`)
- 평점 집계 **1** (`review … where album_id in (…) group by` — 이미 `IN` 집계라 N+1 아님)
- lazy resolve **3P** (행마다 artist + genre + label 단건 SELECT)

## 2. 원인

`Album`의 세 연관이 모두 `@ManyToOne(fetch = LAZY)`인데, 응답 DTO 변환(`AlbumSummaryResponse.from()`)이 이들을 트랜잭션 내에서 모두 접근한다 → 프록시가 행마다 풀리며 단건 SELECT가 발생한다.

```java
// AlbumSummaryResponse.from(Album, AlbumRating) — 변환 시 lazy proxy 가 행마다 풀림
ArtistRef.from(album.getArtist()),   // SELECT artist  WHERE id=?
GenreRef.from(album.getGenre()),     // SELECT genre   WHERE id=?
LabelRef.from(album.getLabel()),     // SELECT label   WHERE id=?  (nullable)
```

페치 조인을 의도적으로 쓰지 않아 W9까지 시연용으로 보존했고(`AlbumQueryN1Test`), #196에서 수치를 박제했다.

## 3. 개선

`AlbumRepository`에서 `JpaSpecificationExecutor.findAll(Specification, Pageable)`를 **`@EntityGraph`로 오버라이드**해 세 연관을 본 쿼리에 동반 페치한다. Specification 동적 필터·페이징은 그대로 두고 페치 전략만 바꾼다.

```java
// AlbumRepository.java
@Override
@EntityGraph(attributePaths = {"artist", "genre", "label"})
Page<Album> findAll(Specification<Album> spec, Pageable pageable);
```

**근거 (Spring Data JPA 공식 패턴 · context7로 확인)**
- 페이징+Specification에 엔티티 그래프를 적용하는 정식 방법은 상속한 `findAll(Specification, Pageable)`을 재선언하고 `@EntityGraph`를 붙이는 것(`spring-data-jpa` 레퍼런스 / 이슈 DATAJPA-1207). ad-hoc `attributePaths`는 별도 `@NamedEntityGraph` 정의 없이 경로만으로 그래프를 만든다.
- artist/genre/label은 **전부 to-one**이라 컬렉션 페치가 아니다 → 페이징 in-memory 경고(HHH000104) 없이 본 쿼리에 OUTER JOIN으로 인라인된다.
- 이 `findAll` 오버라이드의 **유일한 호출처는 `AlbumService.search`** 이므로 영향 범위가 목록 경로에 한정된다. 단건 `findDetail`(별도 `findById` 경로)는 변경하지 않았다.
- 환경: Spring Boot 4.0.6 / Java 21 — 이 API를 완전 지원.

## 4. Before / After 측정

Hibernate `Statistics`로 측정(`AlbumQueryN1Test`, `generate_statistics=true`). 측정 범위는 **쿼리 수**(결정론적). 응답시간 p95 재측정은 본 이슈 범위 외(슬로우쿼리 풀스캔과 엮여 있어 W10-2 검색 인덱스 소관).

| 지표 | Before (W9, #196) | After (#203) |
|---|---|---|
| `prepareStatementCount` (5행) | **17** | **2** |
| `prepareStatementCount` 일반식 | `1 + 1 + 3P` (선형) | **상수 2** (행수 무관) |
| `entityFetchCount` (= N) | **15** | **0** |
| `queryExecutionCount` | 2 | 2 (유지) |

**행수 무관 상수화 증명** (`search_keepsQueryCountConstant_regardlessOfRowCount`):

```
[#203 N+1 after] rows=3 → prepareStatementCount=2, rows=7 → prepareStatementCount=2 (entityFetchCount=0, queryExecutionCount=2)
```

- 5행: 17 → 2 (≈ 8.5배 감소), lazy resolve 15 → 0.
- 단일 페이지(행수 < page size 20)라 count 쿼리가 스킵돼 `2`(본 쿼리 + 평점집계). 작은 페이지로 count가 실행돼도 행수와 무관하게 상수다.

## 5. 검증

```bash
# OrbStack/Docker(Testcontainers) 기동 상태에서
cd backend
./gradlew test --tests "com.groove.catalog.album.application.AlbumQueryN1Test"
```

`AlbumQueryN1Test`를 **N+1 재발 방지 회귀 가드**로 전환했다(이전 "쿼리 수 > 5" 보존 게이트의 방향 역전):
- `search_fetchesAssociationsInOneQuery_noN1` — `entityFetchCount == 0` **and** `prepareStatementCount == 2`, 연관 값이 DTO에 채워졌는지(프록시 null 아님) 확인.
- `search_keepsQueryCountConstant_regardlessOfRowCount` — 3건·7건 모두 쿼리 2개로 동일.
- 누군가 `@EntityGraph`를 제거하면 두 테스트가 즉시 실패한다.

회귀 점검: `com.groove.catalog.album.*` 전체 통과 — 검색 필터/페이징/정렬 무회귀.

## 6. 주의 / 메모

- **keyword 검색 경로**: `AlbumSpecs.keyword`가 WHERE 절용 `root.join("artist", LEFT)`(non-fetch)를 만든다. `@EntityGraph` 페치 조인과 합쳐지면 artist 테이블 조인이 둘 생길 수 있으나 to-one이라 행 증식이 없어 무해하다(쿼리 수 상수 유지).
- **count 쿼리**: Spring Data는 count 쿼리에 EntityGraph를 적용하지 않아 불필요한 조인이 없다.
- **정렬**: 컨트롤러 화이트리스트가 root 스칼라(id/createdAt/price/releaseYear)뿐이라 연관 정렬 조인 이슈가 없다.
- **단건 조회(`findDetail`)**: 본 이슈 범위 외. 단건이라 N+1이 3으로 고정(비선형)이고 별도 `findById` 경로다.
