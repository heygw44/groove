-- 앨범 구독 목록은 member_id 로 좁힌 뒤 created_at 내림차순으로 정렬하는데, 정렬 키를 선두로 갖는
-- 인덱스가 없어 uk_album_watch_member_album 으로 그 회원의 구독을 전부 읽고 filesort 로 넘어갔다.
-- 구독 3천 건 기준 3,003행을 읽고 정렬하던 것이 역순 스캔 20행이 된다. 구독이 늘어도 읽는 양이 고정된다.
-- 유니크 제약은 (member_id, album_id) 라 정렬 키를 포함하지 않아 상위 집합으로 대체되지 않는다. 둘 다 남긴다.
create index idx_album_watch_member_created on album_watch (member_id, created_at);
