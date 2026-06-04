-- V6: 카탈로그 — album (ERD §4.6 ★).
--
-- W5-3 (#33) 범위로 album 도입. artist/genre/label 을 FK 로 묶고 ON DELETE RESTRICT 로
-- 참조 무결성을 보장한다. 본 마이그레이션은 의도적으로 최소 인덱스만 둔다.
--
-- [W5] (FK 기본 인덱스만):
--   - idx_album_artist (artist_id)
--   - idx_album_genre  (genre_id)
--   - idx_album_label  (label_id)
--
-- [W10] **의도적 누락** — W10 슬로우 쿼리 측정 / 인덱스 튜닝 시연용 (ERD §4.6).
--   - idx_album_search (genre_id, status, price)
--   - idx_album_year   (release_year)
--   - idx_album_title  (title)  — FULLTEXT 검토 후보
--   - idx_album_limited (is_limited, status)
-- 위 인덱스를 본 V6 에서 추가하지 말 것. (PR 리뷰 시 누락이 아니라 의도 — 본 헤더가 근거)
--
-- 비즈니스 룰 위치:
--   - stock ≥ 0 / price ≥ 0 : DB CHECK + 도메인 메서드 이중 방어선
--   - status 전이 룰        : APP (AlbumStatus.canTransitionTo) — W5-3 범위 미포함, 후속 이슈
--   - FK 참조 무결성        : DB ON DELETE RESTRICT
CREATE TABLE album (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    title           VARCHAR(300) NOT NULL,
    artist_id       BIGINT       NOT NULL,
    genre_id        BIGINT       NOT NULL,
    label_id        BIGINT       NULL,
    release_year    SMALLINT     NOT NULL,
    format          VARCHAR(30)  NOT NULL,
    price           BIGINT       NOT NULL,
    stock           INT          NOT NULL DEFAULT 0,
    status          VARCHAR(20)  NOT NULL DEFAULT 'SELLING',
    is_limited      BOOLEAN      NOT NULL DEFAULT FALSE,
    cover_image_url VARCHAR(500) NULL,
    description     TEXT         NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_album_price_non_negative CHECK (price >= 0),
    CONSTRAINT ck_album_stock_non_negative CHECK (stock >= 0),
    CONSTRAINT fk_album_artist FOREIGN KEY (artist_id) REFERENCES artist (id) ON DELETE RESTRICT,
    CONSTRAINT fk_album_genre  FOREIGN KEY (genre_id)  REFERENCES genre  (id) ON DELETE RESTRICT,
    CONSTRAINT fk_album_label  FOREIGN KEY (label_id)  REFERENCES label  (id) ON DELETE RESTRICT,
    INDEX idx_album_artist (artist_id),
    INDEX idx_album_genre  (genre_id),
    INDEX idx_album_label  (label_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
