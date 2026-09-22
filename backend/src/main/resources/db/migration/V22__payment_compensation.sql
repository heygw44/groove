-- 같은 주문에 다른 paymentKey 로 동시에 승인이 들어와 나중에 커밋된 쪽을 보상 취소할 때 쓰는 대기 큐다.
-- 이 키는 uk_payment_order 때문에 payment 행을 가질 수 없어, 토스 취소 자체가 실패하면 흔적이 없었다
-- (#455). 취소를 부르기 전에 이 행부터 커밋해, 실패해도 대사 스케줄러가 이어받게 한다.
CREATE TABLE payment_compensation (
    id bigint NOT NULL AUTO_INCREMENT,
    payment_key varchar(200) NOT NULL,
    order_id bigint NOT NULL,
    toss_order_id varchar(64) NULL,
    approved_at datetime(6) NULL,
    reason varchar(200) NULL,
    status varchar(20) NOT NULL,
    attempts int NOT NULL DEFAULT 0,
    last_error varchar(500) NULL,
    canceled_at datetime(6) NULL,
    created_at datetime(6) NOT NULL,
    updated_at datetime(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_payment_compensation_payment_key UNIQUE (payment_key),
    CONSTRAINT fk_payment_compensation_order FOREIGN KEY (order_id) REFERENCES orders (id)
) ENGINE=InnoDB;

CREATE INDEX idx_payment_compensation_status_updated ON payment_compensation (status, updated_at);
