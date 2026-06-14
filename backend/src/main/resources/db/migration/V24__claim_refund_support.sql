-- V24: 반품 환불 지원 (M16 #239) — 결제 부분 환불 누적액 + 주문 전량 반품 마커.
--
-- payment.refunded_amount: 부분 반품으로 누적된 환불액. 발송 전 전액 환불(markRefunded)은 amount 전액을,
--   부분 반품 환불(refund)은 claim 환불액을 누적한다. refunded_amount == amount 가 되면 결제가 REFUNDED(종착),
--   그 전엔 PARTIALLY_REFUNDED. PARTIALLY_REFUNDED 는 기존 VARCHAR(20) status 컬럼에 그대로 저장(DDL 불필요).
--
-- orders.returned_at: 전량 반품 완료 마커. 모든 OrderItem 이 반품 환불되어 결제 전액이 환불됐을 때만 찍는다 —
--   OrderStatus 는 DELIVERED/COMPLETED 로 유지해 "배송된 사실"을 보존하고(상태 폭발 회피) 환불 여부만 표식한다.
--   부분 반품만 있으면 NULL 로 남고, Claim aggregate 가 부분 반품의 진실 원천이다.
--
-- 둘 다 ADD COLUMN 이라 ALGORITHM=INSTANT 로 메타데이터만 갱신(테이블 재작성 없음, MySQL 8.0.12+).
ALTER TABLE payment
    ADD COLUMN refunded_amount BIGINT NOT NULL DEFAULT 0,
    ALGORITHM=INSTANT;

ALTER TABLE orders
    ADD COLUMN returned_at DATETIME(6) NULL,
    ALGORITHM=INSTANT;
