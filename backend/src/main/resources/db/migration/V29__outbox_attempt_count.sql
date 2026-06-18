-- V29: 아웃박스 릴레이 재시도 상한 + DLQ 격리 — outbox_event.attempt_count (#268).
--
-- OutboxRelayScheduler 는 미발행 행을 건별로 격리해 재시도하므로 한 건 실패가 같은 배치의 나머지를 막지
-- 않는다(정상). 다만 영구 실패(poison) 이벤트는 published_at 이 NULL 로 남아 매 주기 재조회되며 batch-size
-- 슬롯을 영구 점유한다 — 재시도 상한이 없어 정상 이벤트를 밀어낼 수 있다(극단 시나리오).
--
-- attempt_count 로 핸들러 실패 횟수를 누적하고, 릴레이 조회를 published_at IS NULL AND attempt_count < N
-- (max-attempts, 기본 5) 으로 제한한다. N 회를 채운 이벤트는 곧 DLQ(격리) 상태로, 더 이상 디스패치되지
-- 않아 정상 이벤트 처리를 방해하지 않는다(운영자가 카운터를 리셋하면 재구동 가능).
--
-- 비즈니스 룰/처리 위치:
--   - 카운터 증가(핸들러 실패) : APP — OutboxRelayScheduler.recordFailure 가 incrementAttemptCount(독립 트랜잭션)
--   - 릴레이 대상 제한          : APP — findByPublishedAtIsNullAndAttemptCountLessThanOrderByIdAsc(N, limit)
--   - DLQ 격리(attempt_count>=N): 조회에서 자동 제외(별도 status 컬럼 없이 임계값으로 판정)
--
-- 인덱스: 기존 idx_outbox_unpublished (published_at, id) 를 유지한다. 릴레이 조회는 이 인덱스로 미발행 행을
-- id FIFO 스캔하고 attempt_count 는 잔여 필터로 처리한다. poison 은 드물어 격리분 누적이 제한적이므로 본
-- 규모에선 복합 인덱스 재설계가 불필요하다.
ALTER TABLE outbox_event
    ADD COLUMN attempt_count INT NOT NULL DEFAULT 0 AFTER published_at;
