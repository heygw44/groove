-- PaymentStatus 에 UNKNOWN/CANCEL_REQUESTED 를 추가해 V1 의 CHECK 를 걷는다(엔티티는 @JdbcTypeCode(VARCHAR) 라 CHECK 가 다시 생기지 않는다).
-- version 은 fail/approve 동시 갱신 시 lost update 를 낙관적 락으로 막기 위함이다.
SET @chk := (SELECT GROUP_CONCAT(CONCAT('DROP CHECK `', CONSTRAINT_NAME, '`') SEPARATOR ', ')
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'payment' AND CONSTRAINT_TYPE = 'CHECK');
SET @sql := IF(@chk IS NULL, 'SELECT 1', CONCAT('ALTER TABLE payment ', @chk));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE payment ADD COLUMN version bigint NOT NULL DEFAULT 0;
