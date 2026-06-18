-- V30: 릴레이 인덱스에 attempt_count 포함 — DLQ 격리 행을 인덱스 레벨에서 제외 (#268 코드리뷰 반영).
--
-- V29 로 attempt_count 가 생기며 릴레이 조회가 published_at IS NULL AND attempt_count < N 으로 바뀌었다.
-- 기존 idx_outbox_unpublished (published_at, id) 는 attempt_count 를 커버하지 못해, DLQ 격리 행
-- (attempt_count >= N, published_at NULL — cleanup 이 발행 완료 행만 회수하므로 영구 잔존)도 매 릴레이 주기
-- 인덱스 선두(id 작음=오래됨)에서 스캔된 뒤 잔여 필터로 버려졌다. 격리 행이 누적되면 주기당 O(격리행) 비용.
--
-- (published_at, attempt_count, id) 로 재구성하면 attempt_count < N 범위 스캔이 격리 행을 인덱스 레벨에서
-- 제외해 죽은 행을 읽지 않는다. ORDER BY id 정렬 대상은 라이브(미격리) 행으로 한정되어 작은 파일소트만 남고,
-- 정리 쿼리(published_at < cutoff)는 published_at 선두를 그대로 활용한다.
-- (격리 행은 진단을 위해 삭제하지 않고 보존하되, 본 인덱스로 스캔 비용만 제거한다 — V29 주석의 '복합 인덱스
-- 재설계 불필요' 판단을 코드리뷰 피드백으로 수정.)
ALTER TABLE outbox_event DROP INDEX idx_outbox_unpublished;
CREATE INDEX idx_outbox_unpublished ON outbox_event (published_at, attempt_count, id);
