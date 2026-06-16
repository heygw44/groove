-- V27: 클레임 종류 — 취소(CANCEL)/반품(RETURN) 통합 (M16 #238 부분 취소·부분 환불).
--
-- #239 가 만든 claim aggregate 에 claim_type 을 도입해 발송 전 부분 취소(CANCEL)를 흡수한다 — 네이버 커머스의
-- 통합 클레임 모델과 동일하게, 취소를 위한 별도 테이블/메커니즘을 만들지 않고 타입으로 구분한다.
--   - CANCEL : 발송 전(주문 PAID/PREPARING) 부분 취소 — 회수·검수 없이 REQUESTED → REFUNDED 즉시 환불 (관리자).
--   - RETURN : 발송 후(주문 DELIVERED/COMPLETED) 반품 — 역물류 상태머신 (#239 기존 동작).
--
-- 비즈니스 룰 위치:
--   - 타입별 자격 윈도우(CANCEL=PAID/PREPARING, RETURN=DELIVERED/COMPLETED) : APP (ClaimService)
--   - CANCEL 상태 경로 REQUESTED → REFUNDED                                : APP (Claim.markCancelRefunded — 타입·상태 직접 가드)
--   - 부분 취소 환불액(할인 안분) + 쿠폰 최소주문금액 재계산/복원           : APP (ClaimService.cancelPartially / cancellationRefund)
--
-- DEFAULT 'RETURN' 으로 V27 이전 모든 claim 행을 RETURN(#239 의미)으로 백필한다. ADD COLUMN 이라 ALGORITHM=INSTANT
-- 로 메타데이터만 갱신(테이블 재작성 없음, MySQL 8.0.12+). 별도 인덱스는 두지 않는다 — 타입 단독 필터 조회가 없고
-- 기존 idx_claim_order / idx_claim_status 로 접근 경로가 충분하다.
ALTER TABLE claim
    ADD COLUMN claim_type VARCHAR(20) NOT NULL DEFAULT 'RETURN',
    ALGORITHM=INSTANT;
