-- V11: 결제 폴링 스케줄러용 인덱스 (#W7-4, ERD §4.11).
--
-- PaymentReconciliationScheduler 가 웹훅 유실 대비로 PENDING 결제를 created_at 기준 주기 조회한다:
--   SELECT ... FROM payment WHERE status = 'PENDING' AND created_at < :cutoff
-- V10 주석에서 [W10] 슬로우 쿼리 측정 후로 미뤘던 idx_payment_status_created 를, 그 소비처(폴링
-- 스케줄러)가 도착한 본 이슈에서 추가한다.
ALTER TABLE payment ADD INDEX idx_payment_status_created (status, created_at);
