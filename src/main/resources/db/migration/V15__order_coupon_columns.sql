-- V15: orders 쿠폰 할인 컬럼 (ERD §4.9, docs/plans/coupon-system.md §부록 V15).
--
-- 확장(M13) 범위. payable = total_amount - discount_amount 파생(저장 안 함 — 도메인 getPayableAmount).
-- applied_member_coupon_id 를 orders 에 두지 않아 orders <-> member_coupon 순환 FK 를 회피한다
-- (사용 쿠폰 추적은 member_coupon.order_id 역참조 — ERD §4.16 비고).
--
-- 비즈니스 룰 위치:
--   - 0 ≤ discount_amount ≤ total_amount : DB CHECK + 도메인 가드(Order.applyDiscount)
--   - 쿠폰 적용/복원 트랜잭션 정합        : APP (OrderService.place / cancel, AdminOrderService.refund)
ALTER TABLE orders
    ADD COLUMN discount_amount BIGINT NOT NULL DEFAULT 0 AFTER safe_packaging_requested,
    ADD CONSTRAINT ck_orders_discount_within_total
        CHECK (discount_amount >= 0 AND discount_amount <= total_amount);
