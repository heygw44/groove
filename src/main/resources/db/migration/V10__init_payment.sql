-- V10: 결제 — payment (ERD §4.11, glossary §2.8).
--
-- W7-3 (#55) 범위. 본 이슈는 결제 요청 접수(PENDING) + 상태 조회까지 — PAID/FAILED 전이와
-- paid_at / failure_reason 기록은 #W7-4 웹훅/폴링에서 처리한다.
--
-- 멱등성 레코드 테이블(idempotency_record)은 ERD §4.12 이며 #W7-2 의 V9 에서 이미 도입했다.
--
-- [W5] (UNIQUE + 웹훅 검증 인덱스만):
--   - uk_payment_order   UNIQUE (order_id)            -- 주문당 결제 1건
--   - idx_payment_pg_tx  (pg_transaction_id)          -- PG 웹훅 검증용
--
-- [W10] (슬로우 쿼리 측정 후 추가, 본 V10 에서는 의도적 누락):
--   - idx_payment_status_created (status, created_at) -- 폴링 스케줄러용 (PENDING 조회)
--
-- 비즈니스 룰 위치:
--   - 주문당 결제 1건                                   : DB UNIQUE (재시도는 새 row 아닌 상태 갱신)
--   - amount >= 0                                      : DB CHECK + 도메인 메서드 이중 방어선
--   - status 전이 (PENDING→PAID/FAILED, PAID→REFUNDED) : APP (PaymentStatus.canTransitionTo)
--   - paid_at: PAID 전환 시 기록                        : APP (#W7-4)
--   - 멱등성 키 이중 보호                                : DB(idempotency_record UNIQUE) + APP(IdempotencyService)
--   - FK 참조 무결성:
--       payment.order_id → orders   ON DELETE CASCADE  (order_item 과 동일 — 주문 삭제 시 결제 동반 삭제)
CREATE TABLE payment (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    order_id          BIGINT       NOT NULL,
    amount            BIGINT       NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    method            VARCHAR(20)  NOT NULL,
    pg_provider       VARCHAR(20)  NOT NULL DEFAULT 'MOCK',
    pg_transaction_id VARCHAR(100) NULL,
    paid_at           DATETIME(6)  NULL,
    failure_reason    VARCHAR(500) NULL,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_payment_amount_non_negative CHECK (amount >= 0),
    CONSTRAINT uk_payment_order UNIQUE (order_id),
    CONSTRAINT fk_payment_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    INDEX idx_payment_pg_tx (pg_transaction_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
