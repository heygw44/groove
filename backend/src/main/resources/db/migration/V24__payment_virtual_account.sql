-- 무통장(가상계좌) 결제수단 정보를 payment 에 추가한다. va_secret_hash 는 입금 웹훅 secret 의 SHA-256 해시다 -
-- 조회 API 는 secret 을 null 로 주기 때문에 승인 응답에서만 받을 수 있어 평문 대신 해시로 저장한다.
ALTER TABLE payment
    ADD COLUMN easy_pay_provider varchar(30) NULL,
    ADD COLUMN va_bank_code varchar(10) NULL,
    ADD COLUMN va_account_number varchar(64) NULL,
    ADD COLUMN va_customer_name varchar(100) NULL,
    ADD COLUMN va_due_date datetime(6) NULL,
    ADD COLUMN va_secret_hash varchar(64) NULL;
