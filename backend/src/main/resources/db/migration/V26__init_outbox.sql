-- V26: 이벤트 아웃박스 — outbox_event (M16 #237 트랜잭셔널 아웃박스).
--
-- 결제 완료 후속 처리(배송 생성·알림 등)를 인프로세스 @TransactionalEventListener(AFTER_COMMIT) 로 발행하면
-- 커밋과 리스너 실행 사이에 프로세스가 죽을 때 유실된다. 트랜잭셔널 아웃박스는 상태 변경과 같은 트랜잭션에서
-- 이벤트를 이 테이블에 기록(원자 커밋)하고, 릴레이 스케줄러(OutboxRelayScheduler)가 미발행 행을 주기적으로
-- 발행(at-least-once)한다. 컨슈머는 멱등(예: ShippingProvisioner.existsByOrderId)이라 중복 발행·재기동에도
-- 정확히 1회 효과다.
--
-- 비즈니스 룰/처리 위치:
--   - 발행(미발행 행 기록)        : APP — 상태 변경 트랜잭션 안에서 OutboxEventPublisher 가 INSERT
--   - 릴레이(미발행 → 발행 완료)   : APP — OutboxRelayScheduler 가 published_at IS NULL 배치 조회 후 디스패치
--   - 멱등 컨슈머                  : APP — OutboxEventHandler 구현(배송은 existsByOrderId)
--   - TTL 정리(발행 완료 행 회수)  : APP — OutboxEventCleanupTask 가 published_at < now - retention 삭제
--
-- payload 는 이벤트 본문 JSON(TEXT — idempotency_record.response_body 선례와 동일).
-- published_at NULL = 미발행. idx_outbox_unpublished (published_at, id): 릴레이가 미발행 행을 id FIFO 로
-- 스캔하고, 정리 스케줄러가 published_at 범위로 회수하는 두 접근을 한 인덱스로 흡수한다.
CREATE TABLE outbox_event (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    aggregate_type VARCHAR(50)  NOT NULL,
    aggregate_id   BIGINT       NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    payload        TEXT         NOT NULL,
    published_at   DATETIME(6)  NULL,
    created_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_outbox_unpublished (published_at, id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
