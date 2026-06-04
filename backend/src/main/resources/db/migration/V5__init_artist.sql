-- V5: 카탈로그 — artist (ERD §4.3).
--
-- W5-2 (#32) 범위로 artist 만 도입한다. album (W5-3) 이 후속 V6+ 에서 artist_id 를 FK 로 참조한다.
-- name 은 동명이인 허용을 위해 UNIQUE 미적용 (ERD §4.3 비즈니스 룰). 검색용 idx_artist_name 은
-- W10 슬로우 쿼리 측정 후 별도 마이그레이션으로 추가한다 (ERD §4.3 [W10]).
-- description 은 가변 길이 자유 텍스트(NULL 허용). 길이 상한은 애플리케이션(@Size 2000) 단에서 제어.
CREATE TABLE artist (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(200) NOT NULL,
    description TEXT         NULL,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
