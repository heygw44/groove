-- V22: 주문/리뷰 목록 복합 인덱스 추가 (#225) — V8/V13 의 [W10] 의도적 누락분 보완.
--
-- V8(orders)·V13(review) 헤더가 "[W10] 슬로우 쿼리 측정 후 추가" 로 약속했으나 어느
-- 마이그레이션에도 추가되지 않은 복합 인덱스 3종을 본 마이그레이션에서 도입한다. 핫 경로
-- (회원 주문목록 / 상태별 내 주문 / 앨범 리뷰목록)는 모두 ORDER BY created_at 정렬을
-- Pageable(컨트롤러가 createdAt 화이트리스트)에 의존하는데, 현재 인덱스로 정렬이 커버되지
-- 않아 filesort(또는 status 경로는 풀스캔)를 유발한다. (Before/After 측정: #225,
-- docs/improvements/index-coverage.md · docs/measurement/baseline.md)
--
--   - idx_orders_member_created (member_id, created_at)
--       회원 주문목록(findByMemberId)·상태별 내 주문(findByMemberIdAndStatus).
--       Before: fk_orders_member 로 member_id ref 후 created_at filesort → After: 정렬까지 인덱스 커버.
--   - idx_orders_status_created (status, created_at)
--       관리자 상태별 주문 조회. Before: status 인덱스 부재로 type=ALL + filesort → After: ref + 정렬 커버.
--       ※ 스케줄러 배치(findByStatusAndPaidAtBefore…, 익명화 스캔)는 status 접두만 부분 커버 — index-coverage.md §6 참조.
--   - idx_review_album_created (album_id, created_at)
--       앨범 리뷰목록(findByAlbumId). Before: fk_review_album 로 album_id ref 후 created_at filesort → After: 정렬까지 인덱스 커버.
--
-- 온라인 DDL(ALGORITHM=INPLACE, LOCK=NONE) — V11/V16/V21 컨벤션. 엔진 미지원이면 즉시 실패.
--
-- ※ V8/V13 은 이미 적용된 마이그레이션이라 본문·주석을 수정하지 않는다(Flyway 체크섬은 주석까지 포함 →
--    수정 시 기존 DB 의 validate 가 실패). 그래서 V8/V13 의 [W10] 누락분 해소 사실은 손대지 않고
--    본 V22 헤더와 docs/improvements/index-coverage.md 에만 기록한다.
ALTER TABLE orders ADD INDEX idx_orders_member_created (member_id, created_at), ALGORITHM=INPLACE, LOCK=NONE;
ALTER TABLE orders ADD INDEX idx_orders_status_created (status, created_at),     ALGORITHM=INPLACE, LOCK=NONE;
ALTER TABLE review ADD INDEX idx_review_album_created  (album_id, created_at),   ALGORITHM=INPLACE, LOCK=NONE;
