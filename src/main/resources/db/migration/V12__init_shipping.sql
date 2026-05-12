-- V12: 배송 — orders 배송지 스냅샷 컬럼 추가 + shipping 테이블 (ERD §4.9, §4.13).
--
-- W7-6 (#58) 범위. 결제 완료 시 OrderPaidEvent 의 AFTER_COMMIT 리스너가 배송 행을 PREPARING 으로
-- 생성하고, 자동 진행 스케줄러가 PREPARING→SHIPPED→DELIVERED 로 한 단계씩 민다. 운송장 번호는 UUID.
--
-- 1) orders 에 배송지 컬럼 추가
--    API.md §3.5 의 주문 생성 요청 shipping 블록을 주문 시점 스냅샷으로 저장한다 (album_title_snapshot 과 같은
--    이유 — 회원이 사후에 주소록을 바꿔도 발송된 주문에는 영향이 없다). 결제 완료 후 shipping 행으로 그대로 복사된다.
--    기존 행 호환을 위해 NOT NULL 컬럼에는 빈 문자열 / FALSE 기본값을 둔다 (신규 주문은 항상 도메인 검증을 통과한 값).
--    ALGORITHM=INSTANT — MySQL 8.0.12+ 에서 메타데이터만 갱신하는 즉시 ADD COLUMN (테이블 재작성 없음).
ALTER TABLE orders
    ADD COLUMN recipient_name           VARCHAR(50)  NOT NULL DEFAULT '',
    ADD COLUMN recipient_phone          VARCHAR(20)  NOT NULL DEFAULT '',
    ADD COLUMN address                  VARCHAR(500) NOT NULL DEFAULT '',
    ADD COLUMN address_detail           VARCHAR(200) NULL,
    ADD COLUMN zip_code                 VARCHAR(20)  NOT NULL DEFAULT '',
    ADD COLUMN safe_packaging_requested BOOLEAN      NOT NULL DEFAULT FALSE,
    ALGORITHM=INSTANT;

-- 2) shipping 테이블 (ERD §4.13)
--
-- [W5] (UNIQUE 만):
--   - uk_shipping_order    UNIQUE (order_id)          -- 주문당 배송 1건
--   - uk_shipping_tracking UNIQUE (tracking_number)   -- 운송장 번호 중복 불가
--
-- idx_shipping_status (ERD 상 [W10] 표기였음): 자동 진행 스케줄러가 status 기준으로 주기 조회하므로 그 소비처와
--   함께 선반영한다 (V11 의 idx_payment_status_created 와 같은 결정). (status, created_at) 복합으로 두어
--   PREPARING→SHIPPED 스캔(created_at 필터)을 받치고, SHIPPED→DELIVERED 스캔은 status 프리픽스로 좁힌다.
--
-- 비즈니스 룰 위치:
--   - 주문당 배송 1건                                  : DB UNIQUE (uk_shipping_order)
--   - tracking_number 중복 불가                        : DB UNIQUE (uk_shipping_tracking)
--   - status 전이 (PREPARING→SHIPPED→DELIVERED)        : APP (ShippingStatus.canTransitionTo + Shipping 의 단일 진입점)
--   - shipped_at: SHIPPED 전환 시 기록                  : APP (Shipping.markShipped)
--   - delivered_at: DELIVERED 전환 시 기록             : APP (Shipping.markDelivered)
--   - 배송지 = 주문 시점 스냅샷 복사                     : APP (ShippingCreationListener)
--   - FK 참조 무결성:
--       shipping.order_id → orders  ON DELETE CASCADE  (payment / order_item 과 동일 — 주문 삭제 시 배송 동반 삭제)
CREATE TABLE shipping (
    id                       BIGINT       NOT NULL AUTO_INCREMENT,
    order_id                 BIGINT       NOT NULL,
    tracking_number          VARCHAR(50)  NOT NULL,
    status                   VARCHAR(20)  NOT NULL DEFAULT 'PREPARING',
    recipient_name           VARCHAR(50)  NOT NULL,
    recipient_phone          VARCHAR(20)  NOT NULL,
    address                  VARCHAR(500) NOT NULL,
    address_detail           VARCHAR(200) NULL,
    zip_code                 VARCHAR(20)  NOT NULL,
    safe_packaging_requested BOOLEAN      NOT NULL DEFAULT FALSE,
    shipped_at               DATETIME(6)  NULL,
    delivered_at             DATETIME(6)  NULL,
    created_at               DATETIME(6)  NOT NULL,
    updated_at               DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_shipping_order UNIQUE (order_id),
    CONSTRAINT uk_shipping_tracking UNIQUE (tracking_number),
    CONSTRAINT fk_shipping_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    INDEX idx_shipping_status (status, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
