-- 관리자 주문 목록, 상품 목록 기본 정렬, 안 읽은 알림 목록이 정렬 컬럼을 선두로 갖는 인덱스가 없어
-- 대상 테이블을 통째로 읽고 filesort 로 넘어갔다. 정렬 키를 인덱스로 받게 해 역순 스캔으로 바꾼다.

-- 관리자 주문 목록은 ORDER BY created_at DESC 인데 created_at 선두 인덱스가 없었다.
-- status 필터가 붙으면 idx_orders_status_expires 로 후보를 좁힌 뒤 다시 정렬했다.
create index idx_orders_created on orders (created_at);
create index idx_orders_status_created on orders (status, created_at);

-- status 는 값이 7개뿐이라 단독으로는 후보를 못 좁히고, 필터·조인 경로는 좌측 프리픽스가 같은
-- idx_orders_status_expires 가 그대로 대체한다. 유일한 손해는 인기순 정렬의 판매량 집계로,
-- 이 인덱스를 커버링 풀스캔하던 것이 더 넓은 인덱스로 옮겨가 427ms -> 522ms 가 된다.
-- 그 쿼리는 매 요청 4만여 행을 materialize 해 총 1.5~2.5초가 걸리는 별개 문제라 90ms 는 묻힌다.
drop index idx_orders_status on orders;

-- 상품 목록은 status <> 'HIDDEN' 이라 선두 컬럼이 비등가라서 idx_product_status_created 를 못 탄다.
create index idx_product_created on product (created_at);

-- 상품 검색과 자동완성은 전부 title LIKE '%키워드%' 라 title 선두 인덱스로 범위를 못 좁히고,
-- 앨범 중복 판별의 등가 조회는 album 의 idx_album_title_artist 가 맡는다. 실행계획에 등장하지 않는다.
drop index idx_product_title_artist on product;

-- 안 읽은 알림 목록은 read_at 필터만 인덱스로 받고 created_at 정렬은 filesort 로 넘어갔다.
-- 기존 idx_notification_member_read (member_id, read_at) 의 상위 집합이라 그대로 대체한다.
create index idx_notification_member_read_created on notification (member_id, read_at, created_at);
drop index idx_notification_member_read on notification;
