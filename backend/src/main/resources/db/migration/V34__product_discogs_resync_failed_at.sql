-- Discogs 재검증이 실패한 시각. 실패는 discogs_synced_at 을 갱신하지 않아 같은 행이 후보 맨 앞에 계속 서므로,
-- 쿨다운 동안 후보에서 빼 뒤의 정상 행이 굶지 않게 한다. 성공하거나 릴리즈 참조가 끊기면 비운다.
-- 후보 조회는 idx_product_resync(discogs_release_id, discogs_synced_at) 로 좁힌 뒤 이 컬럼은 잔여 조건으로만 걸러
-- 별도 인덱스를 두지 않는다.
alter table product add column discogs_resync_failed_at datetime(6) null;
