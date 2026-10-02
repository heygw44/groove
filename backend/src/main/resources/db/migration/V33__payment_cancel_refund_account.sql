-- 가상계좌 결제의 전액취소 재시도가 환불계좌를 잃지 않도록 요청 시점의 계좌를 payment_cancel 에 보관한다.
-- 취소가 확정되거나 실패하면 비우므로 기존 행은 모두 NULL 로 남는다.
ALTER TABLE payment_cancel
    ADD COLUMN refund_account_bank_code   varchar(10)  NULL,
    ADD COLUMN refund_account_number      varchar(64)  NULL,
    ADD COLUMN refund_account_holder_name varchar(100) NULL;
