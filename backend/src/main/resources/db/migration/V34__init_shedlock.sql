-- V34: ShedLock 분산락 테이블 (#365 — 다중 인스턴스 배치 중복 실행 차단).
--
-- @Scheduled 배치 10종이 노드마다 동시에 뛰는 것을 행 단위 락으로 막는다. 새 인프라 없이 기존 DB 로.
-- JdbcTemplateLockProvider 기본 스키마(테이블/컬럼명 고정). .usingDbTime() 을 쓰므로 lock_until/locked_at
-- 은 DB 서버 클록 기준이라 노드 간 시계 오차에 휘둘리지 않는다.

CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP(3) NOT NULL,
    locked_at  TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    locked_by  VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
