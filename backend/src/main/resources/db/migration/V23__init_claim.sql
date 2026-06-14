-- V23: 반품 — claim + claim_item (M16 #239 역물류 상태머신).
--
-- 배송완료(DELIVERED)/완료(COMPLETED) 주문에 한해 부분/전체 반품을 접수한다. 상태머신은 OrderStatus 와 분리된
-- 별도 aggregate(Claim) 다 — 주문 상태에 반품을 섞으면 상태 폭발이 일어나기 때문이다:
--   REQUESTED → APPROVED → IN_TRANSIT → INSPECTING → REFUNDED / REJECTED
-- 발송 전 즉시 취소·환불(AdminOrderService.refund)과 의미·경로가 분리된 배송완료 후 역물류 유스케이스다.
--
-- 비즈니스 룰 위치:
--   - status 전이                              : APP (ClaimStatus.canTransitionTo + Claim 단일 진입점)
--   - 반품 자격 (DELIVERED/COMPLETED)           : APP (ClaimService)
--   - 반품 기한 (delivered_at + return-window)  : APP (ClaimService — Shipping.deliveredAt anchor)
--   - 항목 잔여 수량 가드                       : APP (ClaimService — 비-REJECTED claim 수량 합)
--                                                 order_id 비유니크 — 거부(REJECTED) 후 재요청 허용
--   - 검수통과 시 부분환불+재입고+전량시쿠폰복원 : APP (ClaimService.completeRefund, AdminOrderService.refund 미러)
--   - quantity > 0, unit_price_snapshot >= 0    : DB CHECK + APP
--   - FK 참조 무결성:
--       claim.order_id            → orders     ON DELETE CASCADE  (주문 삭제 시 반품 동반 삭제)
--       claim_item.claim_id       → claim      ON DELETE CASCADE  (반품 삭제 시 항목 동반 삭제)
--       claim_item.order_item_id  → order_item ON DELETE CASCADE  (주문 항목과 생명주기 일치)
--
-- idx_claim_status (status, updated_at): 자동 진행 스케줄러가 status 기준 시간경과 후보를 주기 조회 (idx_shipping_status 패턴).
CREATE TABLE claim (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    order_id         BIGINT       NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'REQUESTED',
    reason           VARCHAR(500) NOT NULL,
    rejection_reason VARCHAR(500) NULL,
    refund_amount    BIGINT       NOT NULL DEFAULT 0,
    approved_at      DATETIME(6)  NULL,
    in_transit_at    DATETIME(6)  NULL,
    inspecting_at    DATETIME(6)  NULL,
    completed_at     DATETIME(6)  NULL,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_claim_refund_amount CHECK (refund_amount >= 0),
    CONSTRAINT fk_claim_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    INDEX idx_claim_order (order_id),
    INDEX idx_claim_status (status, updated_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE claim_item (
    id                  BIGINT NOT NULL AUTO_INCREMENT,
    claim_id            BIGINT NOT NULL,
    order_item_id       BIGINT NOT NULL,
    quantity            INT    NOT NULL,
    unit_price_snapshot BIGINT NOT NULL,
    created_at          DATETIME(6) NOT NULL,
    updated_at          DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_claim_item_quantity CHECK (quantity > 0),
    CONSTRAINT ck_claim_item_unit_price CHECK (unit_price_snapshot >= 0),
    -- 한 반품(claim) 안에서 같은 주문 항목 중복 방지 — APP(ClaimService 가 요청 라인 수량 합산)의 DB 방어선.
    CONSTRAINT uk_claim_item_order_item UNIQUE (claim_id, order_item_id),
    CONSTRAINT fk_claim_item_claim FOREIGN KEY (claim_id) REFERENCES claim (id) ON DELETE CASCADE,
    CONSTRAINT fk_claim_item_order_item FOREIGN KEY (order_item_id) REFERENCES order_item (id) ON DELETE CASCADE,
    INDEX idx_claim_item_claim (claim_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
