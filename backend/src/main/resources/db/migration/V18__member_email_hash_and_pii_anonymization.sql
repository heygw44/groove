-- V18: 회원 탈퇴 PII 익명화 (#170, GDPR/개인정보).
--
-- 탈퇴는 그동안 deleted_at 만 찍는 순수 soft delete 라 email/name/phone 평문과 주문/배송 PII 가
-- 영구 잔존했다. 이 마이그레이션은 두 갈래를 받친다:
--   Part A — 탈퇴 즉시 회원 PII 익명화: email 평문을 'withdrawn-{id}@deleted.local' 로 치환하되,
--            재가입 차단(패턴 A)은 신설 email_hash(SHA-256) 로 유지한다. name/phone 은 마스킹/NULL.
--   Part B — 배송완료 + 보존기간 경과 주문/배송 PII 를 배치로 익명화: orders/shipping 에 anonymized_at 마커.
--
-- 단일 파일을 세미콜론으로 분리해 순차 실행한다(각 DDL 은 암묵 커밋). ALGORITHM=INSTANT 는
-- MySQL 8.0.12+ 에서 메타데이터만 갱신하는 즉시 ADD/MODIFY COLUMN 이다(테이블 재작성 없음) —
-- 단 UNIQUE 추가는 INSTANT 대상이 아니므로 별도 ALTER 로 둔다.

-- 1) member.email_hash: ADD(nullable) → 기존 행 백필 → NOT NULL 승격 → UNIQUE 제약.
--    백필값 SHA2(LOWER(TRIM(email)),256) 는 애플리케이션의 Member.hashEmail(정규화 후 Sha256Hasher.hex)
--    과 동치다(둘 다 소문자 hex). 정규화(LOWER/TRIM)는 기존 uk_member_email 의 utf8mb4_unicode_ci
--    (대소문자 무시) 차단 시맨틱을 해시 점유로 그대로 옮기기 위함이다.
--    email 이 이미 UNIQUE(ci) 라 정규화 평문은 유일 → 해시도 유일 → UNIQUE 추가가 실패하지 않는다.
ALTER TABLE member
    ADD COLUMN email_hash CHAR(64) NULL,
    ALGORITHM=INSTANT;

UPDATE member SET email_hash = SHA2(LOWER(TRIM(email)), 256);

ALTER TABLE member
    MODIFY COLUMN email_hash CHAR(64) NOT NULL;

ALTER TABLE member
    ADD CONSTRAINT uk_member_email_hash UNIQUE (email_hash);

-- 2) member.phone NULL 허용: 탈퇴 익명화에서 phone 을 NULL 로 비운다. 활성 회원은 가입 시 도메인
--    검증으로 항상 non-null 이므로 애플리케이션 불변식에는 영향이 없다.
--    nullability 변경은 ALGORITHM=INSTANT 미지원(MySQL ERROR 1845)이라 기본 알고리즘(INPLACE/COPY)으로 둔다.
ALTER TABLE member
    MODIFY COLUMN phone VARCHAR(20) NULL;

-- 2-1) 백카탈로그 익명화: #170 이전에 이미 탈퇴(deleted_at)했으나 평문 PII 가 남은 회원도 익명화한다.
--      email_hash 백필(1단계)이 원본 이메일의 해시를 이미 점유시켰으므로(재가입 차단 유지), 여기서는 평문만
--      제거한다 — Member.anonymize() 와 동일 규칙(email→withdrawn-{id}@deleted.local, name 마스킹, phone NULL).
--      id 가 PK 라 치환 email 은 유일해 uk_member_email 과 충돌하지 않는다. 반드시 1·2단계 뒤에 실행한다.
UPDATE member
SET email = CONCAT('withdrawn-', id, '@deleted.local'),
    name  = '탈퇴회원',
    phone = NULL
WHERE deleted_at IS NOT NULL;

-- 3) orders / shipping 익명화 마커. 배치가 'DELIVERED + 보존기간 경과 + anonymized_at IS NULL' 을
--    대상으로 잡고, 익명화 후 anonymized_at 을 찍어 다음 주기에서 제외(멱등)한다.
--    전용 인덱스(delivered_at/anonymized_at)는 프로젝트 컨벤션(V8/V12)대로 슬로우 쿼리 측정 후 W10 으로
--    연기한다 — 기존 idx_shipping_status(status, created_at) 의 status 프리픽스가 1차로 좁힌다. 단
--    reconciliation 의 '과도 상태 소량' 과 달리 익명화 미처리분은 누적될 수 있으므로 W10 측정 1순위 후보다.
ALTER TABLE orders
    ADD COLUMN anonymized_at DATETIME(6) NULL,
    ALGORITHM=INSTANT;

ALTER TABLE shipping
    ADD COLUMN anonymized_at DATETIME(6) NULL,
    ALGORITHM=INSTANT;
