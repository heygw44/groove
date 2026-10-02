-- 한정반은 1인 1매로 고정한다(Redis buyers 셋 + uk_limited_purchase). 예전에 관리자가 1~5 로 저장한 드롭이 남아 있으면
-- 엔티티 검증이 수정 요청마다 COMMON_INVALID_INPUT 으로 거절하므로 저장값을 1 로 맞춘다.
update limited_drop set per_member_limit = 1 where per_member_limit <> 1;
