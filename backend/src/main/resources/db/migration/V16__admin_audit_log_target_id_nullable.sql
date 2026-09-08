-- SALES_AGGREGATION 처럼 대상 엔티티가 없는 시스템 범위 감사 행위가 생겨 target_id 를 null 로 저장해야 한다.
-- 지금은 not null 이라 INSERT 가 DataIntegrityViolationException 으로 실패하고 AdminAuditLogWriter 가
-- 그 예외를 삼켜(afterCommit 예외 전파 금지) 감사 로그만 조용히 사라진다.
alter table admin_audit_log modify column target_id bigint null;
