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
-- 인덱스: 본 마이그레이션은 컬럼만 추가하고, V30 에서 idx_outbox_unpublished 를 (published_at, attempt_count,
-- id) 로 재구성해 릴레이 조회(published_at IS NULL AND attempt_count < N)가 DLQ 격리 행을 인덱스 레벨에서
-- 제외하도록 한다. ALGORITHM=INSTANT 로 메타데이터만 변경해 테이블 재구성·잠금 없이 즉시 완료한다(MySQL 8.4
-- 는 NOT NULL DEFAULT 컬럼의 위치 지정 추가를 INSTANT 로 지원). 즉시 적용 불가 시 즉시 실패해 INPLACE/COPY
-- 무음 폴백을 막는다.
ALTER TABLE outbox_event
    ADD COLUMN attempt_count INT NOT NULL DEFAULT 0 AFTER published_at,
    ALGORITHM=INSTANT;
