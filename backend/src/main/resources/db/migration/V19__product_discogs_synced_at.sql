-- Discogs API 약관상 API Content 를 원본보다 6시간 이상 오래된 상태로 표시할 수 없다.
-- 재검증 스케줄러와 신선도 판정의 선행 작업으로 마지막 동기화 시각을 기록할 컬럼을 둔다.
alter table product add column discogs_synced_at datetime(6) null;

-- 백필은 NOW() 로 한다. NULL 이 의미상 정확하지만("동기화된 적 없음") 배포 순간 운영 273건
-- 전부가 stale 이 되어 최초 재검증 스윕이 끝날 때까지 모든 Discogs 상품의 스펙 표가 비는,
-- 우리가 스스로 만드는 장애다. created_at 은 몇 주 전 적재분이라 NULL 과 다를 게 없다.
-- NOW() 는 "마이그레이션 시점에 이 데이터는 신선했다고 간주한다"는 의도적 단언이고,
-- 같은 API 로 최근 적재된 데이터라 근거가 있다. 6시간 유예 한 번을 사는 대가로 화면이
-- 비는 사고를 없애며, 그 빚은 273건 약 9분이면 갚힌다.
update product set discogs_synced_at = now() where discogs_release_id is not null;

create index idx_product_resync on product (discogs_release_id, discogs_synced_at);
