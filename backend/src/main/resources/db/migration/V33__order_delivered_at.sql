-- V33: orders.delivered_at — 반품 기한 anchor 의 결정적 기준 (#326).
--
-- 반품 기한은 "배송완료 시각" 을 기준으로 삼는다. 그 시각이 별도 애그리거트(shipping.delivered_at)에만
-- 존재하면, 관리자 강제 전이(AdminOrderService.changeStatus)·로컬 시드처럼 배송 행을 만들지 않고
-- 주문만 DELIVERED 로 전진시키는 경로에서 anchor 를 결정할 수 없어 정상 반품이 막힌다.
--
-- 배송완료 시각을 주문 자신의 컬럼으로 끌어올린다. 모든 DELIVERED 전이는 Order.changeStatus 단일
-- 진입점을 거치므로(정상 파이프라인·관리자 강제·시드 공통), 이후 생성되는 주문은 배송 행 유무와
-- 무관하게 delivered_at 을 항상 갖는다. paid_at/cancelled_at 과 같은 패턴.

ALTER TABLE orders
    ADD COLUMN delivered_at DATETIME(6) NULL AFTER paid_at;

-- 기존 주문 백필 — 정상 배송 파이프라인을 거친 주문은 shipping.delivered_at 을 보유하므로 그대로 복사해
-- 이미 배송완료된 주문의 반품 가능성을 보존한다. 배송 행이 없는 과거 강제 전이 주문은 결정 불가로 남는다.
UPDATE orders o
    JOIN shipping s ON s.order_id = o.id
   SET o.delivered_at = s.delivered_at
 WHERE o.delivered_at IS NULL
   AND s.delivered_at IS NOT NULL;
