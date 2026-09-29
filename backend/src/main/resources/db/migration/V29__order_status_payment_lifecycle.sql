-- orders.status 를 결제 생애주기(PENDING/PAID/CANCELED)로 좁힌다(#543). 이행 단계는 V26 부터 order_item.status 가 맡는다.
-- V1 의 이름 없는 CHECK 를 멱등하게 걷는다(엔티티는 @JdbcTypeCode(VARCHAR) 라 CHECK 가 다시 생기지 않는다, V20 과 같은 방식).
-- 배포 전 payment.status = 'CANCEL_REQUESTED' 가 0건인지 확인한다(09-deployment 런북).
SET @chk := (SELECT GROUP_CONCAT(CONCAT('DROP CHECK `', CONSTRAINT_NAME, '`') SEPARATOR ', ')
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND CONSTRAINT_TYPE = 'CHECK');
SET @sql := IF(@chk IS NULL, 'SELECT 1', CONCAT('ALTER TABLE orders ', @chk));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE orders SET status = 'PAID' WHERE status IN ('PREPARING', 'SHIPPED', 'DELIVERED');
UPDATE orders SET status = 'CANCELED', canceled_at = COALESCE(canceled_at, updated_at) WHERE status = 'REFUNDED';
