-- 주문/리뷰 목록 인덱스 측정 시드 (#225)
--
-- V22 복합 인덱스(idx_orders_member_created / idx_orders_status_created / idx_review_album_created)의
-- EXPLAIN Before/After 를 의미 있게 캡처하려면 orders/review 에 충분한 행이 필요한데, 데모/k6 경로는
-- orders/review 를 런타임에만 만든다(scripts/seed.sh 는 album 5만·member 만 적재). 그래서 측정 전용으로
-- orders 5만 + review 5만(주문 1건당 1리뷰)을 합성한다 — loadtest/seed-coupon-loadtest.sql 과 같은 결.
--
-- 전제: scripts/seed.sh 적재 후 실행(member id 1..80 USER + 81 ADMIN, album id 1..50000 존재).
--   FK: orders.member_id→member, review.album_id→album, review.order_id→orders.
-- 적용:  docker compose exec -T -e MYSQL_PWD=$DB_PASSWORD mysql mysql -ugroove groove < loadtest/seed-order-review-loadtest.sql
-- 재실행 가능: 기존 IDXTEST-* 주문을 먼저 지운다(review 는 order FK CASCADE 로 동반 삭제).

SET SESSION cte_max_recursion_depth = 200000;

-- 재실행 정리(IDXTEST 태깅분만). orders 삭제 시 review.order_id ON DELETE CASCADE 로 리뷰 동반 삭제.
DELETE FROM orders WHERE order_number LIKE 'IDXTEST-%';

-- ── orders 5만 ────────────────────────────────────────────────────────────
--   member_id: 1..80 라운드로빈(회원당 ≈625건 → member 필터 ref 의미)
--   status   : DELIVERED 60% / PAID 20% / PENDING 10% / CANCELLED 10% (status 필터 선택도 확보)
--   created_at: NOW - n분(약 34일 분산 → 정렬 대상)
INSERT INTO orders (order_number, member_id, status, total_amount, discount_amount, paid_at,
                    created_at, updated_at, recipient_name, recipient_phone, address, zip_code)
WITH RECURSIVE seq(n) AS (
    SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 50000
)
SELECT
    CONCAT('IDXTEST-', LPAD(n, 7, '0')),
    1 + (n % 80),
    CASE WHEN n % 10 < 6 THEN 'DELIVERED'
         WHEN n % 10 < 8 THEN 'PAID'
         WHEN n % 10 = 8 THEN 'PENDING'
         ELSE 'CANCELLED' END,
    10000 + (n % 50) * 1000,
    0,
    CASE WHEN n % 10 < 8 THEN NOW(6) - INTERVAL n MINUTE ELSE NULL END,
    NOW(6) - INTERVAL n MINUTE,
    NOW(6) - INTERVAL n MINUTE,
    '측정', '01000000000', '서울시 강남구', '06000'
FROM seq;

-- ── review 5만 (주문 1건당 1리뷰) ──────────────────────────────────────────
--   album_id : 1..500 라운드로빈(앨범당 ≈100건 → album 필터 ref + 정렬 의미)
--   member_id: 해당 주문의 회원, rating 1..5, created_at: 주문 created_at 상속
--   uk_review_order_album(order_id, album_id): order_id 가 행마다 유일 → 충돌 없음
INSERT INTO review (member_id, album_id, order_id, rating, content, created_at, updated_at)
SELECT
    o.member_id,
    1 + (o.rn % 500),
    o.id,
    1 + (o.rn % 5),
    NULL,
    o.created_at,
    o.created_at
FROM (
    SELECT id, member_id, created_at,
           ROW_NUMBER() OVER (ORDER BY id) - 1 AS rn
    FROM orders
    WHERE order_number LIKE 'IDXTEST-%'
) o;

-- 적재 요약
SELECT CONCAT('orders(IDXTEST) = ', COUNT(*)) AS seeded FROM orders WHERE order_number LIKE 'IDXTEST-%'
UNION ALL SELECT CONCAT('  status=DELIVERED = ', COUNT(*)) FROM orders WHERE order_number LIKE 'IDXTEST-%' AND status = 'DELIVERED'
UNION ALL SELECT CONCAT('  member_id=1      = ', COUNT(*)) FROM orders WHERE order_number LIKE 'IDXTEST-%' AND member_id = 1
UNION ALL SELECT CONCAT('review           = ', COUNT(*)) FROM review
UNION ALL SELECT CONCAT('  album_id=1       = ', COUNT(*)) FROM review WHERE album_id = 1;
