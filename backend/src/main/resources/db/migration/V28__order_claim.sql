-- 취소·반품 클레임 도입(#540). order_item 에 걸리는 요청 1건이 행 1개다. 새 테이블이라 백필은 없다.
CREATE TABLE order_claim (
    id                            bigint          NOT NULL AUTO_INCREMENT,
    order_item_id                 bigint          NOT NULL,
    type                          varchar(20)     NOT NULL,
    status                        varchar(20)     NOT NULL DEFAULT 'REQUESTED',
    reason                        varchar(200)    NULL,
    reject_reason                 varchar(200)    NULL,
    restock                       tinyint         NULL,
    refund_account_bank_code      varchar(10)     NULL,
    refund_account_number         varchar(64)     NULL,
    refund_account_holder_name    varchar(100)    NULL,
    requested_at                  datetime(6)     NOT NULL,
    resolved_at                   datetime(6)     NULL,
    created_at                    datetime(6)     NOT NULL,
    updated_at                    datetime(6)     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_order_claim_order_item FOREIGN KEY (order_item_id) REFERENCES order_item (id)
) ENGINE=InnoDB;

CREATE INDEX idx_order_claim_status_requested ON order_claim (status, requested_at);

-- 부분취소 건이 어느 클레임 승인으로 시작됐는지 남긴다. 전액취소 등 클레임과 무관한 취소는 NULL 이다.
ALTER TABLE payment_cancel
    ADD COLUMN order_claim_id bigint NULL,
    ADD CONSTRAINT fk_payment_cancel_order_claim FOREIGN KEY (order_claim_id) REFERENCES order_claim (id);
