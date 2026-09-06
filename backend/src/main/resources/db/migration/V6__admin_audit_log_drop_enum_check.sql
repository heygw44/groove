-- V1 이 admin_audit_log.target_type / action 에 만든 CHECK 가 enum 상수 추가(PRODUCT_IMPORT, CATALOG_IMPORT_JOB_START, CATALOG_IMPORT_JOB)를 막는다.
-- 엔티티는 @JdbcTypeCode(VARCHAR) 라 이후 ddl-auto 로 CHECK 가 다시 생기지 않는다. 운영에서 수동 삭제된 경우를 대비해 멱등하게 처리한다.
SET @chk := (SELECT GROUP_CONCAT(CONCAT('DROP CHECK `', CONSTRAINT_NAME, '`') SEPARATOR ', ')
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'admin_audit_log' AND CONSTRAINT_TYPE = 'CHECK');
SET @sql := IF(@chk IS NULL, 'SELECT 1', CONCAT('ALTER TABLE admin_audit_log ', @chk));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
