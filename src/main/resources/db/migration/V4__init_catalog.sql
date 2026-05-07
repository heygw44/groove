-- V4: 카탈로그 정적 엔티티 — genre, label (ERD §4.4, §4.5).
--
-- 본 마이그레이션은 #31 (W5-1) 범위로 genre/label 만 도입한다.
-- artist/album 은 후속 이슈(W5-2/W5-3) 에서 V5 이후로 추가되며 album 의 FK 참조가 이 두 테이블을 가리킨다.
-- name 중복은 DB UNIQUE 가 최종 방어선이며 애플리케이션 선검사(existsByName)는 빠른 409 응답용이다.
CREATE TABLE genre (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    name        VARCHAR(50) NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_genre_name (name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE label (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(100) NOT NULL,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_label_name (name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
