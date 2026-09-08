-- 관리자 통계의 기간 조회 두 개가 인덱스 없이 테이블을 통째로 훑었다.
-- 둘 다 고카디널리티 datetime 범위 조회라, 값이 몇 개뿐인 컬럼을 선두에 두는 인덱스와 성격이 다르다.

-- 일별 매출은 승인 행과 취소 행을 UNION ALL 로 합치는데, 취소 브랜치만 canceled_at 범위라
-- 인덱스가 없어 payment 15만 행을 매번 훑었다. 기간을 좁혀도 스캔 대상이 줄지 않는다.
-- 범위 조건이 NULL 행을 인덱스에서 자동으로 제외하므로 취소 행만 정확히 짚는다.
create index idx_payment_canceled_at on payment (canceled_at);

-- 대시보드 요약의 '오늘 가입 회원 수'가 member 를 풀스캔했다. 회원은 지우지 않고 쌓이기만 하는데
-- 조회 범위는 늘 하루라, 테이블이 커질수록 읽는 양과 결과의 격차가 벌어진다.
create index idx_member_created on member (created_at);
