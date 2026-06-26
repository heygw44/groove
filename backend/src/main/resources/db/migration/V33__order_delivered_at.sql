-- V33: orders.delivered_at — 반품 기한 anchor 의 결정적 기준 (#326).
--
-- 반품 기한은 "배송완료 시각" 을 기준으로 삼는다. 그 시각이 별도 애그리거트(shipping.delivered_at)에만
-- 존재하면, 관리자 강제 전이(AdminOrderService.changeStatus)·로컬 시드처럼 배송 행을 만들지 않고
-- 주문만 DELIVERED 로 전진시키는 경로에서 anchor 를 결정할 수 없어 정상 반품이 막힌다.
--
-- 배송완료 시각을 주문 자신의 컬럼으로 끌어올린다. 모든 DELIVERED 전이는 Order.changeStatus 단일
-- 진입점을 거치므로(정상 파이프라인·관리자 강제·시드 공통), 이후 생성되는 주문은 배송 행 유무와
-- 무관하게 delivered_at 을 항상 갖는다. paid_at/cancelled_at 과 같은 패턴.

-- 스키마 추가만 수행한다. 대량 백필 UPDATE 는 매칭 주문 행 락을 문장 종료까지 잡아 배포 중 쓰기 경로를
-- 막을 수 있으므로 마이그레이션에 두지 않는다. delivered_at 이 비어 있는 기존 배송완료 주문은
-- ClaimService 가 읽기 시점에 shipping.delivered_at 로 보강(결정적)하므로 백필 없이도 반품 가능성이 보존된다.
ALTER TABLE orders
    ADD COLUMN delivered_at DATETIME(6) NULL AFTER paid_at;
