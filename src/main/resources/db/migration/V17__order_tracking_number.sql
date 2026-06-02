-- V17: orders 운송장번호 컬럼 (이슈 #116).
--
-- 배송 생성(ShippingCreationListener, OrderPaidEvent AFTER_COMMIT) 시 발급된 운송장번호를 주문에
-- 비정규화한다. order -> shipping 역참조(모듈 의존 역전) 없이 주문 상세 응답(OrderResponse)이 운송장번호를
-- 노출해, 프론트가 별도 매핑 API 없이 GET /shippings/{trackingNumber} 로 배송 추적하게 한다.
--
-- 쓰기 주체는 shipping 모듈의 리스너(이미 order 를 의존) — 쓰기 방향 shipping->order 라 모듈 경계 유지.
-- 결제 완료 전(배송 미생성)에는 NULL. 운송장번호 형식은 ShippingResponse 와 동일(hex+하이픈, 최대 50자).
ALTER TABLE orders
    ADD COLUMN tracking_number VARCHAR(64) NULL AFTER discount_amount;
