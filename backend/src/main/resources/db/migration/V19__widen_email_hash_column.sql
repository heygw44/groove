-- V19: email_hash 컬럼 폭 확장 (#186).
--
-- #186 에서 이메일 점유 해시를 결정적 SHA-256(64 hex) → 서버 비밀키 HMAC 으로 전환하면서, 출력에 키 롤링용
-- 버전 prefix 를 붙인다(예: 'v1:' + 64 hex = 67자). 기존 CHAR(64) 로는 이 값을 담지 못하므로 VARCHAR(72) 로
-- 넓힌다(향후 prefix 여유 포함). CHAR→VARCHAR 로 바꿔 Hibernate @Column(length=72)(VARCHAR) 매핑과 타입을
-- 맞춰 ddl-auto: validate 모호성도 없앤다. type-widening MODIFY 라 UNIQUE 인덱스(uk_member_email_hash)는
-- 보존된다(CHAR(64)→VARCHAR(72) 는 비손실 확장).
--
-- HMAC 재계산(활성 회원 백필)은 MySQL 에 HMAC 내장 함수가 없어 SQL 로 표현할 수 없으므로, 이어지는 Java
-- 마이그레이션 V20(EmailHasher DI)이 수행한다. 본 DDL 은 SQL 로 분리해 모든 실행 컨텍스트(전체 컨텍스트는
-- 물론 @DataJpaTest 슬라이스처럼 @Component JavaMigration 빈이 로딩되지 않는 경우 포함)에서 항상 적용되게 한다.
ALTER TABLE member
    MODIFY COLUMN email_hash VARCHAR(72) NOT NULL;
