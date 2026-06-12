-- V8: 주문 — orders, order_item (ERD §4.9, §4.10).
--
-- W6-2 (#42) 범위. 본 이슈는 도메인 모델 + 상태 머신만 다룬다 — 주문 생성 API,
-- 재고 차감, orderNumber 발급기는 #W6-3 에서 도입한다.
--
-- 테이블명 'orders' 는 SQL 예약어 'order' 회피용 복수형 (ERD §3.0 표기 규칙).
--
-- [W6] (UNIQUE + FK 기본 인덱스만):
--   - uk_orders_number          UNIQUE (order_number)
--   - idx_orders_guest_email    (guest_email)        -- 게스트 주문 조회
--   - idx_order_item_order      (order_id)           -- 주문 상세 조회
--   - idx_order_item_album      (album_id)           -- FK 기본
--
-- [W10] (슬로우 쿼리 측정 후 추가, 본 V8 에서는 의도적 누락):
--   - idx_orders_member_created (member_id, created_at)  -- 회원 주문 목록
--   - idx_orders_status_created (status, created_at)     -- 관리자 상태별 조회
--
-- 비즈니스 룰 위치:
--   - order_number 중복 불가          : DB UNIQUE
--   - status 전이 (PENDING→PAID→…)  : APP (OrderStatus.canTransitionTo + Order.changeStatus 단일 진입점)
--   - total_amount ≥ 0              : DB CHECK + 도메인 메서드 이중 방어선
--   - quantity > 0 / unit_price ≥ 0 : DB CHECK + 도메인 메서드 이중 방어선
--   - member_id XOR guest_email     : APP (#W6-3 OrderService 생성 시점) — DB 제약 미적용
--   - 가격 / 앨범명 스냅샷             : APP (주문 시점 값 복사, album 사후 변경과 무관)
--   - FK 참조 무결성:
--       orders.member_id → member       ON DELETE SET NULL  (게스트 전환 보존, 주문 이력 유지)
--       order_item.order_id → orders    ON DELETE CASCADE   (주문 삭제 시 항목 동반 삭제)
--       order_item.album_id → album     ON DELETE RESTRICT  (주문이 album 을 잡고 있을 수 있음)
CREATE TABLE orders (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    order_number     VARCHAR(30)  NOT NULL,
    member_id        BIGINT       NULL,
    guest_email      VARCHAR(255) NULL,
    guest_phone      VARCHAR(20)  NULL,
    status           VARCHAR(30)  NOT NULL,
    total_amount     BIGINT       NOT NULL,
    paid_at          DATETIME(6)  NULL,
    cancelled_at     DATETIME(6)  NULL,
    cancelled_reason VARCHAR(500) NULL,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_orders_total_non_negative CHECK (total_amount >= 0),
    CONSTRAINT uk_orders_number UNIQUE (order_number),
    CONSTRAINT fk_orders_member FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE SET NULL,
    INDEX idx_orders_guest_email (guest_email)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE order_item (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    order_id             BIGINT       NOT NULL,
    album_id             BIGINT       NOT NULL,
    quantity             INT          NOT NULL,
    unit_price           BIGINT       NOT NULL,
    album_title_snapshot VARCHAR(300) NOT NULL,
    created_at           DATETIME(6)  NOT NULL,
    updated_at           DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_order_item_quantity_positive   CHECK (quantity > 0),
    CONSTRAINT ck_order_item_unit_price_non_neg  CHECK (unit_price >= 0),
    CONSTRAINT fk_order_item_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_order_item_album FOREIGN KEY (album_id) REFERENCES album  (id) ON DELETE RESTRICT,
    INDEX idx_order_item_order (order_id),
    INDEX idx_order_item_album (album_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
