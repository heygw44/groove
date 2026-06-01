-- 선착순 쿠폰 발급 k6 부하 시드 (#93)
--
-- 한정 100장 ACTIVE 쿠폰 1건 + 로그인 가능한 회원 N명을 심는다. 회원당 1장 UNIQUE 때문에
-- 발급 카운터 경합을 노출하려면 서로 다른 회원이 충분히 많아야 한다 — 풀을 한정수량보다 크게(기본 600) 잡는다.
--
-- 비밀번호는 모두 평문 'Loadtest123!' 의 bcrypt($2b$10) 해시다 (BCryptPasswordEncoder 가 $2b 를 검증).
-- 직접 INSERT 라 signup/login rate limit·검증을 우회한다. 앱 기동(Flyway 마이그레이션) 이후 적용할 것.
--
-- 적용:  docker exec -i <mysql> mysql -uroot -p<pw> groove < loadtest/seed-coupon-loadtest.sql
-- 재실행 가능: 기존 부하 데이터를 먼저 지운다.

SET @MEMBERS := 600;
SET @PW := '$2b$10$WifOZdHyww9sA/OJrM5VPuUHjwETF1ek9NluQlKbXJoW5RKwtxa8e'; -- bcrypt('Loadtest123!')

-- 재실행을 위한 정리. FK 순서: refresh_token(RESTRICT) → member_coupon(coupon별) → coupon → member.
-- k6 setup() 로그인이 회원마다 refresh_token 을 남기므로(RESTRICT) 회원 삭제 전에 먼저 지운다.
-- member_coupon 의 회원 FK 는 CASCADE 라 회원 삭제로 함께 지워진다.
DELETE rt FROM refresh_token rt JOIN member m ON rt.member_id = m.id WHERE m.email LIKE 'loadtest-%@groove.test';
DELETE mc FROM member_coupon mc JOIN coupon c ON mc.coupon_id = c.id WHERE c.name LIKE 'LOADTEST%';
DELETE FROM coupon WHERE name LIKE 'LOADTEST%';
DELETE FROM member WHERE email LIKE 'loadtest-%@groove.test';

-- 한정 100장 ACTIVE 쿠폰 (과거~미래 유효기간)
INSERT INTO coupon (name, discount_type, discount_value, min_order_amount, total_quantity, issued_count,
                    per_member_limit, valid_from, valid_until, status, created_at, updated_at)
VALUES ('LOADTEST 선착순 한정', 'FIXED_AMOUNT', 1000, 0, 100, 0, 1,
        NOW(6) - INTERVAL 1 DAY, NOW(6) + INTERVAL 10 DAY, 'ACTIVE', NOW(6), NOW(6));

-- 회원 N명 — email=loadtest-00001@groove.test ... , 같은 bcrypt 해시 재사용
DROP PROCEDURE IF EXISTS seed_loadtest_members;
DELIMITER $$
CREATE PROCEDURE seed_loadtest_members(IN n INT, IN pw VARCHAR(255))
BEGIN
    DECLARE i INT DEFAULT 1;
    WHILE i <= n DO
        INSERT INTO member (email, password, name, phone, role, email_verified, created_at, updated_at)
        VALUES (CONCAT('loadtest-', LPAD(i, 5, '0'), '@groove.test'), pw,
                CONCAT('Load', LPAD(i, 5, '0')), CONCAT('010', LPAD(i, 8, '0')),
                'USER', TRUE, NOW(6), NOW(6));
        SET i = i + 1;
    END WHILE;
END$$
DELIMITER ;
CALL seed_loadtest_members(@MEMBERS, @PW);
DROP PROCEDURE seed_loadtest_members;

SELECT id AS coupon_id, name, total_quantity, status FROM coupon WHERE name LIKE 'LOADTEST%';
SELECT COUNT(*) AS seeded_members FROM member WHERE email LIKE 'loadtest-%@groove.test';
