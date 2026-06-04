-- V9: 멱등성 레코드 — idempotency_record (#W7-2, ARCHITECTURE.md §7).
--
-- 결제 요청·웹훅 수신 등 중복 호출에 안전해야 하는 연산을 위한 멱등성 인프라.
-- 클라이언트가 보낸 Idempotency-Key 로 동일 요청을 1회만 처리하고 결과를 캐싱한다.
-- 본 이슈는 인프라만 도입한다 — 실제로 @Idempotent / IdempotencyService 를 소비하는
-- 결제 API 엔드포인트는 #W7-3 에서 붙는다.
--
-- 인덱스:
--   - uk_idempotency_key       UNIQUE (idempotency_key)  -- "1회만 처리"의 1차 방어선 (마커 INSERT 경쟁)
--   - idx_idempotency_expires  (expires_at)              -- TTL 정리 스케줄러의 확정 액세스 패턴 (첫날부터 사용, 슬로우쿼리 후 추가 컨벤션의 예외)
--
-- 비즈니스 룰 위치:
--   - idempotency_key 유일성        : DB UNIQUE
--   - IN_PROGRESS → COMPLETED 전이  : APP (IdempotencyService 가 소유 스레드 1개만 갱신)
--   - TTL 만료 레코드 삭제           : APP (IdempotencyRecordCleanupTask, @Scheduled)
--   - request_fingerprint 일치 검증 : APP (replay 시 IdempotencyService)
--   - 캐시 응답 직렬화/역직렬화       : APP (Jackson, response_type 은 디버깅용 메타)
--
-- 실패 시 마커 행은 삭제된다(재시도 허용) — 따라서 status 는 IN_PROGRESS / COMPLETED 두 값만 존재한다.
-- request_fingerprint 는 호출자 정의(권장: 요청 페이로드의 SHA-256 hex), 미제공 시 NULL.
CREATE TABLE idempotency_record (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    idempotency_key     VARCHAR(255) NOT NULL,
    status              VARCHAR(20)  NOT NULL,
    request_fingerprint VARCHAR(255) NULL,
    response_type       VARCHAR(255) NULL,
    response_body       TEXT         NULL,
    expires_at          DATETIME(6)  NOT NULL,
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_idempotency_key UNIQUE (idempotency_key),
    INDEX idx_idempotency_expires (expires_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
