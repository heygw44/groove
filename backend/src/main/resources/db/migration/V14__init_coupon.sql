-- V14: 쿠폰 — coupon, member_coupon (ERD §4.15, §4.16).
--
-- 확장(M13) 범위. 선착순 한정수량 발급의 동시성 제어는 APP(원자적 조건부 UPDATE) — 후속 이슈(#90),
-- decisions/coupon-concurrency.md.
--
-- 비즈니스 룰 위치:
--   - discount_value > 0 / 정률 1~100      : DB CHECK + 도메인(Coupon.Builder)
--   - issued_count <= total_quantity(한정) : DB CHECK + APP 원자적 UPDATE(#90, 1차 방어)
--   - 회원당 동일 쿠폰 1장                  : DB UNIQUE(coupon_id, member_id)
--   - status 전이                          : APP (CouponStatus / MemberCouponStatus canTransitionTo)
--   - expires_at = 발급 시 valid_until 스냅샷 : APP (MemberCoupon.issue)
--   - FK: coupon ON DELETE RESTRICT / member ON DELETE CASCADE / orders ON DELETE SET NULL
CREATE TABLE coupon (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    name                VARCHAR(100) NOT NULL,
    discount_type       VARCHAR(30)  NOT NULL,
    discount_value      BIGINT       NOT NULL,
    max_discount_amount BIGINT       NULL,
    min_order_amount    BIGINT       NOT NULL DEFAULT 0,
    total_quantity      INT          NULL,
    issued_count        INT          NOT NULL DEFAULT 0,
    per_member_limit    INT          NOT NULL DEFAULT 1,
    valid_from          DATETIME(6)  NOT NULL,
    valid_until         DATETIME(6)  NOT NULL,
    status              VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE',
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_coupon_discount_value_positive CHECK (discount_value > 0),
    CONSTRAINT ck_coupon_percentage_range        CHECK (discount_type <> 'PERCENTAGE' OR discount_value BETWEEN 1 AND 100),
    CONSTRAINT ck_coupon_min_order_non_negative  CHECK (min_order_amount >= 0),
    CONSTRAINT ck_coupon_issued_non_negative     CHECK (issued_count >= 0),
    CONSTRAINT ck_coupon_quantity_non_negative   CHECK (total_quantity IS NULL OR total_quantity >= 0),
    CONSTRAINT ck_coupon_issued_within_total     CHECK (total_quantity IS NULL OR issued_count <= total_quantity),
    CONSTRAINT ck_coupon_per_member_positive     CHECK (per_member_limit > 0),
    CONSTRAINT ck_coupon_valid_period            CHECK (valid_until > valid_from),
    INDEX idx_coupon_status (status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- FK 컬럼 인덱스: coupon_id 는 uk(coupon_id, member_id) 의 좌측 프리픽스로 커버,
-- member_id 는 idx(member_id, status) 좌측, order_id 는 idx_member_coupon_order 로 커버.
CREATE TABLE member_coupon (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    coupon_id   BIGINT      NOT NULL,
    member_id   BIGINT      NOT NULL,
    status      VARCHAR(30) NOT NULL DEFAULT 'ISSUED',
    issued_at   DATETIME(6) NOT NULL,
    expires_at  DATETIME(6) NOT NULL,
    used_at     DATETIME(6) NULL,
    order_id    BIGINT      NULL,
    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_member_coupon_coupon_member UNIQUE (coupon_id, member_id),
    CONSTRAINT fk_member_coupon_coupon FOREIGN KEY (coupon_id) REFERENCES coupon (id) ON DELETE RESTRICT,
    CONSTRAINT fk_member_coupon_member FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE CASCADE,
    CONSTRAINT fk_member_coupon_order  FOREIGN KEY (order_id)  REFERENCES orders (id) ON DELETE SET NULL,
    INDEX idx_member_coupon_member_status (member_id, status),
    INDEX idx_member_coupon_order (order_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
