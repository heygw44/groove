-- V16: 회원 쿠폰 만료 배치(#92) · 관리자 정렬 보조 인덱스.
--
-- (1) member_coupon 만료 배치 검색용:
--   MemberCouponExpirationTask 가 매 주기 실행하는
--     UPDATE member_coupon SET status='EXPIRED' WHERE status='ISSUED' AND expires_at < ? LIMIT ?
--   의 검색 조건 (status, expires_at) 을 단일 인덱스로 커버해 풀스캔을 회피한다. 기존
--   idx_member_coupon_member_status(member_id, status) 는 member_id 가 선행 키라 본 배치에는
--   사용되지 못한다.
--
-- (2) coupon 관리자 목록 정렬용:
--   AdminCouponController 의 정렬 화이트리스트({id, validUntil}) 가 valid_until 정렬을
--   허용하지만 V14 의 coupon 테이블에는 valid_until 인덱스가 없어 filesort 가 발생했다.
--   '인덱스 없는 컬럼 정렬 차단' 이라는 컨트롤러 의도와 정합시키기 위해 인덱스를 추가한다.
ALTER TABLE member_coupon
    ADD INDEX idx_member_coupon_status_expires (status, expires_at);

ALTER TABLE coupon
    ADD INDEX idx_coupon_valid_until (valid_until);
