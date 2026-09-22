-- 토스 웹훅(PAYMENT_STATUS_CHANGED) 수신 이력. 서명 헤더가 없어 저장한 toss_status 는 판단이 아니라
-- 멱등 키(중복 전송 흡수)와 감사 용도다. 상태 판단은 항상 재조회로 한다(#456).
CREATE TABLE payment_webhook_event (
    id bigint NOT NULL AUTO_INCREMENT,
    event_type varchar(50) NULL,
    payment_key varchar(200) NULL,
    toss_order_id varchar(64) NULL,
    toss_status varchar(30) NULL,
    event_created_at datetime(6) NULL,
    received_at datetime(6) NOT NULL,
    processed_at datetime(6) NULL,
    result varchar(30) NULL,
    detail varchar(500) NULL,
    created_at datetime(6) NOT NULL,
    updated_at datetime(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_payment_webhook_event_dedup UNIQUE (payment_key, toss_status, event_created_at)
) ENGINE=InnoDB;
