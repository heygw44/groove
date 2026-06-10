-- V21: 검색 인덱스 추가 (#204) — W10 슬로우쿼리 Before/After 의 After 단계.
-- (버전 20 은 member 의 Java 마이그레이션 V20__email_hash_hmac_backfill 이 선점 → V21 배정)
--
-- 베이스라인 #196 (docs/measurement/baseline.md §5) 에서 박제: 키워드 검색이
--   WHERE status='SELLING' AND (LOWER(title) LIKE '%kw%' OR LOWER(artist.name) LIKE '%kw%')
-- 로 선행 와일드카드 + LOWER() 래핑 + 인덱스 부재가 겹쳐 album 50,000건을 매번 풀스캔
-- (EXPLAIN type=ALL, rows≈49,803, search p95 930ms > SLO 800ms). V6 헤더의 [W10] 의도적
-- 누락 인덱스를 본 마이그레이션에서 도입한다. ERD §4.6 후보를 따른다.
--
-- 키워드 경로는 단일 B-Tree 로 해소 불가(선행 와일드카드)하고, title 과 artist.name 이 서로
-- 다른 테이블이라 MATCH(title) OR MATCH(artist.name) 의 cross-table OR 는 옵티마이저가 단일
-- FULLTEXT 인덱스로 구동하지 못해 풀스캔으로 회귀한다. 따라서 artist 이름을 album 에
-- 비정규화(artist_name)해 단일 테이블 FULLTEXT(title, artist_name) 로 만들어(ES 의 flattened
-- 검색 문서 패턴) cross-table OR 자체를 제거한다. artist_name 동기화는 앱(AlbumService.create/
-- update, ArtistService.update 의 벌크 UPDATE)이 담당한다.

-- (1) artist_name 비정규화 컬럼 + 기존 데이터 백필.
ALTER TABLE album ADD COLUMN artist_name VARCHAR(200) NOT NULL DEFAULT '' AFTER title;
UPDATE album a JOIN artist ar ON a.artist_id = ar.id SET a.artist_name = ar.name;

-- (2) 키워드 검색 FULLTEXT (ngram: token=2, 한글/CJK·부분일치로 기존 substring LIKE 의미 보존).
--   주의: InnoDB 테이블에 추가하는 첫 FULLTEXT 인덱스는 숨은 FTS_DOC_ID 컬럼 생성을 위해
--   테이블 리빌드 + SHARED 메타데이터 락이 필요하다 — 다른 인덱스(V11/V16)의 LOCK=NONE 온라인
--   DDL 을 쓸 수 없다(MySQL 제약). 50,000건 규모라 실측상 빠르나 명세상 명시한다.
ALTER TABLE album ADD FULLTEXT INDEX ft_album_keyword (title, artist_name) WITH PARSER ngram;

-- (3) 비키워드 필터/정렬 경로(검색 트래픽의 5/6) 복합 인덱스 — ERD §4.6 후보 + 기본정렬 보조.
--   온라인 DDL(ALGORITHM=INPLACE, LOCK=NONE) — V11/V16 컨벤션. 엔진이 미지원이면 즉시 실패.
--   - idx_album_status_created: 기본 목록(status=SELLING ORDER BY created_at DESC LIMIT) — 정렬+조기종료.
--   - idx_album_search        : 카테고리(genre)+상태+가격 필터 (ERD §4.6).
--   - idx_album_year          : 발매연도 범위 필터.
--   - idx_album_limited       : 한정반 목록(is_limited+status).
ALTER TABLE album ADD INDEX idx_album_status_created (status, created_at), ALGORITHM=INPLACE, LOCK=NONE;
ALTER TABLE album ADD INDEX idx_album_search (genre_id, status, price),   ALGORITHM=INPLACE, LOCK=NONE;
ALTER TABLE album ADD INDEX idx_album_year (release_year),                ALGORITHM=INPLACE, LOCK=NONE;
ALTER TABLE album ADD INDEX idx_album_limited (is_limited, status),       ALGORITHM=INPLACE, LOCK=NONE;
