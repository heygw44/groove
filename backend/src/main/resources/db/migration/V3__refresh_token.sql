-- V3: refresh_token 테이블 (이슈 #22 — Refresh Token Rotation)
--
-- 보안 요구사항: 토큰 평문 저장 금지. token_hash 에 SHA-256(hex 64자) 만 저장.
-- 회전 체인 추적: replaced_by_token_id 가 다음 회전된 토큰을 가리킨다 (self-FK).
-- 재사용 감지: revoked 상태 토큰이 다시 들어오면 같은 member 의 모든 활성 토큰을 무효화.
CREATE TABLE refresh_token (
    id                      BIGINT       NOT NULL AUTO_INCREMENT,
    member_id               BIGINT       NOT NULL,
    token_hash              CHAR(64)     NOT NULL,
    issued_at               DATETIME(6)  NOT NULL,
    expires_at              DATETIME(6)  NOT NULL,
    revoked_at              DATETIME(6)  NULL,
    replaced_by_token_id    BIGINT       NULL,
    created_at              DATETIME(6)  NOT NULL,
    updated_at              DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_token_hash (token_hash),
    KEY idx_refresh_member_revoked (member_id, revoked_at),
    CONSTRAINT fk_refresh_token_member
        FOREIGN KEY (member_id) REFERENCES member (id),
    -- self-FK 의 ON DELETE SET NULL: 회전 체인 행 삭제 시 후방 참조가 자동으로 해제되어
    -- 운영/테스트의 batch 삭제 시 순서 의존성을 없앤다.
    CONSTRAINT fk_refresh_token_replaced_by
        FOREIGN KEY (replaced_by_token_id) REFERENCES refresh_token (id) ON DELETE SET NULL
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
