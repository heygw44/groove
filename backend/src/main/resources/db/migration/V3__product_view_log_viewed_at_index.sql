-- 90일 보관 정책의 배치 삭제(WHERE viewed_at < :threshold)를 위한 인덱스.
-- 기존 인덱스는 (member_id, viewed_at), (product_id, viewed_at) 뿐이라 선두 컬럼이 viewed_at 이 아니어서
-- 삭제 대상 조회가 product_view_log 전체를 풀스캔한다.
create index idx_view_log_viewed_at on product_view_log (viewed_at);
