-- V31: payment.callback_token 추가 (#297/#304 — successUrl/failUrl 토큰 바인딩).
--
-- 토스 successUrl/failUrl 은 토스가 보내는 미인증 브라우저 GET 콜백이라 orderNumber 만 알면 호출 가능 →
-- 타인의 진행 중 결제 강제 실패 등 교차 주문 조작 여지(#295 리뷰 P1-1). 이를 막기 위해 checkout 단계에서
-- 결제별 무작위 토큰을 발급·저장하고, successUrl/failUrl 쿼리에 실어 round-trip 시킨 뒤 콜백 핸들러가
-- 저장 토큰과 일치를 검증한다(불일치/누락 → 거부).
--
-- NULL 허용: 레거시(mock) request 경로로 만들어진 결제는 토큰이 없다. orderId 로 단건 조회 후 비교하므로
-- 인덱스는 두지 않는다.

ALTER TABLE payment
    ADD COLUMN callback_token VARCHAR(64) NULL;
