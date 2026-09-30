-- 관리자 상품주문 목록이 order_item 기준으로 바뀌면서 정렬 키 oi.created_at 을 받쳐 줄 인덱스가 없어
-- 상품주문 전체를 읽고 filesort 로 넘어갔다. 대시보드 클레임 건수도 claim_status 로 좁힐 길이 없어 풀스캔이었다.

-- ORDER BY oi.created_at DESC, oi.id DESC 는 보조 인덱스 끝에 PK 가 붙어 (created_at, id) 역순 스캔으로
-- 그대로 받는다. 상태 탭(PREPARING 등)·취소반품 탭·기간 필터도 이 역순 스캔 위에서 걸러 20건을 채우면 멈춘다.
-- 상품주문 50만 건 기준 무필터 목록이 테이블 스캔 + 정렬 250ms 에서 역순 스캔 20행 0.1ms 가 된다.
-- 상태 탭 전용 (status, created_at) 은 넣지 않았다. 진행 중 상태는 최근 행에 몰려 역순 스캔이 금방 채우고,
-- 상태 전이 UPDATE 마다 갱신할 인덱스가 하나 더 늘어나는 비용이 더 크다.
create index idx_order_item_created on order_item (created_at);

-- 진행 중 클레임(CANCEL_REQUEST·RETURN_REQUEST·COLLECTING)은 전체의 1% 미만이라 선택도가 높다.
-- 건수 서브쿼리 두 개가 테이블 스캔 50만 행에서 인덱스 조회 수백 행이 된다(160ms -> 5ms).
create index idx_order_item_claim_status on order_item (claim_status);
